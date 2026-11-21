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
    val remoteFavorites: List<Track> = emptyList(),
    val remoteHistory: List<Track> = emptyList(),
    val librarySyncing: Boolean = false,
    val queueChips: List<QueueFilterChip> = emptyList(),
    val activeQueueChipLabel: String? = null,
    val radioLoading: Boolean = false,
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
    private var radioJob: Job? = null

    private var currentTrackAttempt = 0
    private var consecutiveTrackFailures = 0
    private var cachedSource: AudioStreamSource? = null
    private var queueRestored = false
    private var lastRecordedQueueItemId: String? = null
    private var lastAutoplayExtendedTrackId: String? = null

    internal val playerListener =
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
                            recordCurrentItemToHistoryIfNeeded()
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
                    recordCurrentItemToHistoryIfNeeded()
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
                if (category == FailureCategory.EXPIRED_SOURCE || category == FailureCategory.AUTH_INVALID) {
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

            mutableUiState.update {
                it.copy(
                    queueState = normalized,
                    status = if (item != null) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
                    positionMs = normalized.positionMs,
                    durationMs = (item?.track?.durationSeconds ?: 0) * 1000L,
                    errorMessage = null,
                    authSession = resolvedAuth,
                    favorites = favorites,
                    favoriteIds = mergedFavoriteIds,
                    recentHistory = history,
                    accountPlaylists = librarySnapshot.playlists,
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
        mutableUiState.update { it.copy(queueChips = emptyList(), activeQueueChipLabel = null) }
        applyQueueAndStartTrack(nextQueue, startPositionMs = 0L)
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
        var nextQueue = initialQueue
        for (track in tracks) {
            val item = QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
            nextQueue = QueueReducer.reduce(nextQueue, QueueAction.Enqueue(item))
        }
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
        lastAutoplayExtendedTrackId = null
        val empty = mutableUiState.value.queueState.copy(items = emptyList(), currentIndex = -1, positionMs = 0L)
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
                val additional = fetchedTracks.filter { it.id !in existingIds }
                var nextQueue = baseQueue
                for (track in additional) {
                    val item = QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
                    nextQueue = QueueReducer.reduce(nextQueue, QueueAction.Enqueue(item))
                }
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
                }
            }
    }

    fun toggleFavorite(track: Track? = mutableUiState.value.currentTrack) {
        val target = track ?: return
        val activeState = mutableUiState.value
        val activeScope = activeState.authSession.scope
        val isAuthenticated = activeState.authSession is AuthSessionState.Authenticated
        val wasLikedAnywhere = target.id in activeState.favoriteIds
        val inLocalStore = activeState.favorites.any { it.id == target.id }

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
                withContext(ioDispatcher) {
                    runCatching { musicEngine.setRemoteTrackLike(target.id, nowLiked) }
                }
            }
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

        scope.launch {
            withContext(ioDispatcher) {
                runCatching { catalogStore.recordHistory(currentItem.track, activeScope) }
            }
            val refreshed =
                withContext(ioDispatcher) {
                    runCatching { catalogStore.getRecentHistory(activeScope) }.getOrDefault(emptyList())
                }
            mutableUiState.update { it.copy(recentHistory = refreshed) }
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

    internal fun handlePlaybackFailure(
        category: FailureCategory,
        message: String,
    ) {
        val state = mutableUiState.value
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
                        var extended = currentState.queueState
                        for (track in newTracks) {
                            val item = QueueItem(queueItemId = "${track.id}-${UUID.randomUUID()}", track = track)
                            extended = QueueReducer.reduce(extended, QueueAction.Enqueue(item))
                        }
                        extended = QueueReducer.reduce(extended, QueueAction.SetQueue(extended.items, nextStartIndex))
                        mutableUiState.update { it.copy(radioLoading = false) }
                        currentTrackAttempt = 0
                        cachedSource = null
                        applyQueueAndStartTrack(extended, startPositionMs = 0L)
                    } else {
                        mutableUiState.update {
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
    ): Int =
        when (lyrics) {
            is LyricsDocument.Synced -> LyricsSyncResolver.findActiveLineIndex(lyrics.lines, positionMs)
            else -> -1
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
