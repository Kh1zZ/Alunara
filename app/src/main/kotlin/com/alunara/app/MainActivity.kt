package com.alunara.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.alunara.app.playback.PlaybackSessionManager
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.playback.SyncNotice
import com.alunara.app.playback.SyncNoticeKind
import com.alunara.app.remote.AlunaraRemoteServer
import com.alunara.app.remote.RemoteServerStatus
import com.alunara.app.ui.components.AddToPlaylistDialog
import com.alunara.app.ui.components.AppUpdateDialog
import com.alunara.app.ui.components.DockedMiniPlayer
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.i18n.AlunaraLanguageStore
import com.alunara.app.ui.i18n.AppLanguage
import com.alunara.app.ui.i18n.AppLocaleHelper
import com.alunara.app.ui.i18n.LocalAppLanguage
import com.alunara.app.update.AppUpdateManager
import com.alunara.app.update.AppUpdateUiState
import com.alunara.app.ui.screens.CatalogDetailScreen
import com.alunara.app.ui.screens.CatalogDetailUiState
import com.alunara.app.ui.screens.CatalogTarget
import com.alunara.app.ui.screens.CompanionInputPayload
import com.alunara.app.ui.screens.HomeScreen
import com.alunara.app.ui.screens.LatestSearch
import com.alunara.app.ui.screens.LibraryScreen
import com.alunara.app.ui.screens.LocalLoginCompanionServer
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
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.GenreCategory
import com.alunara.core.domain.model.GenreSection
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.AudioQualityPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
        TvDestination.SEARCH,
        TvDestination.LIBRARY,
        TvDestination.SETTINGS,
    )

