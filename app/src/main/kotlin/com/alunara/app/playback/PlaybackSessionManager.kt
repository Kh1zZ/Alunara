package com.alunara.app.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.alunara.core.domain.contract.AccountSessionStore
import com.alunara.core.domain.contract.CatalogStore
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.contract.PlaybackLifecycleController
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.domain.lyrics.LyricsRomanizer
import com.alunara.core.domain.lyrics.LyricsSyncResolver
import com.alunara.core.domain.model.AccountCredentials
import com.alunara.core.domain.model.AccountProfile
import com.alunara.core.domain.model.AccountScope
import com.alunara.core.domain.model.AudioStreamSource
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioQualityPreset
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SyncNoticeKind {
    FAVORITE_SYNC_FAILED,
    PLAYLIST_CREATED,
    PLAYLIST_ADDED,
    PLAYLIST_REMOVED,
    PLAYLIST_SAVED,
    PLAYLIST_UNSAVED,
    ARTIST_SUBSCRIBED,
    ARTIST_UNSUBSCRIBED,
    SYNC_FAILED,
}

data class SyncNotice(
    val id: Long,
    val kind: SyncNoticeKind,
    val detail: String? = null,
)

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
    val authSession: AuthSessionState = AuthSessionState.Guest,
    val authLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val favorites: List<Track> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val recentHistory: List<Track> = emptyList(),
    val accountPlaylists: List<PlaylistSummary> = emptyList(),
    val savedPlaylistIds: Set<String> = emptySet(),
    val subscribedArtistIds: Set<String> = emptySet(),
    val remoteFavorites: List<Track> = emptyList(),
    val remoteHistory: List<Track> = emptyList(),
    val librarySyncing: Boolean = false,
    val syncNotice: SyncNotice? = null,
    val queueChips: List<QueueFilterChip> = emptyList(),
    val activeQueueChipLabel: String? = null,
    val radioLoading: Boolean = false,
    val audioQuality: AudioQualityPreset = AudioQualityPreset.AUTO,
    val activeStreamCodecLabel: String? = null,
    val lyricsSyncOffsetMs: Long = 0L,
    val showRomanizedLyrics: Boolean = false,
    val hasRomanizedLyrics: Boolean = false,
    val hasPartialKanjiNotice: Boolean = false,
) {
    val currentTrack: Track?
        get() = queueState.currentItem?.track

    val isCurrentTrackFavorite: Boolean
        get() = currentTrack?.id in favoriteIds
}

private data class AccountLibrarySnapshot(
    val playlists: List<PlaylistSummary> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
)

/**
 * Coordinates queue state, Room persistence, audio stream resolution, bounded retry/recovery,
 * radio & autoplay tail extension, account-scoped favorites/history, verified session lifecycle,
 * lyrics fetching/sync, and stop-on-exit enforcement (PRD §§6–7, 10, FR-11–FR-16, FR-22).
 */
