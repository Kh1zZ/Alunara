package com.alunara.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.contract.PlaybackLifecycleController
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.domain.lyrics.LyricsSyncResolver
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.FailureCategory
import com.alunara.core.domain.policy.RetryPolicy
import com.alunara.core.domain.queue.QueueAction
import com.alunara.core.domain.queue.QueueReducer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PlaybackSessionUiState(
    val queueState: QueueState = QueueState(),
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyrics: LyricsDocument = LyricsDocument.Unavailable(""),
    val lyricsLoading: Boolean = false,
    val selectedLyricsProvider: LyricsProviderId? = null,
    val activeLyricIndex: Int = -1,
    val errorMessage: String? = null,
) {
    val currentTrack: Track?
        get() = queueState.currentItem?.track
}

/**
 * Coordinates queue state, Room persistence, audio stream resolution, bounded retry/recovery,
 * lyrics fetching/sync, and stop-on-exit lifecycle enforcement (PRD §§6–7).
 */
class PlaybackSessionManager(
    private val musicEngine: MusicEngine,
    private val lyricsEngine: LyricsEngine,
    private val queueStore: QueueStore,
    private val playerController: SinglePlayerLifecycleController,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PlaybackLifecycleController {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mutableUiState = MutableStateFlow(PlaybackSessionUiState())
    val uiState: StateFlow<PlaybackSessionUiState> = mutableUiState.asStateFlow()

    private var resolveJob: Job? = null
    private var retryJob: Job? = null
    private var lyricsJob: Job? = null
    private var tickerJob: Job? = null
    private var persistJob: Job? = null

    private var currentTrackAttempt = 0
    private var consecutiveTrackFailures = 0
    private var cachedSource: AudioStreamSource? = null
    private var queueRestored = false

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val track = mutableUiState.value.currentTrack
                val duration = resolveDurationMs(track)
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        mutableUiState.update {
                            it.copy(
                                status = PlaybackStatus.BUFFERING,
                                durationMs = duration,
                            )
                        }
                    }

                    Player.STATE_READY -> {
                        val playing = playerController.isPlaying()
                        mutableUiState.update {
                            it.copy(
                                status = if (playing) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                                durationMs = duration,
                                errorMessage = null,
                            )
                        }
                        if (playing) {
                            currentTrackAttempt = 0
                            consecutiveTrackFailures = 0
                            startTickerIfNeeded()
                        } else {
                            stopTicker()
                        }
                    }

                    Player.STATE_ENDED -> {
                        stopTicker()
                        handleTrackEnded()
                    }

                    Player.STATE_IDLE -> {
                        stopTicker()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    currentTrackAttempt = 0
                    consecutiveTrackFailures = 0
                    mutableUiState.update {
                        it.copy(
                            status = PlaybackStatus.PLAYING,
                            durationMs = resolveDurationMs(it.currentTrack),
                            errorMessage = null,
                        )
                    }
                    startTickerIfNeeded()
                } else {
                    stopTicker()
                    val pos = playerController.currentPositionMs()
                    mutableUiState.update { state ->
                        val nextStatus =
                            when (state.status) {
                                PlaybackStatus.PLAYING -> PlaybackStatus.PAUSED
                                else -> state.status
                            }
                        val updatedQueue = QueueReducer.reduce(state.queueState, QueueAction.UpdatePosition(pos))
                        state.copy(
                            queueState = updatedQueue,
                            status = nextStatus,
                            positionMs = pos,
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopTicker()
                val category = PlaybackErrorClassifier.classify(error)
                runCatching {
                    android.util.Log.w(
                        "AlunaraPlayback",
                        "onPlayerError track=${mutableUiState.value.currentTrack?.id} category=$category msg=${error.message}",
                        error,
                    )
                }
                if (category == FailureCategory.EXPIRED_SOURCE) {
                    cachedSource = null
                }
                handlePlaybackFailure(category, error.message ?: "Playback error")
            }
        }

    fun restorePersistedQueue() {
        if (queueRestored) return
        queueRestored = true
        scope.launch {
            val loaded =
                withContext(ioDispatcher) {
                    runCatching { queueStore.loadQueue() }.getOrDefault(QueueState())
                }
            val normalized = QueueReducer.reduce(QueueState(), QueueAction.Restore(loaded))
            val item = normalized.currentItem
            mutableUiState.update {
                it.copy(
                    queueState = normalized,
                    status = if (item != null) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
                    positionMs = normalized.positionMs,
                    durationMs = (item?.track?.durationSeconds ?: 0) * 1000L,
                    errorMessage = null,
                )
            }
        }
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
    ) {
        if (tracks.isEmpty()) return
        val items =
            tracks.map { track ->
                QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
            }
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.SetQueue(items, startIndex))
        consecutiveTrackFailures = 0
        currentTrackAttempt = 0
        cachedSource = null
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
    }

    fun playTrackAt(index: Int) {
        val current = mutableUiState.value.queueState
        if (index !in current.items.indices) return
        val nextQueue = QueueReducer.reduce(current, QueueAction.SetQueue(current.items, index))
        consecutiveTrackFailures = 0
        currentTrackAttempt = 0
        cachedSource = null
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
    }

    fun enqueue(track: Track) {
        val wasEmpty = mutableUiState.value.queueState.items.isEmpty()
        val item = QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.Enqueue(item))
        mutableUiState.update {
            it.copy(
                queueState = nextQueue,
                status = if (wasEmpty) PlaybackStatus.PAUSED else it.status,
                durationMs = if (wasEmpty) (track.durationSeconds ?: 0) * 1000L else it.durationMs,
            )
        }
        persistQueueAsync(nextQueue)
    }

    fun removeFromQueue(index: Int) {
        val oldQueue = mutableUiState.value.queueState
        val removingCurrent = index == oldQueue.currentIndex
        val nextQueue = QueueReducer.reduce(oldQueue, QueueAction.RemoveAt(index))
        if (nextQueue == oldQueue) return

        if (nextQueue.items.isEmpty()) {
            clearQueue()
            return
        }

        if (removingCurrent) {
            cachedSource = null
            currentTrackAttempt = 0
            if (mutableUiState.value.status == PlaybackStatus.PLAYING || playerController.hasActivePlayer) {
                applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
            } else {
                mutableUiState.update {
                    it.copy(
                        queueState = nextQueue,
                        positionMs = 0L,
                        durationMs = (nextQueue.currentItem?.track?.durationSeconds ?: 0) * 1000L,
                    )
                }
                persistQueueAsync(nextQueue)
            }
        } else {
            mutableUiState.update { it.copy(queueState = nextQueue) }
            persistQueueAsync(nextQueue)
        }
    }

    fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.Move(fromIndex, toIndex))
        mutableUiState.update { it.copy(queueState = nextQueue) }
        persistQueueAsync(nextQueue)
    }

    fun clearQueue() {
        cancelActiveWork()
        playerController.onAppBackgroundedOrStopped()
        cachedSource = null
        currentTrackAttempt = 0
        consecutiveTrackFailures = 0
        val empty = mutableUiState.value.queueState.copy(items = emptyList(), currentIndex = -1, positionMs = 0L)
        mutableUiState.value =
            PlaybackSessionUiState(
                queueState = empty,
                status = PlaybackStatus.IDLE,
            )
        persistQueueAsync(empty)
    }

    fun togglePlayPause() {
        val state = mutableUiState.value
        val currentTrack = state.currentTrack ?: return
        if (playerController.hasActivePlayer) {
            if (playerController.isPlaying()) {
                playerController.pause()
            } else if (state.status == PlaybackStatus.ERROR || state.status == PlaybackStatus.ENDED) {
                currentTrackAttempt = 0
                resolveAndPlay(currentTrack, startPositionMs = state.positionMs)
            } else {
                playerController.play()
            }
        } else {
            currentTrackAttempt = 0
            resolveAndPlay(currentTrack, startPositionMs = state.positionMs)
            fetchLyricsForTrack(currentTrack, state.selectedLyricsProvider)
        }
    }

    fun ensureLyricsLoaded() {
        val state = mutableUiState.value
        val track = state.currentTrack ?: return
        if (!state.lyricsLoading && state.lyrics is LyricsDocument.Unavailable && state.lyrics.reason.isEmpty()) {
            fetchLyricsForTrack(track, state.selectedLyricsProvider)
        }
    }

    fun cycleLyricsProvider() {
        val state = mutableUiState.value
        val track = state.currentTrack ?: return
        val order = listOf(LyricsProviderId.SIMPMUSIC, LyricsProviderId.LRCLIB, LyricsProviderId.KUGOU)
        val currentProvider =
            state.selectedLyricsProvider
                ?: when (val doc = state.lyrics) {
                    is LyricsDocument.Synced -> doc.provider
                    is LyricsDocument.Plain -> doc.provider
                    is LyricsDocument.Unavailable -> null
                }
        val currentIdx = order.indexOf(currentProvider)
        val nextProvider = order[(currentIdx + 1) % order.size]
        fetchLyricsForTrack(track, preferredProvider = nextProvider)
    }

    fun skipNext() {
        val current = mutableUiState.value.queueState
        if (current.items.isEmpty() || current.currentIndex >= current.items.lastIndex) return
        val nextQueue = QueueReducer.reduce(current, QueueAction.SkipNext)
        currentTrackAttempt = 0
        cachedSource = null
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
    }

    fun skipPrevious() {
        val current = mutableUiState.value.queueState
        if (current.items.isEmpty()) return
        val currentPos =
            if (playerController.hasActivePlayer) playerController.currentPositionMs() else mutableUiState.value.positionMs
        if (currentPos > 5_000L || current.currentIndex <= 0) {
            seekTo(0L)
            return
        }
        val prevQueue = QueueReducer.reduce(current, QueueAction.SkipPrevious)
        currentTrackAttempt = 0
        cachedSource = null
        applyQueueAndStartTrack(prevQueue, startPositionMs = 0L)
    }

    fun seekBy(deltaMs: Long) {
        val state = mutableUiState.value
        val duration = resolveDurationMs(state.currentTrack)
        val currentPos =
            if (playerController.hasActivePlayer) playerController.currentPositionMs() else state.positionMs
        val target =
            if (duration > 0L) {
                (currentPos + deltaMs).coerceIn(0L, duration)
            } else {
                (currentPos + deltaMs).coerceAtLeast(0L)
            }
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        if (playerController.hasActivePlayer) {
            playerController.seekTo(safePos)
        }
        mutableUiState.update { state ->
            val updatedQueue = QueueReducer.reduce(state.queueState, QueueAction.UpdatePosition(safePos))
            state.copy(
                queueState = updatedQueue,
                positionMs = safePos,
                activeLyricIndex = computeActiveLyricIndex(state.lyrics, safePos),
            )
        }
    }

    fun setAutoplay(enabled: Boolean) {
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.SetAutoplay(enabled))
        mutableUiState.update { it.copy(queueState = nextQueue) }
        persistQueueAsync(nextQueue)
    }

    override fun onAppBackgroundedOrStopped() {
        cancelActiveWork()
        val capturedPosition =
            if (playerController.hasActivePlayer) {
                playerController.currentPositionMs()
            } else {
                mutableUiState.value.positionMs
            }
        playerController.onAppBackgroundedOrStopped()

        val updatedQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.UpdatePosition(capturedPosition))
        mutableUiState.update { state ->
            state.copy(
                queueState = updatedQueue,
                positionMs = capturedPosition,
                status = if (updatedQueue.currentItem != null) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
            )
        }
        scope.launch(NonCancellable + ioDispatcher) {
            runCatching { queueStore.saveQueue(updatedQueue) }
        }
    }

    private fun applyQueueAndStartTrack(
        nextQueue: QueueState,
        startPositionMs: Long,
    ) {
        cancelActiveWork()
        val track = nextQueue.currentItem?.track
        if (track == null) {
            clearQueue()
            return
        }
        mutableUiState.update {
            it.copy(
                queueState = nextQueue,
                status = PlaybackStatus.RESOLVING,
                positionMs = startPositionMs,
                durationMs = (track.durationSeconds ?: 0) * 1000L,
                lyrics = LyricsDocument.Unavailable(""),
                lyricsLoading = true,
                selectedLyricsProvider = null,
                activeLyricIndex = -1,
                errorMessage = null,
            )
        }
        persistQueueAsync(nextQueue)
        resolveAndPlay(track, startPositionMs)
        fetchLyricsForTrack(track, preferredProvider = null)
    }

    private fun resolveAndPlay(
        track: Track,
        startPositionMs: Long,
    ) {
        resolveJob?.cancel()
        resolveJob =
            scope.launch {
                mutableUiState.update { it.copy(status = PlaybackStatus.RESOLVING, errorMessage = null) }
                val cached =
                    cachedSource?.takeIf {
                        it.trackId == track.id && it.expiresAtEpochMs > System.currentTimeMillis() + 30_000L
                    }
                val streamResult =
                    if (cached != null) {
                        Result.success(cached)
                    } else {
                        withContext(ioDispatcher) { musicEngine.resolveAudioStream(track.id) }
                    }

                if (!isActive || mutableUiState.value.currentTrack?.id != track.id) return@launch

                streamResult.fold(
                    onSuccess = { source ->
                        cachedSource = source
                        playerController.prepareAudioStream(
                            source = source,
                            startPositionMs = startPositionMs,
                            playWhenReady = true,
                            listener = playerListener,
                        )
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        runCatching {
                            android.util.Log.w(
                                "AlunaraPlayback",
                                "resolveAudioStream failed track=${track.id}: ${error.message}",
                                error,
                            )
                        }
                        handlePlaybackFailure(FailureCategory.TRANSIENT_TIMEOUT, error.message ?: "Stream unavailable")
                    },
                )
            }
    }

    private fun handlePlaybackFailure(
        category: FailureCategory,
        message: String,
    ) {
        val state = mutableUiState.value
        val track = state.currentTrack ?: return
        val decision =
            retryPolicy.evaluate(
                category = category,
                attemptIndex = currentTrackAttempt,
                consecutiveTrackFailures = consecutiveTrackFailures,
                queueSize = state.queueState.items.size,
            )

        if (decision.shouldRetry) {
            currentTrackAttempt++
            mutableUiState.update {
                it.copy(
                    status = PlaybackStatus.RESOLVING,
                    errorMessage = "${decision.reason}…",
                )
            }
            retryJob?.cancel()
            retryJob =
                scope.launch {
                    delay(decision.delayMs)
                    if (!isActive || mutableUiState.value.currentTrack?.id != track.id) return@launch
                    resolveAndPlay(track, startPositionMs = state.positionMs)
                }
        } else if (decision.shouldSkipToNext && state.queueState.currentIndex < state.queueState.items.lastIndex) {
            consecutiveTrackFailures++
            currentTrackAttempt = 0
            cachedSource = null
            val nextQueue = QueueReducer.reduce(state.queueState, QueueAction.SkipNext)
            applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
        } else {
            mutableUiState.update {
                it.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = message,
                )
            }
        }
    }

    private fun handleTrackEnded() {
        val state = mutableUiState.value
        val queue = state.queueState
        if (queue.autoplayEnabled && queue.currentIndex < queue.items.lastIndex) {
            val nextQueue = QueueReducer.reduce(queue, QueueAction.SkipNext)
            currentTrackAttempt = 0
            cachedSource = null
            applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
        } else {
            mutableUiState.update {
                it.copy(
                    status = PlaybackStatus.ENDED,
                    positionMs = it.durationMs,
                )
            }
        }
    }

    private fun fetchLyricsForTrack(
        track: Track,
        preferredProvider: LyricsProviderId? = null,
    ) {
        lyricsJob?.cancel()
        lyricsJob =
            scope.launch {
                mutableUiState.update {
                    it.copy(
                        lyricsLoading = true,
                        selectedLyricsProvider = preferredProvider,
                        lyrics = LyricsDocument.Unavailable(""),
                        activeLyricIndex = -1,
                    )
                }
                val doc =
                    withContext(ioDispatcher) {
                        try {
                            lyricsEngine.fetchLyrics(track, preferredProvider)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (t: Throwable) {
                            LyricsDocument.Unavailable(t.message ?: "Lyrics unavailable")
                        }
                    }
                if (!isActive || mutableUiState.value.currentTrack?.id != track.id) return@launch
                mutableUiState.update { state ->
                    state.copy(
                        lyrics = doc,
                        lyricsLoading = false,
                        activeLyricIndex = computeActiveLyricIndex(doc, state.positionMs),
                    )
                }
            }
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob =
            scope.launch {
                while (isActive && playerController.isPlaying()) {
                    val pos = playerController.currentPositionMs()
                    val dur = resolveDurationMs(mutableUiState.value.currentTrack)
                    mutableUiState.update { state ->
                        state.copy(
                            positionMs = pos,
                            durationMs = dur,
                            activeLyricIndex = computeActiveLyricIndex(state.lyrics, pos),
                        )
                    }
                    delay(100L)
                }
            }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun cancelActiveWork() {
        resolveJob?.cancel()
        resolveJob = null
        retryJob?.cancel()
        retryJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        stopTicker()
    }

    private fun persistQueueAsync(queueState: QueueState) {
        persistJob?.cancel()
        persistJob =
            scope.launch(ioDispatcher) {
                runCatching { queueStore.saveQueue(queueState) }
            }
    }

    private fun resolveDurationMs(track: Track?): Long {
        val playerDur = playerController.durationMs()
        if (playerDur > 0L && playerDur != C.TIME_UNSET) return playerDur
        return (track?.durationSeconds ?: 0).toLong() * 1000L
    }

    private fun computeActiveLyricIndex(
        lyrics: LyricsDocument,
        positionMs: Long,
    ): Int =
        when (lyrics) {
            is LyricsDocument.Synced -> LyricsSyncResolver.findActiveLineIndex(lyrics.lines, positionMs)
            else -> -1
        }
}