class MainActivity : ComponentActivity() {
    private var pendingNowPlaying by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val savedLanguage = AlunaraLanguageStore(newBase).load()
        super.attachBaseContext(AppLocaleHelper.wrapContext(newBase, savedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNowPlaying = savedInstanceState?.getBoolean(STATE_PENDING_NOW_PLAYING) == true
        acceptPlaybackIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val container = (application as AlunaraApplication).container
        val prefsStore = AlunaraDisplayPrefsStore(applicationContext)
        val languageStore = AlunaraLanguageStore(applicationContext)
        setContent {
            var displayPrefs by remember { mutableStateOf(prefsStore.load()) }
            var appLanguage by remember { mutableStateOf(languageStore.load()) }
            val baseConfig = LocalConfiguration.current
            val (localizedConfig, localizedContext) =
                remember(appLanguage, baseConfig) {
                    AppLocaleHelper.createLocalizedContext(
                        activity = this@MainActivity,
                        baseConfig = baseConfig,
                        language = appLanguage,
                    )
                }
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalAppLanguage provides appLanguage,
            ) {
                AlunaraTheme(displayPrefs = displayPrefs) {
                    AlunaraTvApp(
                        musicEngine = container.musicEngine,
                        sessionManager = container.playbackSessionManager,
                        appUpdateManager = container.appUpdateManager,
                        remoteServer = container.remoteServer,
                        displayPrefs = displayPrefs,
                        appLanguage = appLanguage,
                        pendingNowPlaying = pendingNowPlaying,
                        onNowPlayingRequestHandled = { pendingNowPlaying = false },
                        onUpdateDisplayPrefs = { updated ->
                            displayPrefs = updated
                            prefsStore.save(updated)
                        },
                        onSelectAppLanguage = { updated ->
                            if (updated != appLanguage) {
                                languageStore.save(updated)
                                Locale.setDefault(
                                    AppLocaleHelper.toLocale(
                                        language = updated,
                                        preserveRegionFrom = Locale.getDefault(),
                                    ),
                                )
                                appLanguage = updated
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptPlaybackIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PENDING_NOW_PLAYING, pendingNowPlaying)
    }

    private fun acceptPlaybackIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            pendingNowPlaying = true
            intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
        }
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.alunara.app.EXTRA_OPEN_NOW_PLAYING"
        private const val STATE_PENDING_NOW_PLAYING = "pendingNowPlaying"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AlunaraTvApp(
    musicEngine: MusicEngine,
    sessionManager: PlaybackSessionManager,
    appUpdateManager: AppUpdateManager,
    remoteServer: AlunaraRemoteServer,
    displayPrefs: AlunaraDisplayPrefs,
    appLanguage: AppLanguage,
    pendingNowPlaying: Boolean,
    onNowPlayingRequestHandled: () -> Unit,
    onUpdateDisplayPrefs: (AlunaraDisplayPrefs) -> Unit,
    onSelectAppLanguage: (AppLanguage) -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val sessionState by sessionManager.uiState.collectAsStateWithLifecycle()
    val remoteStatus by remoteServer.status.collectAsStateWithLifecycle()
    val updateUiState by appUpdateManager.uiState.collectAsStateWithLifecycle()
    val updateDialogVisible by appUpdateManager.dialogVisible.collectAsStateWithLifecycle()
    var updateLaunchError by remember { mutableStateOf<Int?>(null) }
    val autoUpdateEnabled by appUpdateManager.autoUpdateEnabled.collectAsStateWithLifecycle()
    val hostView = LocalView.current
    val hostActivityContext = hostView.context
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var currentDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var returnDestBeforeNowPlaying by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var catalogStack by remember { mutableStateOf<List<CatalogTarget>>(emptyList()) }
    var showWebViewLogin by rememberSaveable { mutableStateOf(false) }
    var awaitingLoginVerification by remember { mutableStateOf(false) }
    var playlistDialogVisible by remember { mutableStateOf(false) }
    var playlistDialogTrack by remember { mutableStateOf<Track?>(null) }
    var activeContentEntryRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var activeCompanionServer by remember { mutableStateOf<LocalLoginCompanionServer?>(null) }
    var companionInputHandler by remember { mutableStateOf<(suspend (CompanionInputPayload) -> Unit)?>(null) }
    var companionGraceJob by remember { mutableStateOf<Job?>(null) }
    val railRequesters = remember { RailDestinations.associateWith { FocusRequester() } }
    val railHomeFocusRequester = railRequesters[TvDestination.HOME] ?: remember { FocusRequester() }
    val contentBoxRequester = remember { FocusRequester() }
    val miniPlayerPlayPauseRequester = remember { FocusRequester() }
    var isRailFocused by remember { mutableStateOf(false) }
    var isContentFocused by remember { mutableStateOf(false) }
    var hoveredRailDest by remember { mutableStateOf<TvDestination?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()
    var focusRestoreTick by remember { mutableIntStateOf(0) }
    var pendingRailEnterTick by remember { mutableIntStateOf(0) }
    var searchFocusRequested by remember { mutableStateOf(false) }

    fun scheduleCompanionGraceShutdown() {
        companionGraceJob?.cancel()
        companionGraceJob =
            coroutineScope.launch {
                delay(25_000L)
                activeCompanionServer?.stop()
                activeCompanionServer = null
            }
    }

    val openSignInWithCompanion: () -> Unit = {
        companionGraceJob?.cancel()
        companionGraceJob = null
        activeCompanionServer?.stop()
        awaitingLoginVerification = false
        val server =
            LocalLoginCompanionServer(
                scope = coroutineScope,
                language = appLanguage,
                onInput = { payload -> companionInputHandler?.invoke(payload) },
            )
        val currentAuth = sessionState.authSession
        if (currentAuth is AuthSessionState.Authenticated) {
            server.markCompleted(currentAuth.profile.name)
        }
        server.start()
        activeCompanionServer = server
        showWebViewLogin = true
    }

    LaunchedEffect(sessionState.authSession, sessionState.authLoading, sessionState.authErrorMessage, awaitingLoginVerification) {
        val authed = sessionState.authSession as? AuthSessionState.Authenticated
        if (authed != null) {
            activeCompanionServer?.markCompleted(authed.profile.name)
            if (awaitingLoginVerification) {
                awaitingLoginVerification = false
                showWebViewLogin = false
                focusRestoreTick++
                scheduleCompanionGraceShutdown()
            }
        } else if (awaitingLoginVerification && !sessionState.authLoading && !sessionState.authErrorMessage.isNullOrBlank()) {
            activeCompanionServer?.markVerificationFailed(sessionState.authErrorMessage.orEmpty())
            awaitingLoginVerification = false
            showWebViewLogin = false
            focusRestoreTick++
            scheduleCompanionGraceShutdown()
        }
    }

    LaunchedEffect(sessionState.syncNotice?.id) {
        if (sessionState.syncNotice != null) {
            delay(2800L)
            sessionManager.clearSyncNotice()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            companionGraceJob?.cancel()
            activeCompanionServer?.stop()
            activeCompanionServer = null
        }
    }

    LaunchedEffect(appLanguage) {
        remoteServer.language = appLanguage
    }

    DisposableEffect(remoteServer, hostView) {
        remoteServer.onDispatchDpadKey = { keyCode ->
            hostView.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            hostView.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        remoteServer.onNavigateDestination = { destName ->
            val target = TvDestination.entries.firstOrNull { it.name == destName }
            if (target == TvDestination.NOW_PLAYING) {
                if (sessionState.currentTrack != null) {
                    showWebViewLogin = false
                    if (currentDestination != TvDestination.NOW_PLAYING) {
                        returnDestBeforeNowPlaying = currentDestination
                        currentDestination = TvDestination.NOW_PLAYING
                    }
                }
            } else if (target != null) {
                showWebViewLogin = false
                catalogStack = emptyList()
                currentDestination = target
                focusRestoreTick++
            }
        }
        onDispose {
            remoteServer.onDispatchDpadKey = null
            remoteServer.onNavigateDestination = null
        }
    }

    // Home feed state (auto-refreshes when auth scope transitions Guest <-> Authenticated)
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeLoadedOnce by remember { mutableStateOf(false) }
    var homeLoadJob by remember { mutableStateOf<Job?>(null) }
    var lastLoadedAuthScopeKey by remember { mutableStateOf<String?>(null) }
    var lastAppliedLanguage by remember { mutableStateOf<AppLanguage?>(null) }
    val authScopeKey = sessionState.authSession.scope.storageKey
    val catalogDetails = remember(authScopeKey, appLanguage) { linkedMapOf<CatalogTarget, CatalogDetailUiState>() }

    val mergedFavorites =
        remember(sessionState.favorites, sessionState.remoteFavorites) {
            (sessionState.favorites + sessionState.remoteFavorites).distinctBy { it.id }
        }
    val mergedHistory =
        remember(sessionState.recentHistory, sessionState.remoteHistory) {
            (sessionState.recentHistory + sessionState.remoteHistory).distinctBy { it.id }
        }

    // Search & Genre discovery state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val latestSearch =
        remember(coroutineScope, musicEngine) {
            LatestSearch(coroutineScope) { query ->
                withContext(Dispatchers.IO) { musicEngine.searchGuest(query) }
            }
        }
    val searchState = latestSearch.state
    var genreSections by remember { mutableStateOf<List<GenreSection>>(emptyList()) }
    var genresLoading by remember { mutableStateOf(false) }
    var genresError by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<GenreCategory?>(null) }
    var genreResults by remember { mutableStateOf<SearchResults?>(null) }
    var genreContentLoading by remember { mutableStateOf(false) }
    var genreContentError by remember { mutableStateOf<String?>(null) }
    var genreLoadJob by remember { mutableStateOf<Job?>(null) }
    var genreContentJob by remember { mutableStateOf<Job?>(null) }

    fun loadHome(
        force: Boolean = false,
        languageTag: String = appLanguage.innerTubeHl,
    ) {
        if (homeLoading && !force) return
        if (force) {
            homeLoadJob?.cancel()
        }
        homeLoadJob =
            coroutineScope.launch {
                homeLoading = true
                homeError = null
                val res =
                    withContext(Dispatchers.IO) {
                        musicEngine.setContentLanguage(languageTag)
                        musicEngine.fetchGuestHome()
                    }
                res.fold(
                    onSuccess = { homeSections = it },
                    onFailure = { homeError = it.message ?: "Network error" },
                )
                homeLoading = false
            }
    }

    fun loadGenres(
        force: Boolean = false,
        languageTag: String = appLanguage.innerTubeHl,
    ) {
        if (genresLoading && !force) return
        if (force) {
            genreLoadJob?.cancel()
        }
        genreLoadJob =
            coroutineScope.launch {
                genresLoading = true
                genresError = null
                val res =
                    withContext(Dispatchers.IO) {
                        musicEngine.setContentLanguage(languageTag)
                        musicEngine.fetchMoodAndGenres()
                    }
                res.fold(
                    onSuccess = { genreSections = it },
                    onFailure = { genresError = it.message ?: "Network error" },
                )
                genresLoading = false
            }
    }

    fun selectGenre(genre: GenreCategory) {
        genreContentJob?.cancel()
        selectedGenre = genre
        genreResults = null
        genreContentError = null
        genreContentJob =
            coroutineScope.launch {
                genreContentLoading = true
                val res =
                    withContext(Dispatchers.IO) {
                        musicEngine.setContentLanguage(appLanguage.innerTubeHl)
                        musicEngine.fetchGenreContent(genre)
                    }
                res.fold(
                    onSuccess = { genreResults = it },
                    onFailure = { genreContentError = it.message ?: "Network error" },
                )
                genreContentLoading = false
            }
    }

    fun focusActivePaneDirectly() {
        if (!contentBoxRequester.safeRequestFocus()) {
            if (activeContentEntryRequester?.safeRequestFocus() != true) {
                focusManager.moveFocus(FocusDirection.Right)
            }
        }
    }

    fun openNowPlaying() {
        showWebViewLogin = false
        if (currentDestination != TvDestination.NOW_PLAYING) {
            returnDestBeforeNowPlaying = currentDestination
            currentDestination = TvDestination.NOW_PLAYING
        }
    }

    fun openSearchTab(requestInput: Boolean = true) {
        if (currentDestination != TvDestination.SEARCH || catalogStack.isNotEmpty()) {
            catalogStack = emptyList()
            currentDestination = TvDestination.SEARCH
        }
        searchFocusRequested = requestInput
    }

    fun popCatalog() {
        catalogStack = catalogStack.dropLast(1)
        focusRestoreTick++
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
        focusRestoreTick++
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
        focusRestoreTick++
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
        focusRestoreTick++
    }

    // Hoisted stable callbacks so child screens stay 100% skippable across UI updates
    val onTogglePlayPause = remember(sessionManager) { { sessionManager.togglePlayPause() } }
    val onSkipPrevious = remember(sessionManager) { { sessionManager.skipPrevious() } }
    val onSkipNext = remember(sessionManager) { { sessionManager.skipNext() } }
    val onSeekBy = remember(sessionManager) { { delta: Long -> sessionManager.seekBy(delta) } }
    val onSeekTo = remember(sessionManager) { sessionManager::seekTo }
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
    val onCatalogBack = remember { { popCatalog() } }
    val onOpenNowPlayingCb = remember { { openNowPlaying() } }
    val onRetryHome = remember { { loadHome(force = true) } }
    val onQueryChangeCb =
        remember {
            { q: String ->
                searchQuery = q
                if (q.isNotBlank()) selectedGenre = null
            }
        }
    val onSubmitSearchCb =
        remember(latestSearch) {
            {
                if (searchQuery.isNotBlank()) selectedGenre = null
                latestSearch.submit(searchQuery)
            }
        }
    val onRetrySearchCb = remember(latestSearch) { { latestSearch.submit(latestSearch.state.query) } }
    val onClearHistoryCb = remember(sessionManager) { { sessionManager.clearHistory() } }
    val onRefreshLibraryCb = remember(sessionManager) { { sessionManager.refreshAccountLibrary() } }
    val onOpenWebViewLoginCb: () -> Unit = { openSignInWithCompanion() }
    val onOpenAddToPlaylistCb: (Track?) -> Unit =
        remember {
            { targetTrack ->
                playlistDialogTrack = targetTrack
                playlistDialogVisible = true
            }
        }
    val onOpenAddToPlaylistCurrentCb =
        remember(sessionManager) {
            {
                sessionManager.uiState.value.currentTrack?.let { trk ->
                    playlistDialogTrack = trk
                    playlistDialogVisible = true
                }
                Unit
            }
        }
    val onToggleSavePlaylistCb = remember(sessionManager) { { pl: PlaylistSummary -> sessionManager.toggleSavePlaylist(pl) } }
    val onToggleArtistSubscriptionCb =
        remember(sessionManager) {
            { artistId: String, channelId: String?, artistName: String? ->
                sessionManager.toggleArtistSubscription(artistId, channelId, artistName)
            }
        }
    val onRemoveTrackFromPlaylistCb =
        remember(sessionManager) {
            { playlistId: String, track: Track, onSuccess: () -> Unit ->
                sessionManager.removeTrackFromAccountPlaylist(playlistId, track) { ok ->
                    if (ok) onSuccess()
                }
            }
        }
    val onNavigateSearchFromLibrary = remember { { openSearchTab(requestInput = true) } }
    val onNavigateSettingsCb =
        remember {
            {
                catalogStack = emptyList()
                currentDestination = TvDestination.SETTINGS
            }
        }
    val onToggleAutoplayCb =
        remember(sessionManager) {
            { sessionManager.setAutoplay(!sessionManager.uiState.value.queueState.autoplayEnabled) }
        }
    val onLogoutCb = remember(sessionManager) { { sessionManager.logoutAccount() } }
    val onSelectAudioQualityCb = remember(sessionManager) { { preset: AudioQualityPreset -> sessionManager.setAudioQuality(preset) } }
    val onToggleAutoUpdateCb = remember(appUpdateManager) { { appUpdateManager.setAutoUpdateEnabled(!appUpdateManager.autoUpdateEnabled.value) } }
    val onCheckForUpdatesCb = remember(appUpdateManager) { { appUpdateManager.checkForUpdatesManual() } }
    val onOpenUpdateDialogCb = remember(appUpdateManager) { { appUpdateManager.openUpdateDialog() } }
    var wasUpdateDialogOpen by remember { mutableStateOf(false) }
    val onSelectRailDest =
        remember {
            { dest: TvDestination ->
                catalogStack = emptyList()
                if (dest != TvDestination.SEARCH) searchFocusRequested = false
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
        appUpdateManager.checkOnStartup()
    }

    DisposableEffect(lifecycleOwner, appUpdateManager) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    appUpdateManager.onPermissionSettingsReturned()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(updateUiState) {
        val readyState = updateUiState as? AppUpdateUiState.ReadyToInstall ?: return@LaunchedEffect
        updateLaunchError = null
        runCatching {
            hostActivityContext.startActivity(appUpdateManager.buildInstallIntent(readyState.apkFile))
        }.onFailure {
            updateLaunchError = R.string.update_dialog_installer_launch_failed
        }
    }

    LaunchedEffect(updateDialogVisible) {
        if (updateDialogVisible) {
            wasUpdateDialogOpen = true
        } else if (wasUpdateDialogOpen) {
            wasUpdateDialogOpen = false
            updateLaunchError = null
            if (currentDestination != TvDestination.NOW_PLAYING && !showWebViewLogin) {
                focusRestoreTick++
            }
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

    LaunchedEffect(appLanguage) {
        val prevLang = lastAppliedLanguage
        lastAppliedLanguage = appLanguage
        if (prevLang != null && prevLang != appLanguage) {
            delay(350L)
        }
        withContext(Dispatchers.IO) {
            musicEngine.setContentLanguage(appLanguage.innerTubeHl)
        }
        if (prevLang != null && prevLang != appLanguage) {
            if (homeLoadedOnce) {
                loadHome(force = true, languageTag = appLanguage.innerTubeHl)
            }
            selectedGenre = null
            genreResults = null
            genreContentError = null
            if (genreSections.isNotEmpty() || currentDestination == TvDestination.SEARCH) {
                loadGenres(force = true, languageTag = appLanguage.innerTubeHl)
            }
        }
    }

    LaunchedEffect(focusRestoreTick) {
        if (focusRestoreTick > 0) {
            withFrameNanos { }
            withFrameNanos { }
            if (!contentBoxRequester.safeRequestFocus()) {
                if (activeContentEntryRequester?.safeRequestFocus() != true) {
                    (railRequesters[currentDestination] ?: railHomeFocusRequester).safeRequestFocus()
                }
            }
        }
    }

    LaunchedEffect(pendingRailEnterTick) {
        if (pendingRailEnterTick > 0) {
            withFrameNanos { }
            withFrameNanos { }
            focusActivePaneDirectly()
        }
    }

    LaunchedEffect(pendingNowPlaying, sessionState.currentTrack?.id) {
        if (pendingNowPlaying && sessionState.currentTrack != null) {
            openNowPlaying()
            onNowPlayingRequestHandled()
        }
    }

    LaunchedEffect(currentDestination) {
        sessionManager.setNowPlayingVisible(currentDestination == TvDestination.NOW_PLAYING)
        if (currentDestination == TvDestination.SEARCH && genreSections.isEmpty() && !genresLoading) {
            loadGenres()
        }
    }

    BackHandler(enabled = showWebViewLogin || currentDestination != TvDestination.HOME || catalogStack.isNotEmpty() || !isRailFocused) {
        when {
            showWebViewLogin -> {
                awaitingLoginVerification = false
                showWebViewLogin = false
                if (activeCompanionServer?.isCompletedOnTv != true) {
                    activeCompanionServer?.stop()
                    activeCompanionServer = null
                }
                focusRestoreTick++
            }

            currentDestination == TvDestination.NOW_PLAYING -> {
                currentDestination = returnDestBeforeNowPlaying
                focusRestoreTick++
            }

            catalogStack.isNotEmpty() -> {
                popCatalog()
            }

            currentDestination == TvDestination.SEARCH && searchQuery.isBlank() && selectedGenre != null -> {
                selectedGenre = null
                genreResults = null
                genreContentError = null
            }

            !isRailFocused -> {
                (railRequesters[currentDestination] ?: railHomeFocusRequester).safeRequestFocus()
            }

            currentDestination != TvDestination.HOME -> {
                currentDestination = TvDestination.HOME
                searchFocusRequested = false
                railHomeFocusRequester.safeRequestFocus()
            }
        }
    }

    val onRootPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean =
        remember(sessionManager) {
            { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val liveState = sessionManager.uiState.value
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                            if (liveState.currentTrack != null) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PLAY ->
                            if (liveState.currentTrack != null && liveState.status != PlaybackStatus.PLAYING) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PAUSE ->
                            if (liveState.currentTrack != null && liveState.status == PlaybackStatus.PLAYING) {
                                sessionManager.togglePlayPause()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_NEXT ->
                            if (liveState.queueState.currentIndex < liveState.queueState.items.lastIndex) {
                                sessionManager.skipNext()
                                true
                            } else {
                                false
                            }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                            if (liveState.queueState.items.isNotEmpty()) {
                                sessionManager.skipPrevious()
                                true
                            } else {
                                false
                            }
                        else -> false
                    }
                }
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .onPreviewKeyEvent(onRootPreviewKeyEvent),
    ) {
        if (currentDestination == TvDestination.NOW_PLAYING) {
            NowPlayingScreen(
                sessionState = sessionState,
                onLyricsPanelVisibilityChanged = remember(sessionManager) { sessionManager::setLyricsPanelVisible },
                positionMsFlow = sessionManager.positionMsFlow,
                activeLyricIndexFlow = sessionManager.activeLyricIndexFlow,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSeekBy = onSeekBy,
                onSeekTo = onSeekTo,
                onToggleFavorite = onToggleFavoriteCurrent,
                onAddToPlaylist = onOpenAddToPlaylistCurrentCb,
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = !updateDialogVisible && !showWebViewLogin && !playlistDialogVisible },
            )
        } else {
            val browseDest = currentDestination
            val activeCatalogTarget = catalogStack.lastOrNull()
            val contentZoneBorderColor =
                if (isContentFocused) {
                    colors.primaryAccent.copy(alpha = 0.8f)
                } else {
                    colors.secondarySurface.copy(alpha = 0.85f)
                }
            val contentZoneBorderWidth = if (isContentFocused) 1.5.dp else 1.dp
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(10.dp)
                        .focusProperties { canFocus = !updateDialogVisible && !showWebViewLogin && !playlistDialogVisible },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FixedNavigationRail(
                    selected = browseDest,
                    railRequesters = railRequesters,
                    isZoneFocused = isRailFocused,
                    onFocusZoneChanged = { hasFocus ->
                        isRailFocused = hasFocus
                        if (!hasFocus) hoveredRailDest = null
                    },
                    onHoveredDestChanged = { dest -> hoveredRailDest = dest },
                    onConfirmSelect = { dest ->
                        if (currentDestination == dest && catalogStack.isEmpty()) {
                            focusActivePaneDirectly()
                        } else {
                            onSelectRailDest(dest)
                            pendingRailEnterTick++
                        }
                    },
                    onEnterActiveContent = {
                        focusActivePaneDirectly()
                    },
                )

                // Right column: Box 2 (Main Content Box) + Box 3 (Bottom Mini-Player Box)
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val paneTarget: Any = activeCatalogTarget ?: browseDest
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(BrowseZoneShape)
                                .background(colors.background, BrowseZoneShape)
                                .border(BorderStroke(contentZoneBorderWidth, contentZoneBorderColor), BrowseZoneShape)
                                .onFocusChanged { isContentFocused = it.hasFocus }
                                .padding(
                                    start = 24.dp,
                                    end = 28.dp,
                                    top = 18.dp,
                                    bottom = 16.dp,
                                ),
                    ) {
                        AnimatedContent(
                            targetState = paneTarget,
                            contentKey = { target ->
                                when (target) {
                                    is CatalogTarget -> "catalog:${target}"
                                    is TvDestination -> "dest:${target.name}"
                                    else -> "other"
                                }
                            },
                            transitionSpec = {
                                if (prefs.reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(tween(durationMillis = 120)) togetherWith
                                        fadeOut(tween(durationMillis = 90))
                                }
                            },
                            label = "browse_pane_transition",
                            modifier = Modifier.fillMaxSize(),
                        ) { activePane ->
                            val activePaneKey =
                                    when (activePane) {
                                        is CatalogTarget -> "catalog:${activePane}"
                                        is TvDestination -> "dest:${activePane.name}"
                                        else -> "other"
                                    }
                            val paneEntryRequester = remember(activePaneKey) { FocusRequester() }
                            if (activePane == paneTarget) {
                                SideEffect { activeContentEntryRequester = paneEntryRequester }
                            }
                                saveableStateHolder.SaveableStateProvider(key = activePaneKey) {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .then(
                                                if (activePane != paneTarget || updateDialogVisible || showWebViewLogin || playlistDialogVisible) {
                                                    Modifier.focusProperties {
                                                        canFocus = false
                                                        onEnter = { cancelFocusChange() }
                                                    }.onPreviewKeyEvent { true }
                                                } else {
                                                    Modifier
                                                        .focusRequester(contentBoxRequester)
                                                        .focusRestorer(fallback = paneEntryRequester)
                                                        .focusGroup()
                                                },
                                            ),
                                    ) {
                                    when (activePane) {
                                        is CatalogTarget ->
                                            CatalogDetailScreen(
                                                target = activePane,
                                                musicEngine = musicEngine,
                                                detailsCache = catalogDetails,
                                                activeTrackId = sessionState.currentTrack?.id,
                                                onBack = onCatalogBack,
                                                onPlayTracks = onPlayTracksAndOpen,
                                                onEnqueueTrack = onEnqueueTrack,
                                                onEnqueueAll = onEnqueueAllTracks,
                                                onStartRadio = onStartRadioSeedAndOpen,
                                                onOpenAlbum = onOpenAlbumCb,
                                                onOpenPlaylist = onOpenPlaylistCb,
                                                entryFocusRequester = paneEntryRequester,
                                                authSession = sessionState.authSession,
                                                savedPlaylistIds = sessionState.savedPlaylistIds,
                                                subscribedArtistIds = sessionState.subscribedArtistIds,
                                                onToggleSavePlaylist = onToggleSavePlaylistCb,
                                                onToggleArtistSubscription = onToggleArtistSubscriptionCb,
                                                onAddTrackToPlaylist = { trk -> onOpenAddToPlaylistCb(trk) },
                                                onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylistCb,
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
                                                entryFocusRequester = paneEntryRequester,
                                            )

                                        TvDestination.SEARCH ->
                                            SearchScreen(
                                                query = searchQuery,
                                                results = searchState.results,
                                                isLoading = searchState.loading,
                                                errorMessage = searchState.error,
                                                activeTrackId = sessionState.currentTrack?.id,
                                                onQueryChange = onQueryChangeCb,
                                                onSubmitSearch = onSubmitSearchCb,
                                                onPlaySong = onPlayTracksAndOpen,
                                                onEnqueueSong = onEnqueueTrack,
                                                onOpenAlbum = onOpenAlbumCb,
                                                onOpenArtist = onOpenArtistCb,
                                                onOpenPlaylist = onOpenPlaylistCb,
                                                entryFocusRequester = paneEntryRequester,
                                                requestInputFocus = searchFocusRequested && activePane == paneTarget &&
                                                    !updateDialogVisible && !showWebViewLogin && !playlistDialogVisible,
                                                onInputFocusHandled = { searchFocusRequested = false },
                                                onRetrySearch = onRetrySearchCb,
                                                onAddToPlaylist = { trk -> onOpenAddToPlaylistCb(trk) },
                                                genreSections = genreSections,
                                                genresLoading = genresLoading,
                                                genresError = genresError,
                                                selectedGenre = selectedGenre,
                                                genreResults = genreResults,
                                                genreContentLoading = genreContentLoading,
                                                genreContentError = genreContentError,
                                                onSelectGenre = { genre -> selectGenre(genre) },
                                                onClearSelectedGenre = {
                                                    selectedGenre = null
                                                    genreResults = null
                                                    genreContentError = null
                                                },
                                                onRetryGenres = { loadGenres(force = true) },
                                                onRetryGenreContent = {
                                                    selectedGenre?.let { selectGenre(it) }
                                                },
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
                                                entryFocusRequester = paneEntryRequester,
                                                onCreatePlaylistClick = { onOpenAddToPlaylistCb(null) },
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
                                                entryFocusRequester = paneEntryRequester,
                                                audioQuality = sessionState.audioQuality,
                                                onSelectAudioQuality = onSelectAudioQualityCb,
                                                appLanguage = appLanguage,
                                                onSelectAppLanguage = onSelectAppLanguage,
                                                displayPrefs = displayPrefs,
                                                onUpdateDisplayPrefs = onUpdateDisplayPrefs,
                                                currentVersionName = appUpdateManager.currentVersionName,
                                                autoUpdateEnabled = autoUpdateEnabled,
                                                updateUiState = updateUiState,
                                                onToggleAutoUpdate = onToggleAutoUpdateCb,
                                                onCheckForUpdates = onCheckForUpdatesCb,
                                                onOpenUpdateDialog = onOpenUpdateDialogCb,
                                                remoteStatus = remoteStatus,
                                                onToggleRemoteEnabled = { remoteServer.setEnabled(!remoteStatus.enabled) },
                                                onRegenerateRemoteCode = { remoteServer.regenerateCode() },
                                            )

                                        TvDestination.NOW_PLAYING -> Unit
                                    }
                                }
                            }
                        }

                        val pendingNavDest = hoveredRailDest
                        if (isRailFocused && pendingNavDest != null && pendingNavDest != browseDest) {
                            val targetLabel =
                                stringResource(
                                    when (pendingNavDest) {
                                        TvDestination.HOME -> R.string.nav_home
                                        TvDestination.SEARCH -> R.string.nav_search
                                        TvDestination.LIBRARY -> R.string.nav_library
                                        TvDestination.SETTINGS -> R.string.nav_settings
                                        TvDestination.NOW_PLAYING -> R.string.open_now_playing
                                    },
                                )
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .background(colors.secondarySurface, RoundedCornerShape(999.dp))
                                        .border(
                                            BorderStroke(1.dp, colors.primaryAccent.copy(alpha = 0.75f)),
                                            RoundedCornerShape(999.dp),
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .focusProperties { canFocus = false },
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_confirm_ok_hint, targetLabel),
                                    color = colors.focusOutline,
                                    fontSize = AlunaraTypography.label(prefs.textScale),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    val hasMiniPlayerTrack = sessionState.currentTrack != null
                    var retainedMiniPlayerState by remember { mutableStateOf<PlaybackSessionUiState?>(null) }
                    if (hasMiniPlayerTrack) {
                        retainedMiniPlayerState = sessionState
                    }
                    LaunchedEffect(hasMiniPlayerTrack) {
                        if (!hasMiniPlayerTrack) {
                            delay(220L)
                            retainedMiniPlayerState = null
                        }
                    }
                    AnimatedVisibility(
                        visible = hasMiniPlayerTrack,
                        enter =
                            if (prefs.reduceMotion) {
                                EnterTransition.None
                            } else {
                                fadeIn(tween(durationMillis = 180)) +
                                    slideInVertically(tween(durationMillis = 200)) { it / 2 }
                            },
                        exit =
                            if (prefs.reduceMotion) {
                                ExitTransition.None
                            } else {
                                fadeOut(tween(durationMillis = 140)) +
                                    slideOutVertically(tween(durationMillis = 160)) { it / 2 }
                            },
                    ) {
                        val visibleMiniPlayerState = retainedMiniPlayerState
                        if (visibleMiniPlayerState?.currentTrack != null) {
                            DockedMiniPlayer(
                                sessionState = visibleMiniPlayerState,
                                positionMsFlow = sessionManager.positionMsFlow,
                                playPauseFocusRequester = miniPlayerPlayPauseRequester,
                                onMoveUpToContent = {
                                    contentBoxRequester.safeRequestFocus() ||
                                        activeContentEntryRequester?.safeRequestFocus() == true
                                },
                                onMoveLeftToRail = {
                                    (railRequesters[browseDest] ?: railHomeFocusRequester).safeRequestFocus()
                                },
                                onTogglePlayPause = onTogglePlayPause,
                                onOpenNowPlaying = onOpenNowPlayingCb,
                            )
                        }
                    }
                }
            }
        }

        if (showWebViewLogin) {
            YouTubeWebViewLoginScreen(
                companionServer = activeCompanionServer,
                onRegisterCompanionInputHandler = { handler -> companionInputHandler = handler },
                onCancel = {
                    awaitingLoginVerification = false
                    showWebViewLogin = false
                    if (activeCompanionServer?.isCompletedOnTv != true) {
                        activeCompanionServer?.stop()
                        activeCompanionServer = null
                    }
                    focusRestoreTick++
                },
                onCredentialsCaptured = { credentials ->
                    awaitingLoginVerification = true
                    activeCompanionServer?.markVerifying()
                    sessionManager.loginWithCredentials(credentials)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AddToPlaylistDialog(
            visible = playlistDialogVisible,
            targetTrack = playlistDialogTrack,
            authSession = sessionState.authSession,
            playlists = sessionState.accountPlaylists,
            onAddTrackToPlaylist = { playlist, trk ->
                sessionManager.addTrackToAccountPlaylist(playlist, trk)
            },
            onCreatePlaylist = { title, trk ->
                sessionManager.createAccountPlaylist(title, trk)
            },
            onOpenSignIn = onOpenWebViewLoginCb,
            onDismiss = {
                playlistDialogVisible = false
                if (currentDestination != TvDestination.NOW_PLAYING) {
                    focusRestoreTick++
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val activeNotice = sessionState.syncNotice
        if (activeNotice != null) {
            SyncNoticePill(
                notice = activeNotice,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = AlunaraDimens.OverscanHorizontal),
            )
        }

        AppUpdateDialog(
            visible = updateDialogVisible,
            state = updateUiState,
            launchError = updateLaunchError?.let { stringResource(it) },
            currentVersionName = appUpdateManager.currentVersionName,
            onStartDownload = { info -> appUpdateManager.startDownload(info) },
            onCancelDownload = { appUpdateManager.cancelDownload() },
            onRetryCheck = { appUpdateManager.checkForUpdatesManual() },
            onRequestInstallPermission = {
                updateLaunchError = null
                runCatching {
                    hostActivityContext.startActivity(appUpdateManager.buildUnknownSourcesIntent())
                }.onFailure {
                    updateLaunchError = R.string.update_dialog_permission_launch_failed
                }
            },
            onRetriggerInstall = { appUpdateManager.retriggerInstall() },
            onDismiss = { appUpdateManager.dismissDialog() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val BrowseZoneShape = RoundedCornerShape(16.dp)
private val RailControlShape = RoundedCornerShape(AlunaraDimens.RadiusControl)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FixedNavigationRail(
    selected: TvDestination,
    railRequesters: Map<TvDestination, FocusRequester>,
    isZoneFocused: Boolean,
    onFocusZoneChanged: (Boolean) -> Unit,
    onHoveredDestChanged: (TvDestination?) -> Unit,
    onConfirmSelect: (TvDestination) -> Unit,
    onEnterActiveContent: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val activeRailRequester = railRequesters[selected] ?: railRequesters[TvDestination.HOME] ?: FocusRequester.Default
    val zoneBorderColor =
        if (isZoneFocused) {
            colors.primaryAccent.copy(alpha = 0.8f)
        } else {
            colors.secondarySurface.copy(alpha = 0.85f)
        }
    val zoneBorderWidth = if (isZoneFocused) 1.5.dp else 1.dp

    Column(
        modifier =
            Modifier
                .width(AlunaraDimens.RailCollapsedWidth)
                .fillMaxHeight()
                .clip(BrowseZoneShape)
                .background(colors.surface, BrowseZoneShape)
                .border(BorderStroke(zoneBorderWidth, zoneBorderColor), BrowseZoneShape)
                .onFocusChanged { focusState ->
                    onFocusZoneChanged(focusState.hasFocus)
                }
                .focusRestorer(fallback = activeRailRequester)
                .focusGroup()
                .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .focusProperties { canFocus = false },
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

        RailDestinations.forEach { dest ->
            val itemRequester = railRequesters[dest] ?: remember { FocusRequester() }
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
                modifier = Modifier.focusRequester(itemRequester),
                onFocusChangedItem = { isFocused ->
                    if (isFocused) {
                        onHoveredDestChanged(dest)
                    }
                },
                onClick = { onConfirmSelect(dest) },
                onEnterActiveContent = onEnterActiveContent,
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChangedItem: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEnterActiveContent: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val focusScale by
        animateFloatAsState(
            targetValue = if (focused && !prefs.reduceMotion) 1.03f else 1f,
            animationSpec =
                if (prefs.reduceMotion) {
                    snap()
                } else {
                    tween(durationMillis = 120, easing = FastOutSlowInEasing)
                },
            label = "rail_focus_scale",
        )
    val selectedBorder = remember(colors.primaryAccent) { colors.primaryAccent.copy(alpha = 0.55f) }
    val tintColor =
        when {
            focused -> colors.focusedButtonContent
            selected -> colors.primaryAccent
            else -> colors.mutedText
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AlunaraDimens.RailItemHeight)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onEnterActiveContent()
                        true
                    } else {
                        false
                    }
                }
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
                    if (selected) {
                        val barW = 3.dp.toPx()
                        val barH = size.height * 0.48f
                        val barTop = (size.height - barH) / 2f
                        drawRoundRect(
                            color = if (focused) colors.focusedButtonContent else colors.primaryAccent,
                            topLeft = Offset(3.dp.toPx(), barTop),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f),
                        )
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
                    onFocusChangedItem(focusState.isFocused)
                }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SyncNoticePill(
    notice: SyncNotice,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val detail = notice.detail.orEmpty()
    val isError = notice.kind == SyncNoticeKind.FAVORITE_SYNC_FAILED || notice.kind == SyncNoticeKind.SYNC_FAILED
    val text =
        when (notice.kind) {
            SyncNoticeKind.FAVORITE_SYNC_FAILED -> stringResource(R.string.sync_notice_favorite_failed, detail)
            SyncNoticeKind.PLAYLIST_CREATED -> stringResource(R.string.sync_notice_playlist_created, detail)
            SyncNoticeKind.PLAYLIST_ADDED -> stringResource(R.string.sync_notice_playlist_added, detail)
            SyncNoticeKind.PLAYLIST_REMOVED -> stringResource(R.string.sync_notice_playlist_removed, detail)
            SyncNoticeKind.PLAYLIST_SAVED -> stringResource(R.string.sync_notice_playlist_saved, detail)
            SyncNoticeKind.PLAYLIST_UNSAVED -> stringResource(R.string.sync_notice_playlist_unsaved, detail)
            SyncNoticeKind.ARTIST_SUBSCRIBED -> stringResource(R.string.sync_notice_artist_subscribed, detail)
            SyncNoticeKind.ARTIST_UNSUBSCRIBED -> stringResource(R.string.sync_notice_artist_unsubscribed, detail)
            SyncNoticeKind.SYNC_FAILED -> stringResource(R.string.sync_notice_failed, detail)
        }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            modifier
                .background(if (isError) colors.errorSurface else colors.surface, shape)
                .border(
                    BorderStroke(1.5.dp, if (isError) colors.errorText else colors.primaryAccent),
                    shape,
                )
                .padding(horizontal = 16.dp, vertical = 9.dp)
                .focusProperties { canFocus = false },
    ) {
        Text(
            text = text,
            color = if (isError) colors.errorText else colors.primaryText,
            fontSize = AlunaraTypography.label(1f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