class PlaybackSessionManager(
    private val musicEngine: MusicEngine,
    private val lyricsEngine: LyricsEngine,
    private val queueStore: QueueStore,
    private val playerController: AudioPlayerControl,
    private val catalogStore: CatalogStore = NoOpCatalogStore,
    private val accountSessionStore: AccountSessionStore = NoOpAccountSessionStore,
    private val playbackPreferences: PlaybackPreferences = InMemoryPlaybackPreferences(),
    private val onBackgroundServiceStateChanged: (shouldRunService: Boolean) -> Unit = {},
    private val isLowMemoryPressure: () -> Boolean = { false },
    private val prefetchDelayMs: Long = DEFAULT_PREFETCH_DELAY_MS,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PlaybackLifecycleController {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val mutableUiState =
        MutableStateFlow(
            PlaybackSessionUiState(audioQuality = playbackPreferences.getAudioQuality()),
        )
    val uiState: StateFlow<PlaybackSessionUiState> = mutableUiState.asStateFlow()
    private val mutablePositionMs = MutableStateFlow(0L)
    val positionMsFlow: StateFlow<Long> = mutablePositionMs.asStateFlow()
    private val mutableActiveLyricIndex = MutableStateFlow(-1)
    val activeLyricIndexFlow: StateFlow<Int> = mutableActiveLyricIndex.asStateFlow()

    @Volatile
    private var nowPlayingVisible: Boolean = false
    private var lyricsPanelVisible: Boolean = false

    private var resolveJob: Job? = null
    private var retryJob: Job? = null
    private var lyricsJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchTarget: Pair<String, AudioQualityPreset>? = null
    private var tickerJob: Job? = null
    private var persistJob: Job? = null
    private var radioJob: Job? = null

    private var currentTrackAttempt = 0
    private var consecutiveTrackFailures = 0
    private var cachedSource: AudioStreamSource? = null
    private var expectedPlayerTrackId: String? = null
    private var queueRestored = false
    private var lastRecordedQueueItemId: String? = null
    private var lastAutoplayExtendedTrackId: String? = null

    private fun isPlayerSyncedToCurrentTrack(): Boolean {
        val currentId = mutableUiState.value.currentTrack?.id ?: return false
        if (expectedPlayerTrackId != currentId) return false
        val activeId = playerController.activeTrackId
        return activeId == null || activeId == currentId
    }

    internal val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE) {
                    stopTicker()
                    return
                }
                if (!isPlayerSyncedToCurrentTrack()) return
                val track = mutableUiState.value.currentTrack
                val duration = resolveDurationMs(track)
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (prefetchJob?.isActive == true) cancelStreamPrefetch()
                        mutableUiState.update {
                            it.copy(
                                status = PlaybackStatus.BUFFERING,
                                durationMs = duration,
                            )
                        }
                    }

                    Player.STATE_READY -> {
                        val playing = playerController.isPlaying()
                        val nextStatus = if (playing) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                        mutableUiState.update {
                            if (it.status == nextStatus && it.durationMs == duration && it.errorMessage == null) {
                                it
                            } else {
                                it.copy(
                                    status = nextStatus,
                                    durationMs = duration,
                                    errorMessage = null,
                                )
                            }
                        }
                        if (playing) {
                            currentTrackAttempt = 0
                            consecutiveTrackFailures = 0
                            recordCurrentItemToHistoryIfNeeded()
                            startTickerIfNeeded()
                            scheduleNextTrackStreamPrefetch()
                        } else {
                            stopTicker()
                        }
                    }

                    Player.STATE_ENDED -> {
                        stopTicker()
                        handleTrackEnded()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlayerSyncedToCurrentTrack()) return
                if (isPlaying) {
                    currentTrackAttempt = 0
                    consecutiveTrackFailures = 0
                    mutableUiState.update {
                        val dur = resolveDurationMs(it.currentTrack)
                        if (it.status == PlaybackStatus.PLAYING && it.durationMs == dur && it.errorMessage == null) {
                            it
                        } else {
                            it.copy(
                                status = PlaybackStatus.PLAYING,
                                durationMs = dur,
                                errorMessage = null,
                            )
                        }
                    }
                    recordCurrentItemToHistoryIfNeeded()
                    startTickerIfNeeded()
                    scheduleNextTrackStreamPrefetch()
                } else {
                    if (prefetchJob?.isActive == true) cancelStreamPrefetch()
                    stopTicker()
                    val pos = playerController.currentPositionMs()
                    mutablePositionMs.value = pos
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
                if (!isPlayerSyncedToCurrentTrack()) return
                stopTicker()
                val category = PlaybackErrorClassifier.classify(error)
                runCatching {
                    android.util.Log.w(
                        "AlunaraPlayback",
                        "onPlayerError track=${mutableUiState.value.currentTrack?.id} category=$category msg=${error.message}",
                        error,
                    )
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

            val storedSession = withContext(ioDispatcher) { runCatching { accountSessionStore.loadSession() }.getOrNull() }
            var resolvedAuth: AuthSessionState = AuthSessionState.Guest
            var librarySnapshot = AccountLibrarySnapshot()

            if (storedSession != null) {
                val (storedProfile, credentials) = storedSession
                val verifyResult = withContext(ioDispatcher) { musicEngine.verifyAccountSession(credentials) }
                verifyResult.fold(
                    onSuccess = { verifiedProfile ->
                        resolvedAuth = AuthSessionState.Authenticated(verifiedProfile)
                        librarySnapshot = fetchAccountLibrarySnapshot()
                    },
                    onFailure = { error ->
                        withContext(NonCancellable + ioDispatcher) {
                            runCatching { accountSessionStore.clearSession() }
                        }
                        musicEngine.clearAccountSession()
                        resolvedAuth =
                            AuthSessionState.Expired(
                                previousAccountId = storedProfile.accountId,
                                reason = error.message ?: "Session expired",
                            )
                    },
                )
            }

            val scopeKey = resolvedAuth.scope
            val favorites = withContext(ioDispatcher) { runCatching { catalogStore.getFavorites(scopeKey) }.getOrDefault(emptyList()) }
            val history = withContext(ioDispatcher) { runCatching { catalogStore.getRecentHistory(scopeKey) }.getOrDefault(emptyList()) }
            val mergedFavoriteIds = (favorites.map { it.id } + librarySnapshot.likedTracks.map { it.id }).toSet()

            val syncOffsetMs = item?.track?.id?.let { id -> playbackPreferences.getLyricsSyncOffsetMs(id) } ?: 0L
            mutablePositionMs.value = normalized.positionMs
            mutableUiState.update {
                it.copy(
                    queueState = normalized,
                    status = if (item != null) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
                    positionMs = normalized.positionMs,
                    durationMs = (item?.track?.durationSeconds ?: 0) * 1000L,
                    lyricsSyncOffsetMs = syncOffsetMs,
                    errorMessage = null,
                    authSession = resolvedAuth,
                    favorites = favorites,
                    favoriteIds = mergedFavoriteIds,
                    recentHistory = history,
                    accountPlaylists = librarySnapshot.playlists,
                    savedPlaylistIds = librarySnapshot.playlists.map { pl -> pl.id.removePrefix("VL") }.toSet(),
                    remoteFavorites = librarySnapshot.likedTracks,
                    remoteHistory = librarySnapshot.historyTracks,
                )
            }
        }
    }

    private suspend fun fetchAccountLibrarySnapshot(): AccountLibrarySnapshot =
        withContext(ioDispatcher) {
            coroutineScope {
                val playlistsDeferred = async { runCatching { musicEngine.fetchAccountPlaylists().getOrDefault(emptyList()) }.getOrDefault(emptyList()) }
                val likesDeferred = async { runCatching { musicEngine.fetchLikedMusicTracks().getOrDefault(emptyList()) }.getOrDefault(emptyList()) }
                val historyDeferred = async { runCatching { musicEngine.fetchAccountHistoryTracks().getOrDefault(emptyList()) }.getOrDefault(emptyList()) }
                AccountLibrarySnapshot(
                    playlists = playlistsDeferred.await(),
                    likedTracks = likesDeferred.await(),
                    historyTracks = historyDeferred.await(),
                )
            }
        }

    fun refreshAccountLibrary() {
        val activeState = mutableUiState.value
        if (activeState.authSession !is AuthSessionState.Authenticated) {
            mutableUiState.update { state ->
                state.copy(
                    accountPlaylists = emptyList(),
                    savedPlaylistIds = emptySet(),
                    subscribedArtistIds = emptySet(),
                    remoteFavorites = emptyList(),
                    remoteHistory = emptyList(),
                    favoriteIds = state.favorites.map { it.id }.toSet(),
                    librarySyncing = false,
                )
            }
            return
        }
        scope.launch {
            mutableUiState.update { it.copy(librarySyncing = true) }
            val snapshot = fetchAccountLibrarySnapshot()
            mutableUiState.update { state ->
                state.copy(
                    accountPlaylists = snapshot.playlists,
                    savedPlaylistIds = snapshot.playlists.map { pl -> pl.id.removePrefix("VL") }.toSet(),
                    remoteFavorites = snapshot.likedTracks,
                    remoteHistory = snapshot.historyTracks,
                    favoriteIds = (state.favorites.map { it.id } + snapshot.likedTracks.map { it.id }).toSet(),
                    librarySyncing = false,
                )
            }
        }
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        autoExpandRadio: Boolean = true,
    ) {
        if (tracks.isEmpty()) return
        radioJob?.cancel()
        val items =
            tracks.map { track ->
                QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
            }
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.SetQueue(items, startIndex))
        consecutiveTrackFailures = 0
        currentTrackAttempt = 0
        lastAutoplayExtendedTrackId = null
        cachedSource = null
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L, clearQueueChips = true)
        if (autoExpandRadio && tracks.size == 1) {
            expandQueueWithRadio(seedTrack = tracks.first(), replaceUpcoming = false)
        }
    }

    fun playTrackAt(index: Int) {
        val current = mutableUiState.value.queueState
        if (index !in current.items.indices) return
        val nextQueue = QueueReducer.reduce(current, QueueAction.SetQueue(current.items, index))
        consecutiveTrackFailures = 0
        currentTrackAttempt = 0
        lastAutoplayExtendedTrackId = null
        cachedSource = null
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
    }

    fun enqueue(track: Track) {
        enqueueAll(listOf(track))
    }

    fun enqueueAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val initialQueue = mutableUiState.value.queueState
        val wasEmpty = initialQueue.items.isEmpty()
        val newItems =
            tracks.map { track ->
                QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
            }
        val nextQueue = QueueReducer.reduce(initialQueue, QueueAction.EnqueueAll(newItems))
        mutableUiState.update {
            val activeTrack = nextQueue.currentItem?.track
            it.copy(
                queueState = nextQueue,
                status = if (wasEmpty) PlaybackStatus.PAUSED else it.status,
                durationMs = if (wasEmpty) (activeTrack?.durationSeconds ?: 0) * 1000L else it.durationMs,
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
                val nextOffset =
                    nextQueue.currentItem
                        ?.track
                        ?.id
                        ?.let(playbackPreferences::getLyricsSyncOffsetMs)
                        ?: 0L
                mutablePositionMs.value = 0L
                mutableUiState.update {
                    it.copy(
                        queueState = nextQueue,
                        positionMs = 0L,
                        durationMs = (nextQueue.currentItem?.track?.durationSeconds ?: 0) * 1000L,
                        lyricsSyncOffsetMs = nextOffset,
                        activeStreamCodecLabel = null,
                    )
                }
                persistQueueAsync(nextQueue)
            }
        } else {
            mutableUiState.update { it.copy(queueState = nextQueue) }
            persistQueueAsync(nextQueue)
            if (playerController.isPlaying()) {
                scheduleNextTrackStreamPrefetch()
            }
        }
    }

    fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.Move(fromIndex, toIndex))
        mutableUiState.update { it.copy(queueState = nextQueue) }
        persistQueueAsync(nextQueue)
        if (playerController.isPlaying()) {
            scheduleNextTrackStreamPrefetch()
        }
    }

    fun clearQueue() {
        cancelActiveWork()
        expectedPlayerTrackId = null
        playerController.onAppBackgroundedOrStopped()
        onBackgroundServiceStateChanged(false)
        cachedSource = null
        currentTrackAttempt = 0
        consecutiveTrackFailures = 0
        lastAutoplayExtendedTrackId = null
        val empty = mutableUiState.value.queueState.copy(items = emptyList(), currentIndex = -1, positionMs = 0L)
        mutablePositionMs.value = 0L
        mutableActiveLyricIndex.value = -1
        mutableUiState.update { state ->
            state.copy(
                queueState = empty,
                status = PlaybackStatus.IDLE,
                positionMs = 0L,
                durationMs = 0L,
                lyrics = LyricsDocument.Unavailable(""),
                lyricsLoading = false,
                selectedLyricsProvider = null,
                activeLyricIndex = -1,
                lyricsSyncOffsetMs = 0L,
                showRomanizedLyrics = false,
                hasRomanizedLyrics = false,
                hasPartialKanjiNotice = false,
                activeStreamCodecLabel = null,
                errorMessage = null,
                queueChips = emptyList(),
                activeQueueChipLabel = null,
                radioLoading = false,
            )
        }
        persistQueueAsync(empty)
    }

    fun startRadio(
        seedTrack: Track,
        playlistId: String? = null,
    ) {
        radioJob?.cancel()
        playTracks(listOf(seedTrack), 0, autoExpandRadio = false)
        expandQueueWithRadio(seedTrack = seedTrack, playlistId = playlistId, replaceUpcoming = false)
    }

    fun refreshCurrentTrackRadio() {
        val current = mutableUiState.value.currentTrack ?: return
        expandQueueWithRadio(seedTrack = current, replaceUpcoming = true, selectedChipLabel = null)
    }

    fun selectQueueChip(chip: QueueFilterChip?) {
        val current = mutableUiState.value.currentTrack ?: return
        if (chip == null) {
            expandQueueWithRadio(seedTrack = current, replaceUpcoming = true, selectedChipLabel = null)
        } else {
            expandQueueWithRadio(
                seedTrack = current,
                playlistId = chip.playlistId,
                params = chip.params,
                replaceUpcoming = true,
                selectedChipLabel = chip.label,
            )
        }
    }

    private fun expandQueueWithRadio(
        seedTrack: Track,
        playlistId: String? = null,
        params: String? = null,
        replaceUpcoming: Boolean,
        selectedChipLabel: String? = null,
    ) {
        radioJob?.cancel()
        radioJob =
            scope.launch {
                mutableUiState.update { it.copy(radioLoading = true) }
                val radioResult =
                    withContext(ioDispatcher) {
                        musicEngine.fetchRadioQueue(seedTrack.id, playlistId, params)
                    }.getOrNull()
                if (!isActive || mutableUiState.value.currentTrack?.id != seedTrack.id) {
                    mutableUiState.update { it.copy(radioLoading = false) }
                    return@launch
                }
                val fetchedTracks = radioResult?.tracks.orEmpty()
                val fetchedChips = radioResult?.chips.orEmpty()
                val resolvedChipLabel =
                    selectedChipLabel ?: fetchedChips.firstOrNull { it.selected }?.label
                val currentQueue = mutableUiState.value.queueState
                val baseQueue =
                    if (replaceUpcoming && currentQueue.currentIndex in currentQueue.items.indices) {
                        val keptItems = currentQueue.items.take(currentQueue.currentIndex + 1)
                        QueueReducer.reduce(currentQueue, QueueAction.SetQueue(keptItems, currentQueue.currentIndex))
                    } else {
                        currentQueue
                    }
                val existingIds = baseQueue.items.map { it.track.id }.toSet()
                val additional =
                    fetchedTracks
                        .filter { it.id !in existingIds }
                        .map { track ->
                            QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
                        }
                val nextQueue = QueueReducer.reduce(baseQueue, QueueAction.EnqueueAll(additional))
                mutableUiState.update { state ->
                    state.copy(
                        queueState = nextQueue,
                        queueChips = if (fetchedChips.isNotEmpty()) fetchedChips else state.queueChips,
                        activeQueueChipLabel = resolvedChipLabel,
                        radioLoading = false,
                    )
                }
                if (nextQueue != currentQueue) {
                    persistQueueAsync(nextQueue)
                    if (playerController.isPlaying()) {
                        scheduleNextTrackStreamPrefetch()
                    }
                }
            }
    }

    fun clearSyncNotice() {
        mutableUiState.update { if (it.syncNotice == null) it else it.copy(syncNotice = null) }
    }

    private fun emitSyncNotice(
        kind: SyncNoticeKind,
        detail: String? = null,
    ) {
        mutableUiState.update {
            it.copy(
                syncNotice =
                    SyncNotice(
                        id = System.nanoTime(),
                        kind = kind,
                        detail = detail,
                    ),
            )
        }
    }

    fun toggleFavorite(track: Track? = mutableUiState.value.currentTrack) {
        val target = track ?: return
        val activeState = mutableUiState.value
        val activeScope = activeState.authSession.scope
        val isAuthenticated = activeState.authSession is AuthSessionState.Authenticated
        val wasLikedAnywhere = target.id in activeState.favoriteIds
        val inLocalStore = activeState.favorites.any { it.id == target.id }
        val prevFavorites = activeState.favorites
        val prevRemoteFavorites = activeState.remoteFavorites
        val prevFavoriteIds = activeState.favoriteIds

        scope.launch {
            val nowLiked =
                if (wasLikedAnywhere && !inLocalStore) {
                    false
                } else {
                    withContext(ioDispatcher) {
                        catalogStore.toggleFavorite(target, activeScope)
                    }
                }
            val refreshed =
                withContext(ioDispatcher) {
                    runCatching { catalogStore.getFavorites(activeScope) }.getOrDefault(emptyList())
                }
            mutableUiState.update { state ->
                val updatedRemoteFavs =
                    if (isAuthenticated) {
                        if (nowLiked) {
                            (listOf(target) + state.remoteFavorites).distinctBy { it.id }
                        } else {
                            state.remoteFavorites.filterNot { it.id == target.id }
                        }
                    } else {
                        state.remoteFavorites
                    }
                state.copy(
                    favorites = refreshed,
                    remoteFavorites = updatedRemoteFavs,
                    favoriteIds = (refreshed.map { it.id } + updatedRemoteFavs.map { it.id }).toSet(),
                )
            }
            if (isAuthenticated) {
                val remoteResult =
                    withContext(ioDispatcher) {
                        runCatching { musicEngine.setRemoteTrackLike(target.id, nowLiked) }
                            .getOrElse { Result.failure(it) }
                    }
                if (remoteResult.isFailure) {
                    if (inLocalStore != nowLiked) {
                        withContext(ioDispatcher) {
                            runCatching { catalogStore.toggleFavorite(target, activeScope) }
                        }
                    }
                    mutableUiState.update { state ->
                        state.copy(
                            favorites = prevFavorites,
                            remoteFavorites = prevRemoteFavorites,
                            favoriteIds = prevFavoriteIds,
                        )
                    }
                    emitSyncNotice(SyncNoticeKind.FAVORITE_SYNC_FAILED, target.title)
                }
            }
        }
    }

    fun createAccountPlaylist(
        title: String,
        initialTrack: Track? = null,
        onComplete: (String?) -> Unit = {},
    ) {
        val cleanTitle = title.trim()
        val state = mutableUiState.value
        if (cleanTitle.isEmpty() || state.authSession !is AuthSessionState.Authenticated) {
            onComplete(null)
            return
        }
        scope.launch {
            val initialIds = listOfNotNull(initialTrack?.id)
            val result =
                withContext(ioDispatcher) {
                    runCatching { musicEngine.createAccountPlaylist(cleanTitle, initialIds) }
                        .getOrElse { Result.failure(it) }
                }
            result.fold(
                onSuccess = { createdId ->
                    emitSyncNotice(SyncNoticeKind.PLAYLIST_CREATED, cleanTitle)
                    refreshAccountLibrary()
                    onComplete(createdId)
                },
                onFailure = { err ->
                    emitSyncNotice(SyncNoticeKind.SYNC_FAILED, err.message)
                    onComplete(null)
                },
            )
        }
    }

    fun addTrackToAccountPlaylist(
        playlist: PlaylistSummary,
        track: Track,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (mutableUiState.value.authSession !is AuthSessionState.Authenticated) {
            onComplete(false)
            return
        }
        scope.launch {
            val result =
                withContext(ioDispatcher) {
                    runCatching { musicEngine.addTrackToAccountPlaylist(playlist.id, track.id) }
                        .getOrElse { Result.failure(it) }
                }
            result.fold(
                onSuccess = {
                    emitSyncNotice(SyncNoticeKind.PLAYLIST_ADDED, playlist.title)
                    refreshAccountLibrary()
                    onComplete(true)
                },
                onFailure = { err ->
                    emitSyncNotice(SyncNoticeKind.SYNC_FAILED, err.message)
                    onComplete(false)
                },
            )
        }
    }

    fun removeTrackFromAccountPlaylist(
        playlistId: String,
        track: Track,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (mutableUiState.value.authSession !is AuthSessionState.Authenticated) {
            onComplete(false)
            return
        }
        scope.launch {
            val result =
                withContext(ioDispatcher) {
                    runCatching {
                        musicEngine.removeTrackFromAccountPlaylist(
                            playlistId = playlistId,
                            trackId = track.id,
                            setVideoId = track.setVideoId,
                        )
                    }.getOrElse { Result.failure(it) }
                }
            result.fold(
                onSuccess = {
                    emitSyncNotice(SyncNoticeKind.PLAYLIST_REMOVED, track.title)
                    refreshAccountLibrary()
                    onComplete(true)
                },
                onFailure = { err ->
                    emitSyncNotice(SyncNoticeKind.SYNC_FAILED, err.message)
                    onComplete(false)
                },
            )
        }
    }

    fun toggleSavePlaylist(playlist: PlaylistSummary) {
        val state = mutableUiState.value
        if (state.authSession !is AuthSessionState.Authenticated) return
        val cleanId = playlist.id.removePrefix("VL")
        val wasSaved = cleanId in state.savedPlaylistIds
        val targetSaved = !wasSaved
        val prevSavedIds = state.savedPlaylistIds
        val prevPlaylists = state.accountPlaylists

        mutableUiState.update { current ->
            val nextSavedIds = if (targetSaved) current.savedPlaylistIds + cleanId else current.savedPlaylistIds - cleanId
            val nextPlaylists =
                if (targetSaved) {
                    (listOf(playlist) + current.accountPlaylists).distinctBy { it.id.removePrefix("VL") }
                } else {
                    current.accountPlaylists.filterNot { it.id.removePrefix("VL") == cleanId }
                }
            current.copy(savedPlaylistIds = nextSavedIds, accountPlaylists = nextPlaylists)
        }

        scope.launch {
            val result =
                withContext(ioDispatcher) {
                    runCatching { musicEngine.setRemotePlaylistSaved(cleanId, targetSaved) }
                        .getOrElse { Result.failure(it) }
                }
            result.fold(
                onSuccess = {
                    emitSyncNotice(
                        if (targetSaved) SyncNoticeKind.PLAYLIST_SAVED else SyncNoticeKind.PLAYLIST_UNSAVED,
                        playlist.title,
                    )
                    refreshAccountLibrary()
                },
                onFailure = { err ->
                    mutableUiState.update { current ->
                        current.copy(savedPlaylistIds = prevSavedIds, accountPlaylists = prevPlaylists)
                    }
                    emitSyncNotice(SyncNoticeKind.SYNC_FAILED, err.message)
                },
            )
        }
    }

    fun toggleArtistSubscription(
        artistId: String,
        channelId: String?,
        artistName: String? = null,
    ) {
        val state = mutableUiState.value
        if (state.authSession !is AuthSessionState.Authenticated) return
        val prevSubs = state.subscribedArtistIds
        val targetSubscribed = artistId !in prevSubs
        mutableUiState.update { current ->
            val nextSubs = if (targetSubscribed) current.subscribedArtistIds + artistId else current.subscribedArtistIds - artistId
            current.copy(subscribedArtistIds = nextSubs)
        }
        scope.launch {
            val result =
                withContext(ioDispatcher) {
                    runCatching {
                        musicEngine.setRemoteArtistSubscription(
                            artistId = artistId,
                            channelId = channelId,
                            subscribed = targetSubscribed,
                        )
                    }.getOrElse { Result.failure(it) }
                }
            result.fold(
                onSuccess = {
                    emitSyncNotice(
                        if (targetSubscribed) SyncNoticeKind.ARTIST_SUBSCRIBED else SyncNoticeKind.ARTIST_UNSUBSCRIBED,
                        artistName,
                    )
                },
                onFailure = { err ->
                    mutableUiState.update { current -> current.copy(subscribedArtistIds = prevSubs) }
                    emitSyncNotice(SyncNoticeKind.SYNC_FAILED, err.message)
                },
            )
        }
    }

    fun clearHistory() {
        val activeScope = mutableUiState.value.authSession.scope
        scope.launch {
            withContext(ioDispatcher) {
                catalogStore.clearHistory(activeScope)
            }
            mutableUiState.update { it.copy(recentHistory = emptyList()) }
        }
    }

    fun loginWithCredentials(credentials: AccountCredentials) {
        scope.launch {
            mutableUiState.update { it.copy(authLoading = true, authErrorMessage = null) }
            val result = activateAccountSession(credentials)
            mutableUiState.update {
                it.copy(
                    authLoading = false,
                    authErrorMessage = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    suspend fun activateAccountSession(credentials: AccountCredentials): Result<AccountProfile> {
        val verifyResult = withContext(ioDispatcher) { musicEngine.verifyAccountSession(credentials) }
        return verifyResult.fold(
            onSuccess = { profile ->
                withContext(NonCancellable + ioDispatcher) {
                    accountSessionStore.saveSession(profile, credentials)
                }
                val newScope = AccountScope.Account(profile.accountId)
                val favorites = withContext(ioDispatcher) { runCatching { catalogStore.getFavorites(newScope) }.getOrDefault(emptyList()) }
                val history = withContext(ioDispatcher) { runCatching { catalogStore.getRecentHistory(newScope) }.getOrDefault(emptyList()) }
                val snapshot = fetchAccountLibrarySnapshot()
                mutableUiState.update {
                    it.copy(
                        authSession = AuthSessionState.Authenticated(profile),
                        favorites = favorites,
                        favoriteIds = (favorites.map { fav -> fav.id } + snapshot.likedTracks.map { fav -> fav.id }).toSet(),
                        recentHistory = history,
                        accountPlaylists = snapshot.playlists,
                        savedPlaylistIds = snapshot.playlists.map { pl -> pl.id.removePrefix("VL") }.toSet(),
                        remoteFavorites = snapshot.likedTracks,
                        remoteHistory = snapshot.historyTracks,
                    )
                }
                Result.success(profile)
            },
            onFailure = { error -> Result.failure(error) },
        )
    }

    fun logoutAccount() {
        val previousAuth = mutableUiState.value.authSession
        scope.launch {
            runCatching {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
            }
            withContext(NonCancellable + ioDispatcher) {
                if (previousAuth is AuthSessionState.Authenticated) {
                    runCatching { catalogStore.clearAccountData(AccountScope.Account(previousAuth.profile.accountId)) }
                }
                runCatching { accountSessionStore.clearSession() }
            }
            musicEngine.clearAccountSession()
            val guestScope = AccountScope.Guest
            val guestFavorites = withContext(ioDispatcher) { runCatching { catalogStore.getFavorites(guestScope) }.getOrDefault(emptyList()) }
            val guestHistory = withContext(ioDispatcher) { runCatching { catalogStore.getRecentHistory(guestScope) }.getOrDefault(emptyList()) }
            mutableUiState.update {
                it.copy(
                    authSession = AuthSessionState.Guest,
                    authLoading = false,
                    authErrorMessage = null,
                    favorites = guestFavorites,
                    favoriteIds = guestFavorites.map { fav -> fav.id }.toSet(),
                    recentHistory = guestHistory,
                    accountPlaylists = emptyList(),
                    savedPlaylistIds = emptySet(),
                    subscribedArtistIds = emptySet(),
                    remoteFavorites = emptyList(),
                    remoteHistory = emptyList(),
                )
            }
        }
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
                onBackgroundServiceStateChanged(true)
                playerController.play()
            }
        } else {
            currentTrackAttempt = 0
            resolveAndPlay(currentTrack, startPositionMs = state.positionMs)
            if (lyricsPanelVisible) ensureLyricsLoaded()
        }
    }

    fun setLyricsPanelVisible(visible: Boolean) {
        if (lyricsPanelVisible == visible) return
        lyricsPanelVisible = visible
        if (visible) {
            ensureLyricsLoaded()
            updateVisibleLyricsPosition()
        } else if (lyricsJob?.isActive == true) {
            lyricsJob?.cancel()
            lyricsJob = null
            mutableUiState.update { it.copy(lyricsLoading = false) }
        }
        if (nowPlayingVisible && playerController.isPlaying()) {
            stopTicker()
            startTickerIfNeeded()
        }
    }

    fun requestLyricsForRemote() {
        ensureLyricsLoaded()
    }

    private fun ensureLyricsLoaded() {
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

    fun setNowPlayingVisible(visible: Boolean) {
        val wasVisible = nowPlayingVisible
        nowPlayingVisible = visible
        if (visible) {
            updateVisibleLyricsPosition()
            if (!wasVisible && playerController.isPlaying()) {
                stopTicker()
                startTickerIfNeeded()
            }
        } else if (wasVisible && playerController.isPlaying()) {
            stopTicker()
            startTickerIfNeeded()
        }
    }

    private fun updateVisibleLyricsPosition() {
        val livePos =
            if (playerController.hasActivePlayer && isPlayerSyncedToCurrentTrack()) {
                playerController.currentPositionMs()
            } else {
                mutablePositionMs.value
            }
        mutablePositionMs.value = livePos
        if (lyricsPanelVisible) {
            val state = mutableUiState.value
            mutableActiveLyricIndex.value = computeActiveLyricIndex(state.lyrics, livePos, state.lyricsSyncOffsetMs)
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        if (playerController.hasActivePlayer) {
            playerController.seekTo(safePos)
        }
        mutablePositionMs.value = safePos
        mutableUiState.update { state ->
            val updatedQueue = QueueReducer.reduce(state.queueState, QueueAction.UpdatePosition(safePos))
            val computedIdx =
                if (lyricsPanelVisible) {
                    computeActiveLyricIndex(state.lyrics, safePos, state.lyricsSyncOffsetMs)
                        .also { mutableActiveLyricIndex.value = it }
                } else {
                    state.activeLyricIndex
                }
            state.copy(
                queueState = updatedQueue,
                positionMs = safePos,
                activeLyricIndex = computedIdx,
            )
        }
    }

    fun setAutoplay(enabled: Boolean) {
        val nextQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.SetAutoplay(enabled))
        mutableUiState.update { it.copy(queueState = nextQueue) }
        persistQueueAsync(nextQueue)
    }

    fun setAudioQuality(preset: AudioQualityPreset) {
        val currentState = mutableUiState.value
        playbackPreferences.setAudioQuality(preset)
        if (currentState.audioQuality == preset) {
            return
        }

        cachedSource = null
        mutableUiState.update { it.copy(audioQuality = preset) }

        val activeTrack = currentState.currentTrack ?: return
        val shouldReloadStream =
            playerController.hasActivePlayer ||
                currentState.status == PlaybackStatus.PLAYING ||
                currentState.status == PlaybackStatus.BUFFERING ||
                currentState.status == PlaybackStatus.RESOLVING
        if (shouldReloadStream) {
            val resumePosMs =
                if (playerController.hasActivePlayer) {
                    playerController.currentPositionMs()
                } else {
                    currentState.positionMs
                }
            val shouldPlay =
                playerController.isPlaying() ||
                    currentState.status == PlaybackStatus.PLAYING ||
                    currentState.status == PlaybackStatus.BUFFERING ||
                    currentState.status == PlaybackStatus.RESOLVING
            currentTrackAttempt = 0
            resolveAndPlay(activeTrack, startPositionMs = resumePosMs, playWhenReady = shouldPlay)
        }
    }

    fun adjustLyricsSyncOffset(deltaMs: Long) {
        val state = mutableUiState.value
        val trackId = state.currentTrack?.id ?: return
        val updated =
            (state.lyricsSyncOffsetMs + deltaMs).coerceIn(
                InMemoryPlaybackPreferences.MIN_LYRICS_OFFSET_MS,
                InMemoryPlaybackPreferences.MAX_LYRICS_OFFSET_MS,
            )
        applyLyricsSyncOffset(trackId, updated)
    }

    fun resetLyricsSyncOffset() {
        val trackId = mutableUiState.value.currentTrack?.id ?: return
        applyLyricsSyncOffset(trackId, 0L)
    }

    fun toggleRomanizedLyrics() {
        mutableUiState.update { state ->
            if (!state.hasRomanizedLyrics) {
                state
            } else {
                state.copy(showRomanizedLyrics = !state.showRomanizedLyrics)
            }
        }
    }

    private fun applyLyricsSyncOffset(
        trackId: String,
        offsetMs: Long,
    ) {
        playbackPreferences.setLyricsSyncOffsetMs(trackId, offsetMs)
        mutableUiState.update { state ->
            if (state.currentTrack?.id != trackId) {
                state
            } else {
                val currentPos =
                    if (playerController.hasActivePlayer) {
                        playerController.currentPositionMs()
                    } else {
                        mutablePositionMs.value.takeIf { it > 0L } ?: state.positionMs
                    }
                val computedIdx = computeActiveLyricIndex(state.lyrics, currentPos, offsetMs)
                mutableActiveLyricIndex.value = computedIdx
                state.copy(
                    lyricsSyncOffsetMs = offsetMs,
                    activeLyricIndex = computedIdx,
                )
            }
        }
    }

    /**
     * Persists playback progress when the UI leaves foreground while keeping active queued playback
     * alive under [AlunaraPlaybackService]. Releases the player only if no track is queued.
     */
    fun onAppBackgrounded() {
        val state = mutableUiState.value
        prefetchJob?.cancel()
        prefetchJob = null
        radioJob?.cancel()
        radioJob = null
        if (state.queueState.currentItem == null) {
            onAppBackgroundedOrStopped()
            return
        }
        val capturedPosition =
            if (playerController.hasActivePlayer) {
                playerController.currentPositionMs()
            } else {
                state.positionMs
            }
        val updatedQueue = QueueReducer.reduce(state.queueState, QueueAction.UpdatePosition(capturedPosition))
        mutablePositionMs.value = capturedPosition
        mutableUiState.update { current ->
            current.copy(
                queueState = updatedQueue,
                positionMs = capturedPosition,
                radioLoading = false,
            )
        }
        scope.launch(NonCancellable + ioDispatcher) {
            runCatching { queueStore.saveQueue(updatedQueue) }
        }
    }

    override fun onAppBackgroundedOrStopped() {
        cancelActiveWork()
        expectedPlayerTrackId = null
        val capturedPosition =
            if (playerController.hasActivePlayer) {
                playerController.currentPositionMs()
            } else {
                mutableUiState.value.positionMs
            }
        playerController.onAppBackgroundedOrStopped()
        onBackgroundServiceStateChanged(false)

        val updatedQueue = QueueReducer.reduce(mutableUiState.value.queueState, QueueAction.UpdatePosition(capturedPosition))
        mutablePositionMs.value = capturedPosition
        mutableUiState.update { state ->
            state.copy(
                queueState = updatedQueue,
                positionMs = capturedPosition,
                status = if (updatedQueue.currentItem != null) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
                activeStreamCodecLabel = null,
                radioLoading = false,
            )
        }
        scope.launch(NonCancellable + ioDispatcher) {
            runCatching { queueStore.saveQueue(updatedQueue) }
        }
    }

    private fun recordCurrentItemToHistoryIfNeeded() {
        val state = mutableUiState.value
        val currentItem = state.queueState.currentItem ?: return
        if (lastRecordedQueueItemId == currentItem.queueItemId) return
        lastRecordedQueueItemId = currentItem.queueItemId
        val activeScope = state.authSession.scope
        val isAuthenticated = state.authSession is AuthSessionState.Authenticated
        val trackingUrl = cachedSource?.takeIf { it.trackId == currentItem.track.id }?.playbackTrackingUrl

        scope.launch {
            withContext(ioDispatcher) {
                runCatching { catalogStore.recordHistory(currentItem.track, activeScope) }
                if (isAuthenticated) {
                    runCatching {
                        musicEngine.reportPlaybackToAccount(
                            trackId = currentItem.track.id,
                            playbackTrackingUrl = trackingUrl,
                        )
                    }
                }
            }
            val refreshed =
                withContext(ioDispatcher) {
                    runCatching { catalogStore.getRecentHistory(activeScope) }.getOrDefault(emptyList())
                }
            mutableUiState.update { current ->
                val updatedRemoteHistory =
                    if (isAuthenticated) {
                        (listOf(currentItem.track) + current.remoteHistory).distinctBy { it.id }
                    } else {
                        current.remoteHistory
                    }
                if (current.recentHistory == refreshed && current.remoteHistory == updatedRemoteHistory) {
                    current
                } else {
                    current.copy(
                        recentHistory = refreshed,
                        remoteHistory = updatedRemoteHistory,
                    )
                }
            }
        }
    }

    private fun applyQueueAndStartTrack(
        nextQueue: QueueState,
        startPositionMs: Long,
        clearQueueChips: Boolean = false,
    ) {
        cancelActiveWork()
        expectedPlayerTrackId = null
        if (playerController.hasActivePlayer) {
            playerController.stopCurrentTrack()
        }
        val track = nextQueue.currentItem?.track
        if (track == null) {
            clearQueue()
            return
        }
        val trackOffsetMs = playbackPreferences.getLyricsSyncOffsetMs(track.id)
        mutablePositionMs.value = startPositionMs
        mutableActiveLyricIndex.value = -1
        mutableUiState.update {
            it.copy(
                queueState = nextQueue,
                status = PlaybackStatus.RESOLVING,
                positionMs = startPositionMs,
                durationMs = (track.durationSeconds ?: 0) * 1000L,
                lyrics = LyricsDocument.Unavailable(""),
                lyricsLoading = false,
                selectedLyricsProvider = null,
                activeLyricIndex = -1,
                showRomanizedLyrics = false,
                hasRomanizedLyrics = false,
                hasPartialKanjiNotice = false,
                lyricsSyncOffsetMs = trackOffsetMs,
                activeStreamCodecLabel = null,
                errorMessage = null,
                queueChips = if (clearQueueChips) emptyList() else it.queueChips,
                activeQueueChipLabel = if (clearQueueChips) null else it.activeQueueChipLabel,
            )
        }
        persistQueueAsync(nextQueue)
        resolveAndPlay(track, startPositionMs)
        if (lyricsPanelVisible) ensureLyricsLoaded()
    }

    private fun resolveAndPlay(
        track: Track,
        startPositionMs: Long,
        playWhenReady: Boolean = true,
    ) {
        resolveJob?.cancel()
        val requestedQuality = mutableUiState.value.audioQuality
        val syncOffsetMs = playbackPreferences.getLyricsSyncOffsetMs(track.id)
        resolveJob =
            scope.launch {
                mutableUiState.update {
                    if (it.status == PlaybackStatus.RESOLVING &&
                        it.lyricsSyncOffsetMs == syncOffsetMs &&
                        it.errorMessage == null
                    ) {
                        it
                    } else {
                        it.copy(
                            status = PlaybackStatus.RESOLVING,
                            lyricsSyncOffsetMs = syncOffsetMs,
                            errorMessage = null,
                        )
                    }
                }
                val cached =
                    cachedSource?.takeIf {
                        it.trackId == track.id && it.expiresAtEpochMs > System.currentTimeMillis() + 30_000L
                    }
                val streamResult =
                    if (cached != null) {
                        Result.success(cached)
                    } else {
                        withContext(ioDispatcher) { musicEngine.resolveAudioStream(track.id, requestedQuality) }
                    }

                if (!isActive || mutableUiState.value.currentTrack?.id != track.id) return@launch

                streamResult.fold(
                    onSuccess = { source ->
                        cachedSource = source
                        val codecBadge = formatStreamBadge(source)
                        mutableUiState.update { it.copy(activeStreamCodecLabel = codecBadge) }
                        onBackgroundServiceStateChanged(true)
                        expectedPlayerTrackId = track.id
                        playerController.prepareAudioStream(
                            source = source,
                            startPositionMs = startPositionMs,
                            playWhenReady = playWhenReady,
                            listener = playerListener,
                            track = track,
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

    internal fun handlePlaybackFailure(
        category: FailureCategory,
        message: String,
    ) {
        val state = mutableUiState.value
        if (category == FailureCategory.EXPIRED_SOURCE || category == FailureCategory.AUTH_INVALID) {
            cachedSource?.let { musicEngine.invalidateAudioStream(it, state.audioQuality) }
            cachedSource = null
        }
        if (category == FailureCategory.AUTH_INVALID && state.authSession is AuthSessionState.Authenticated) {
            val expiredId = state.authSession.profile.accountId
            musicEngine.clearAccountSession()
            scope.launch {
                withContext(NonCancellable + ioDispatcher) {
                    runCatching { accountSessionStore.clearSession() }
                }
                val guestFav = withContext(ioDispatcher) { runCatching { catalogStore.getFavorites(AccountScope.Guest) }.getOrDefault(emptyList()) }
                val guestHist = withContext(ioDispatcher) { runCatching { catalogStore.getRecentHistory(AccountScope.Guest) }.getOrDefault(emptyList()) }
                mutableUiState.update { current ->
                    current.copy(
                        authSession = AuthSessionState.Expired(previousAccountId = expiredId, reason = "Session expired or revoked"),
                        favorites = guestFav,
                        favoriteIds = guestFav.map { it.id }.toSet(),
                        recentHistory = guestHist,
                        accountPlaylists = emptyList(),
                        savedPlaylistIds = emptySet(),
                        subscribedArtistIds = emptySet(),
                        remoteFavorites = emptyList(),
                        remoteHistory = emptyList(),
                    )
                }
            }
        }

        val currentState = mutableUiState.value
        val track = currentState.currentTrack ?: return
        val decision =
            retryPolicy.evaluate(
                category = category,
                attemptIndex = currentTrackAttempt,
                consecutiveTrackFailures = consecutiveTrackFailures,
                queueSize = currentState.queueState.items.size,
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
                    resolveAndPlay(track, startPositionMs = currentState.positionMs)
                }
        } else if (decision.shouldSkipToNext && currentState.queueState.currentIndex < currentState.queueState.items.lastIndex) {
            consecutiveTrackFailures++
            currentTrackAttempt = 0
            cachedSource = null
            val nextQueue = QueueReducer.reduce(currentState.queueState, QueueAction.SkipNext)
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
            return
        }

        val currentTrack = state.currentTrack
        if (queue.autoplayEnabled && currentTrack != null && lastAutoplayExtendedTrackId != currentTrack.id) {
            lastAutoplayExtendedTrackId = currentTrack.id
            radioJob?.cancel()
            radioJob =
                scope.launch {
                    mutableUiState.update { it.copy(radioLoading = true) }
                    val recommended =
                        withContext(ioDispatcher) {
                            musicEngine.fetchRadioTracks(currentTrack.id)
                        }.getOrDefault(emptyList())
                    val currentState = mutableUiState.value
                    val existingIds = currentState.queueState.items.map { it.track.id }.toSet()
                    val newTracks = recommended.filter { it.id !in existingIds }
                    if (isActive && currentState.currentTrack?.id == currentTrack.id && newTracks.isNotEmpty()) {
                        val nextStartIndex = currentState.queueState.items.size
                        val newItems =
                            newTracks.map { track ->
                                QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
                            }
                        var extended = QueueReducer.reduce(currentState.queueState, QueueAction.EnqueueAll(newItems))
                        extended = QueueReducer.reduce(extended, QueueAction.SetQueue(extended.items, nextStartIndex))
                        mutableUiState.update { it.copy(radioLoading = false) }
                        currentTrackAttempt = 0
                        cachedSource = null
                        applyQueueAndStartTrack(extended, startPositionMs = 0L)
                    } else {
                        mutableUiState.update {
                            mutablePositionMs.value = it.durationMs
                            it.copy(
                                status = PlaybackStatus.ENDED,
                                positionMs = it.durationMs,
                                radioLoading = false,
                            )
                        }
                    }
                }
            return
        }

        mutableUiState.update {
            mutablePositionMs.value = it.durationMs
            it.copy(
                status = PlaybackStatus.ENDED,
                positionMs = it.durationMs,
            )
        }
    }

    private fun fetchLyricsForTrack(
        track: Track,
        preferredProvider: LyricsProviderId? = null,
    ) {
        lyricsJob?.cancel()
        lyricsJob =
            scope.launch {
                mutableActiveLyricIndex.value = -1
                mutableUiState.update {
                    it.copy(
                        lyricsLoading = true,
                        selectedLyricsProvider = preferredProvider,
                        lyrics = LyricsDocument.Unavailable(""),
                        activeLyricIndex = -1,
                        showRomanizedLyrics = false,
                        hasRomanizedLyrics = false,
                        hasPartialKanjiNotice = false,
                    )
                }
                val summary =
                    withContext(ioDispatcher) {
                        val fetched =
                            try {
                                lyricsEngine.fetchLyrics(track, preferredProvider)
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (t: Throwable) {
                                LyricsDocument.Unavailable(t.message ?: "Lyrics unavailable")
                            }
                        LyricsRomanizer.attachRomanization(fetched)
                    }
                if (!isActive || mutableUiState.value.currentTrack?.id != track.id) return@launch
                val livePosMs =
                    if (playerController.hasActivePlayer) {
                        playerController.currentPositionMs()
                    } else {
                        mutablePositionMs.value.takeIf { it > 0L } ?: mutableUiState.value.positionMs
                    }
                mutableUiState.update { state ->
                    val initialIdx = computeActiveLyricIndex(summary.document, livePosMs, state.lyricsSyncOffsetMs)
                    mutableActiveLyricIndex.value = initialIdx
                    state.copy(
                        lyrics = summary.document,
                        lyricsLoading = false,
                        activeLyricIndex = initialIdx,
                        showRomanizedLyrics = summary.hasRomanizableLines,
                        hasRomanizedLyrics = summary.hasRomanizableLines,
                        hasPartialKanjiNotice = summary.hasUnsupportedKanji,
                    )
                }
            }
    }

    private fun scheduleNextTrackStreamPrefetch() {
        val state = mutableUiState.value
        val currentItem = state.queueState.currentItem ?: return
        val nextItem = state.queueState.items.getOrNull(state.queueState.currentIndex + 1)
        if (nextItem == null || nextItem.track.id == currentItem.track.id || isLowMemoryPressure()) {
            cancelStreamPrefetch()
            return
        }
        val target = nextItem.queueItemId to state.audioQuality
        if (prefetchTarget == target) return
        cancelStreamPrefetch()
        prefetchTarget = target

        prefetchJob =
            scope.launch {
                delay(prefetchDelayMs)
                val latest = mutableUiState.value
                if (!isActive ||
                    latest.queueState.currentItem?.queueItemId != currentItem.queueItemId ||
                    latest.queueState.items.getOrNull(latest.queueState.currentIndex + 1)?.queueItemId != nextItem.queueItemId ||
                    latest.audioQuality != state.audioQuality ||
                    !playerController.isPlaying() ||
                    isLowMemoryPressure()
                ) {
                    prefetchTarget = null
                    return@launch
                }
                withContext(ioDispatcher) {
                    try {
                        musicEngine.resolveAudioStream(nextItem.track.id, state.audioQuality)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        // Let playback resolve the stream when the item becomes current.
                    }
                }
            }
    }

    private fun cancelStreamPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchTarget = null
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob =
            scope.launch {
                while (isActive && playerController.isPlaying()) {
                    val pos = playerController.currentPositionMs()
                    mutablePositionMs.value = pos
                    val currentState = mutableUiState.value
                    val dur = resolveDurationMs(currentState.currentTrack)
                    if (dur != currentState.durationMs) {
                        mutableUiState.update { it.copy(durationMs = dur) }
                    }
                    // Skip lyric math and run a quiet 1 Hz progress tick when browsing Home/Library/Settings;
                    // in Now Playing, emit only to isolated positionMsFlow & activeLyricIndexFlow (never uiState).
                    val tickDelay =
                        if (nowPlayingVisible && lyricsPanelVisible) {
                            mutableActiveLyricIndex.value =
                                computeActiveLyricIndex(
                                    currentState.lyrics,
                                    pos,
                                    currentState.lyricsSyncOffsetMs,
                                )
                            if ((currentState.lyrics as? LyricsDocument.Synced)?.isWordSynced == true) {
                                WORD_SYNC_TICKER_INTERVAL_MS
                            } else {
                                LINE_SYNC_TICKER_INTERVAL_MS
                            }
                        } else if (nowPlayingVisible) {
                            NOW_PLAYING_PROGRESS_TICKER_INTERVAL_MS
                        } else {
                            BROWSE_TICKER_INTERVAL_MS
                        }
                    delay(tickDelay)
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
        cancelStreamPrefetch()
        radioJob?.cancel()
        radioJob = null
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
        syncOffsetMs: Long = 0L,
    ): Int =
        when (lyrics) {
            is LyricsDocument.Synced ->
                LyricsSyncResolver.findActiveLineIndex(
                    lyrics.lines,
                    (positionMs + syncOffsetMs).coerceAtLeast(0L),
                )
            else -> -1
        }

    private fun formatStreamBadge(source: AudioStreamSource): String {
        val mime = source.mimeType.lowercase()
        val codec =
            when {
                "opus" in mime || "webm" in mime -> "Opus"
                "mp4a" in mime || "mp4" in mime || "aac" in mime -> "AAC"
                else -> "Audio"
            }
        val kbps = (source.bitrateBps ?: 0) / 1_000
        return if (kbps > 0) "$codec · $kbps kbps" else codec
    }

    companion object {
        private const val DEFAULT_PREFETCH_DELAY_MS: Long = 15_000L
        private const val WORD_SYNC_TICKER_INTERVAL_MS: Long = 80L
        private const val LINE_SYNC_TICKER_INTERVAL_MS: Long = 150L
        private const val NOW_PLAYING_PROGRESS_TICKER_INTERVAL_MS: Long = 250L
        private const val BROWSE_TICKER_INTERVAL_MS: Long = 1_000L
    }
}

private object NoOpCatalogStore : CatalogStore {
    override suspend fun getFavorites(scope: AccountScope): List<Track> = emptyList()

    override suspend fun isFavorite(
        trackId: String,
        scope: AccountScope,
    ): Boolean = false

    override suspend fun toggleFavorite(
        track: Track,
        scope: AccountScope,
    ): Boolean = false

    override suspend fun recordHistory(
        track: Track,
        scope: AccountScope,
    ) = Unit

    override suspend fun getRecentHistory(
        scope: AccountScope,
        limit: Int,
    ): List<Track> = emptyList()

    override suspend fun clearHistory(scope: AccountScope) = Unit

    override suspend fun clearAccountData(scope: AccountScope.Account) = Unit
}

private object NoOpAccountSessionStore : AccountSessionStore {
    override suspend fun loadSession(): Pair<AccountProfile, AccountCredentials>? = null

    override suspend fun saveSession(
        profile: AccountProfile,
        credentials: AccountCredentials,
    ) = Unit

    override suspend fun clearSession() = Unit
}
