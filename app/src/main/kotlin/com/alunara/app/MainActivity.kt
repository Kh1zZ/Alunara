package com.alunara.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.ui.components.DockedMiniPlayer
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.screens.CatalogDetailScreen
import com.alunara.app.ui.screens.CatalogTarget
import com.alunara.app.ui.screens.HomeScreen
import com.alunara.app.ui.screens.LibraryScreen
import com.alunara.app.ui.screens.NowPlayingScreen
import com.alunara.app.ui.screens.SearchScreen
import com.alunara.app.ui.screens.SettingsScreen
import com.alunara.app.ui.screens.YouTubeWebViewLoginScreen
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraDisplayPrefs
import com.alunara.app.ui.theme.AlunaraDisplayPrefsStore
import com.alunara.app.ui.theme.AlunaraTheme
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioQualityPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvDestination {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
    NOW_PLAYING,
}

private val RailDestinations =
    listOf(
        TvDestination.HOME,
        TvDestination.LIBRARY,
        TvDestination.SETTINGS,
    )

class MainActivity : ComponentActivity() {
    private val openNowPlayingSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlayingSignal.intValue++
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val container = (application as AlunaraApplication).container
        val prefsStore = AlunaraDisplayPrefsStore(applicationContext)
        setContent {
            var displayPrefs by remember { mutableStateOf(prefsStore.load()) }
            AlunaraTheme(displayPrefs = displayPrefs) {
                AlunaraTvApp(
                    musicEngine = container.musicEngine,
                    sessionManager = container.playbackSessionManager,
                    displayPrefs = displayPrefs,
                    openNowPlayingSignal = openNowPlayingSignal.intValue,
                    onUpdateDisplayPrefs = { updated ->
                        displayPrefs = updated
                        prefsStore.save(updated)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) {
            openNowPlayingSignal.intValue++
        }
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.alunara.app.EXTRA_OPEN_NOW_PLAYING"
    }
}

@Composable
private fun AlunaraTvApp(
    musicEngine: MusicEngine,
    sessionManager: PlaybackSessionManager,
    displayPrefs: AlunaraDisplayPrefs,
    openNowPlayingSignal: Int,
    onUpdateDisplayPrefs: (AlunaraDisplayPrefs) -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val sessionState by sessionManager.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var currentDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var lastBrowseDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var returnDestBeforeSearch by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var catalogStack by remember { mutableStateOf<List<CatalogTarget>>(emptyList()) }
    var showWebViewLogin by rememberSaveable { mutableStateOf(false) }
    val railHomeFocusRequester = remember { FocusRequester() }
    val browsePaneFocusRequester = remember { FocusRequester() }
    val headerSearchRequester = remember { FocusRequester() }
    val miniPlayerPlayPauseRequester = remember { FocusRequester() }
    val saveableStateHolder = rememberSaveableStateHolder()
    var focusRestoreTick by remember { mutableIntStateOf(0) }
    var searchFocusTick by remember { mutableIntStateOf(0) }

    // Home feed state (auto-refreshes when auth scope transitions Guest <-> Authenticated)
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeLoadedOnce by remember { mutableStateOf(false) }
    var homeLoadJob by remember { mutableStateOf<Job?>(null) }
    var lastLoadedAuthScopeKey by remember { mutableStateOf<String?>(null) }
    val authScopeKey = sessionState.authSession.scope.storageKey

    val mergedFavorites =
        remember(sessionState.favorites, sessionState.remoteFavorites) {
            (sessionState.favorites + sessionState.remoteFavorites).distinctBy { it.id }
        }
    val mergedHistory =
        remember(sessionState.recentHistory, sessionState.remoteHistory) {
            (sessionState.recentHistory + sessionState.remoteHistory).distinctBy { it.id }
        }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<SearchResults?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    fun loadHome(force: Boolean = false) {
        if (homeLoading && !force) return
        if (force) {
            homeLoadJob?.cancel()
        }
        homeLoadJob =
            coroutineScope.launch {
                homeLoading = true
                homeError = null
                val res = withContext(Dispatchers.IO) { musicEngine.fetchGuestHome() }
                res.fold(
                    onSuccess = { homeSections = it },
                    onFailure = { homeError = it.message ?: "Network error" },
                )
                homeLoading = false
            }
    }

    fun executeSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        coroutineScope.launch {
            searchLoading = true
            searchError = null
            val res = withContext(Dispatchers.IO) { musicEngine.searchGuest(q) }
            res.fold(
                onSuccess = { searchResults = it },
                onFailure = { searchError = it.message ?: "Search failed" },
            )
            searchLoading = false
        }
    }

    fun openNowPlaying() {
        showWebViewLogin = false
        if (currentDestination != TvDestination.NOW_PLAYING) {
            lastBrowseDestination = currentDestination
            currentDestination = TvDestination.NOW_PLAYING
            sessionManager.ensureLyricsLoaded()
        }
    }

    fun openHeaderSearch(fromDest: TvDestination) {
        returnDestBeforeSearch =
            if (fromDest == TvDestination.LIBRARY) {
                TvDestination.LIBRARY
            } else {
                TvDestination.HOME
            }
        catalogStack = emptyList()
        lastBrowseDestination = TvDestination.SEARCH
        currentDestination = TvDestination.SEARCH
        searchFocusTick++
    }

    fun openAlbumTarget(album: AlbumRef) {
        catalogStack =
            catalogStack +
            CatalogTarget.Album(
                albumId = album.id,
                initialTitle = album.title,
                initialSubtitle = album.artists.joinToString(", ") { it.name },
                initialThumbnailUrl = album.thumbnailUrl,
            )
    }

    fun openPlaylistTarget(playlist: PlaylistSummary) {
        catalogStack =
            catalogStack +
            CatalogTarget.Playlist(
                playlistId = playlist.id,
                initialTitle = playlist.title,
                initialSubtitle = playlist.author ?: playlist.trackCountText,
                initialThumbnailUrl = playlist.thumbnailUrl,
            )
    }

    fun openArtistTarget(artist: ArtistRef) {
        val artistId = artist.id?.takeIf { it.isNotBlank() } ?: return
        catalogStack =
            catalogStack +
            CatalogTarget.Artist(
                artistId = artistId,
                initialName = artist.name,
                initialThumbnailUrl = artist.thumbnailUrl,
            )
    }

    // Hoisted stable callbacks so child screens stay 100% skippable across UI updates
    val onTogglePlayPause = remember(sessionManager) { { sessionManager.togglePlayPause() } }
    val onSkipPrevious = remember(sessionManager) { { sessionManager.skipPrevious() } }
    val onSkipNext = remember(sessionManager) { { sessionManager.skipNext() } }
    val onSeekBy = remember(sessionManager) { { delta: Long -> sessionManager.seekBy(delta) } }
    val onToggleFavoriteCurrent = remember(sessionManager) { { sessionManager.toggleFavorite() } }
    val onToggleFavoriteTrack = remember(sessionManager) { { track: Track -> sessionManager.toggleFavorite(track) } }
    val onStartRadioCurrent =
        remember(sessionManager) {
            {
                sessionManager.uiState.value.currentTrack?.let { sessionManager.startRadio(it) }
                Unit
            }
        }
    val onSwitchLyricsProvider = remember(sessionManager) { { sessionManager.cycleLyricsProvider() } }
    val onAdjustLyricsSync = remember(sessionManager) { { delta: Long -> sessionManager.adjustLyricsSyncOffset(delta) } }
    val onResetLyricsSync = remember(sessionManager) { { sessionManager.resetLyricsSyncOffset() } }
    val onToggleRomanizedLyrics = remember(sessionManager) { { sessionManager.toggleRomanizedLyrics() } }
    val onPlayQueueAt = remember(sessionManager) { { idx: Int -> sessionManager.playTrackAt(idx) } }
    val onMoveQueueItem = remember(sessionManager) { { from: Int, to: Int -> sessionManager.moveQueueItem(from, to) } }
    val onRemoveQueueItem = remember(sessionManager) { { idx: Int -> sessionManager.removeFromQueue(idx) } }
    val onClearQueue = remember(sessionManager) { { sessionManager.clearQueue() } }
    val onRefreshRadio = remember(sessionManager) { { sessionManager.refreshCurrentTrackRadio() } }
    val onSelectQueueChip = remember(sessionManager) { { chip: QueueFilterChip? -> sessionManager.selectQueueChip(chip) } }
    val onPlayTracksAndOpen =
        remember(sessionManager) {
            { tracks: List<Track>, idx: Int ->
                sessionManager.playTracks(tracks, idx)
                openNowPlaying()
            }
        }
    val onStartRadioSeedAndOpen =
        remember(sessionManager) {
            { seed: Track ->
                sessionManager.startRadio(seed)
                openNowPlaying()
            }
        }
    val onEnqueueTrack = remember(sessionManager) { { track: Track -> sessionManager.enqueue(track) } }
    val onEnqueueAllTracks = remember(sessionManager) { { tracks: List<Track> -> sessionManager.enqueueAll(tracks) } }
    val onOpenAlbumCb = remember { { album: AlbumRef -> openAlbumTarget(album) } }
    val onOpenPlaylistCb = remember { { playlist: PlaylistSummary -> openPlaylistTarget(playlist) } }
    val onOpenArtistCb = remember { { artist: ArtistRef -> openArtistTarget(artist) } }
    val onCatalogBack = remember { { catalogStack = catalogStack.dropLast(1) } }
    val onOpenNowPlayingCb = remember { { openNowPlaying() } }
    val onRetryHome = remember { { loadHome(force = true) } }
    val onQueryChangeCb = remember { { q: String -> searchQuery = q } }
    val onSubmitSearchCb = remember { { executeSearch() } }
    val onClearHistoryCb = remember(sessionManager) { { sessionManager.clearHistory() } }
    val onRefreshLibraryCb = remember(sessionManager) { { sessionManager.refreshAccountLibrary() } }
    val onOpenWebViewLoginCb = remember { { showWebViewLogin = true } }
    val onNavigateSearchFromLibrary = remember { { openHeaderSearch(TvDestination.LIBRARY) } }
    val onNavigateSettingsCb =
        remember {
            {
                catalogStack = emptyList()
                lastBrowseDestination = TvDestination.SETTINGS
                currentDestination = TvDestination.SETTINGS
            }
        }
    val onToggleAutoplayCb =
        remember(sessionManager) {
            { sessionManager.setAutoplay(!sessionManager.uiState.value.queueState.autoplayEnabled) }
        }
    val onLogoutCb = remember(sessionManager) { { sessionManager.logoutAccount() } }
    val onSelectAudioQualityCb = remember(sessionManager) { { preset: AudioQualityPreset -> sessionManager.setAudioQuality(preset) } }
    val onSelectRailDest =
        remember {
            { dest: TvDestination ->
                catalogStack = emptyList()
                lastBrowseDestination = dest
                currentDestination = dest
            }
        }

    LaunchedEffect(Unit) {
        sessionManager.restorePersistedQueue()
        if (currentDestination != TvDestination.NOW_PLAYING && !showWebViewLogin) {
            withFrameNanos { }
            railHomeFocusRequester.safeRequestFocus()
        }
        if (!homeLoadedOnce) {
            homeLoadedOnce = true
            lastLoadedAuthScopeKey = authScopeKey
            loadHome()
        }
    }

    LaunchedEffect(authScopeKey) {
        val prevKey = lastLoadedAuthScopeKey
        if (prevKey != null && prevKey != authScopeKey) {
            lastLoadedAuthScopeKey = authScopeKey
            homeLoadedOnce = true
            loadHome(force = true)
        }
    }

    LaunchedEffect(focusRestoreTick) {
        if (focusRestoreTick > 0) {
            withFrameNanos { }
            browsePaneFocusRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(openNowPlayingSignal, sessionState.currentTrack?.id) {
        if (openNowPlayingSignal > 0 && sessionState.currentTrack != null) {
            showWebViewLogin = false
            openNowPlaying()
        }
    }

    BackHandler(enabled = showWebViewLogin || currentDestination != TvDestination.HOME || catalogStack.isNotEmpty()) {
        when {
            showWebViewLogin -> {
                showWebViewLogin = false
                focusRestoreTick++
            }

            currentDestination == TvDestination.NOW_PLAYING -> {
                currentDestination = lastBrowseDestination
                focusRestoreTick++
            }

            catalogStack.isNotEmpty() -> {
                catalogStack = catalogStack.dropLast(1)
                focusRestoreTick++
            }

            currentDestination == TvDestination.SEARCH -> {
                lastBrowseDestination = returnDestBeforeSearch
                currentDestination = returnDestBeforeSearch
                focusRestoreTick++
            }

            else -> {
                currentDestination = TvDestination.HOME
                focusRestoreTick++
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                            if (sessionState.currentTrack != null) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PLAY ->
                            if (sessionState.currentTrack != null && sessionState.status != PlaybackStatus.PLAYING) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PAUSE ->
                            if (sessionState.currentTrack != null && sessionState.status == PlaybackStatus.PLAYING) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_NEXT ->
                            if (sessionState.queueState.currentIndex < sessionState.queueState.items.lastIndex) {
                                sessionManager.skipNext()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                            if (sessionState.queueState.items.isNotEmpty()) {
                                sessionManager.skipPrevious()
                                true
                            } else {
                                false
                            }
                        else -> false
                    }
                },
    ) {
        if (currentDestination == TvDestination.NOW_PLAYING) {
            NowPlayingScreen(
                sessionState = sessionState,
                positionMsFlow = sessionManager.positionMsFlow,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSeekBy = onSeekBy,
                onToggleFavorite = onToggleFavoriteCurrent,
                onStartRadio = onStartRadioCurrent,
                onSwitchLyricsProvider = onSwitchLyricsProvider,
                onAdjustLyricsSync = onAdjustLyricsSync,
                onResetLyricsSync = onResetLyricsSync,
                onToggleRomanizedLyrics = onToggleRomanizedLyrics,
                onPlayQueueAt = onPlayQueueAt,
                onMoveQueueItem = onMoveQueueItem,
                onRemoveQueueItem = onRemoveQueueItem,
                onClearQueue = onClearQueue,
                onRefreshRadio = onRefreshRadio,
                onSelectQueueChip = onSelectQueueChip,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val browseDest = lastBrowseDestination
            val activeCatalogTarget = catalogStack.lastOrNull()
            val showHeaderSearchBar =
                activeCatalogTarget != null ||
                    browseDest == TvDestination.HOME ||
                    browseDest == TvDestination.LIBRARY
            val onHeaderSearchClick = remember { { openHeaderSearch(lastBrowseDestination) } }
            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                // Content reserves fixed rail width so navigation between rail and content causes zero layout shift
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = AlunaraDimens.RailCollapsedWidth),
                ) {
                    if (showHeaderSearchBar) {
                        TvHeaderSearchBar(
                            currentQuery = searchQuery,
                            onClick = onHeaderSearchClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(headerSearchRequester)
                                    .padding(
                                        start = 28.dp,
                                        end = AlunaraDimens.OverscanHorizontal,
                                        top = AlunaraDimens.OverscanVertical,
                                        bottom = 4.dp,
                                    ),
                        )
                    }
                    val paneTarget: Any = activeCatalogTarget ?: browseDest
                    val paneStateKey =
                        when (paneTarget) {
                            is CatalogTarget -> "catalog:${paneTarget}"
                            is TvDestination -> "dest:${paneTarget.name}"
                            else -> "other"
                        }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(colors.background)
                                .focusRequester(browsePaneFocusRequester)
                                .focusGroup()
                                .padding(
                                    start = 28.dp,
                                    end = AlunaraDimens.OverscanHorizontal,
                                    top = if (showHeaderSearchBar) 8.dp else AlunaraDimens.OverscanVertical,
                                    bottom = AlunaraDimens.OverscanVertical,
                                ),
                    ) {
                        saveableStateHolder.SaveableStateProvider(key = paneStateKey) {
                            when (paneTarget) {
                                is CatalogTarget ->
                                    CatalogDetailScreen(
                                        target = paneTarget,
                                        musicEngine = musicEngine,
                                        activeTrackId = sessionState.currentTrack?.id,
                                        onBack = onCatalogBack,
                                        onPlayTracks = onPlayTracksAndOpen,
                                        onEnqueueTrack = onEnqueueTrack,
                                        onEnqueueAll = onEnqueueAllTracks,
                                        onStartRadio = onStartRadioSeedAndOpen,
                                        onOpenAlbum = onOpenAlbumCb,
                                        onOpenPlaylist = onOpenPlaylistCb,
                                    )

                                TvDestination.HOME ->
                                    HomeScreen(
                                        sections = homeSections,
                                        isLoading = homeLoading,
                                        errorMessage = homeError,
                                        resumeTrack = sessionState.currentTrack,
                                        recentHistory = mergedHistory,
                                        favorites = mergedFavorites,
                                        authSession = sessionState.authSession,
                                        onRetry = onRetryHome,
                                        onContinueQueue = onOpenNowPlayingCb,
                                        onPlaySongFromSection = onPlayTracksAndOpen,
                                        onOpenAlbum = onOpenAlbumCb,
                                        onOpenPlaylist = onOpenPlaylistCb,
                                    )

                                TvDestination.SEARCH ->
                                    SearchScreen(
                                        query = searchQuery,
                                        results = searchResults,
                                        isLoading = searchLoading,
                                        errorMessage = searchError,
                                        activeTrackId = sessionState.currentTrack?.id,
                                        onQueryChange = onQueryChangeCb,
                                        onSubmitSearch = onSubmitSearchCb,
                                        onPlaySong = onPlayTracksAndOpen,
                                        onEnqueueSong = onEnqueueTrack,
                                        onOpenAlbum = onOpenAlbumCb,
                                        onOpenArtist = onOpenArtistCb,
                                        onOpenPlaylist = onOpenPlaylistCb,
                                        requestInputFocusTick = searchFocusTick,
                                    )

                                TvDestination.LIBRARY ->
                                    LibraryScreen(
                                        favorites = mergedFavorites,
                                        recentHistory = mergedHistory,
                                        localHistoryCount = sessionState.recentHistory.size,
                                        accountPlaylists = sessionState.accountPlaylists,
                                        authSession = sessionState.authSession,
                                        librarySyncing = sessionState.librarySyncing,
                                        activeTrackId = sessionState.currentTrack?.id,
                                        onPlayTracks = onPlayTracksAndOpen,
                                        onEnqueueTrack = onEnqueueTrack,
                                        onToggleFavorite = onToggleFavoriteTrack,
                                        onClearHistory = onClearHistoryCb,
                                        onRefreshLibrary = onRefreshLibraryCb,
                                        onOpenPlaylist = onOpenPlaylistCb,
                                        onOpenWebViewLogin = onOpenWebViewLoginCb,
                                        onNavigateToSearch = onNavigateSearchFromLibrary,
                                        onNavigateToSettings = onNavigateSettingsCb,
                                    )

                                TvDestination.SETTINGS ->
                                    SettingsScreen(
                                        autoplayEnabled = sessionState.queueState.autoplayEnabled,
                                        authSession = sessionState.authSession,
                                        authLoading = sessionState.authLoading,
                                        authErrorMessage = sessionState.authErrorMessage,
                                        onToggleAutoplay = onToggleAutoplayCb,
                                        onOpenWebViewLogin = onOpenWebViewLoginCb,
                                        onLogout = onLogoutCb,
                                        audioQuality = sessionState.audioQuality,
                                        onSelectAudioQuality = onSelectAudioQualityCb,
                                        displayPrefs = displayPrefs,
                                        onUpdateDisplayPrefs = onUpdateDisplayPrefs,
                                    )

                                TvDestination.NOW_PLAYING -> Unit
                            }
                        }
                    }

                    if (sessionState.currentTrack != null) {
                        DockedMiniPlayer(
                            sessionState = sessionState,
                            positionMsFlow = sessionManager.positionMsFlow,
                            playPauseFocusRequester = miniPlayerPlayPauseRequester,
                            onTogglePlayPause = onTogglePlayPause,
                            onOpenNowPlaying = onOpenNowPlayingCb,
                        )
                    }
                }

                FixedNavigationRail(
                    selected = browseDest,
                    firstItemRequester = railHomeFocusRequester,
                    contentFocusRequester = browsePaneFocusRequester,
                    onSelect = onSelectRailDest,
                )
            }
        }

        if (showWebViewLogin) {
            YouTubeWebViewLoginScreen(
                onCancel = { showWebViewLogin = false },
                onCredentialsCaptured = { credentials ->
                    showWebViewLogin = false
                    sessionManager.loginWithCredentials(credentials)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val RailControlShape = RoundedCornerShape(AlunaraDimens.RadiusControl)

private class RailFocusTracker(
    var alreadyFocused: Boolean = false,
)

@Composable
private fun FixedNavigationRail(
    selected: TvDestination,
    firstItemRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onSelect: (TvDestination) -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val hostView = LocalView.current
    val focusTracker = remember { RailFocusTracker() }

    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .width(AlunaraDimens.RailCollapsedWidth)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .padding(horizontal = 8.dp, vertical = 18.dp)
                    .onFocusChanged { focusState ->
                        val hasFocusNow = focusState.hasFocus
                        hostView.post {
                            focusTracker.alreadyFocused = hasFocusNow
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_alunara_mark),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            RailDestinations.forEachIndexed { index, dest ->
                RailItem(
                    icon =
                        when (dest) {
                            TvDestination.HOME -> Icons.Rounded.Home
                            TvDestination.SEARCH -> Icons.Rounded.Search
                            TvDestination.LIBRARY -> Icons.Rounded.LibraryMusic
                            TvDestination.SETTINGS -> Icons.Rounded.Settings
                            TvDestination.NOW_PLAYING -> Icons.Rounded.Home
                        },
                    label =
                        stringResource(
                            when (dest) {
                                TvDestination.HOME -> R.string.nav_home
                                TvDestination.SEARCH -> R.string.nav_search
                                TvDestination.LIBRARY -> R.string.nav_library
                                TvDestination.SETTINGS -> R.string.nav_settings
                                TvDestination.NOW_PLAYING -> R.string.open_now_playing
                            },
                        ),
                    selected = selected == dest,
                    focusTracker = focusTracker,
                    rightFocusRequester = contentFocusRequester,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier,
                    onClick = { onSelect(dest) },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.secondarySurface),
        )
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    focusTracker: RailFocusTracker,
    rightFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val selectedBorder = remember(colors.primaryAccent) { colors.primaryAccent.copy(alpha = 0.55f) }
    val tintColor =
        when {
            focused -> colors.focusedButtonContent
            selected -> colors.primaryAccent
            else -> colors.mutedText
        }
    val labelColor =
        when {
            focused -> colors.focusedButtonContent
            selected -> colors.primaryText
            else -> colors.mutedText
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AlunaraDimens.RailItemHeight)
                .focusProperties { right = rightFocusRequester }
                .clip(RailControlShape)
                .drawBehind {
                    val cr = CornerRadius(AlunaraDimens.RadiusControl.toPx())
                    val bg =
                        when {
                            focused -> colors.focusedButtonBg
                            selected -> colors.secondarySurface
                            else -> Color.Transparent
                        }
                    if (bg != Color.Transparent) {
                        drawRoundRect(color = bg, cornerRadius = cr)
                    }
                    val strokeColor =
                        when {
                            focused -> colors.focusRingStrong
                            selected -> selectedBorder
                            else -> Color.Transparent
                        }
                    if (strokeColor != Color.Transparent) {
                        val strokePx = colors.focusBorderWidth.toPx()
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = cr,
                            style = Stroke(width = strokePx),
                        )
                    }
                }
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }
                .onFocusChanged { focusState ->
                    focused = focusState.isFocused
                    if (focusState.isFocused && focusTracker.alreadyFocused && !selected) {
                        onClick()
                    }
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = (10.5f * prefs.textScale.coerceAtMost(1.08f)).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvHeaderSearchBar(
    currentQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val bgColor = if (focused) colors.focusedButtonBg else colors.surface
    val borderColor = if (focused) colors.focusRingStrong else colors.secondarySurface
    val iconTint = if (focused) colors.focusedButtonContent else colors.primaryAccent
    val textColor =
        when {
            focused -> colors.focusedButtonContent
            currentQuery.isNotBlank() -> colors.primaryText
            else -> colors.mutedText
        }

    Row(
        modifier =
            modifier
                .height(AlunaraDimens.ControlHeight)
                .background(bgColor, RailControlShape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), RailControlShape)
                .semantics { role = Role.Button }
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.nav_search),
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = currentQuery.takeIf { it.isNotBlank() } ?: stringResource(R.string.header_search_hint),
            color = textColor,
            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
