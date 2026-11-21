package com.alunara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.alunara.app.ui.screens.CatalogDetailScreen
import com.alunara.app.ui.screens.CatalogTarget
import com.alunara.app.ui.screens.HomeScreen
import com.alunara.app.ui.screens.LibraryScreen
import com.alunara.app.ui.screens.NowPlayingScreen
import com.alunara.app.ui.screens.SearchScreen
import com.alunara.app.ui.screens.SettingsScreen
import com.alunara.app.ui.screens.YouTubeWebViewLoginScreen
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.app.ui.theme.AlunaraTheme
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.SearchResults
import kotlinx.coroutines.Dispatchers
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
        TvDestination.SEARCH,
        TvDestination.LIBRARY,
        TvDestination.SETTINGS,
    )

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val container = (application as AlunaraApplication).container
        setContent {
            AlunaraTheme {
                AlunaraTvApp(
                    musicEngine = container.musicEngine,
                    sessionManager = container.playbackSessionManager,
                )
            }
        }
    }
}

@Composable
private fun AlunaraTvApp(
    musicEngine: MusicEngine,
    sessionManager: PlaybackSessionManager,
) {
    val sessionState by sessionManager.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var currentDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var lastBrowseDestination by rememberSaveable { mutableStateOf(TvDestination.HOME) }
    var catalogStack by remember { mutableStateOf<List<CatalogTarget>>(emptyList()) }
    var showWebViewLogin by rememberSaveable { mutableStateOf(false) }
    var railFocused by remember { mutableStateOf(false) }
    val railHomeFocusRequester = remember { FocusRequester() }

    // Home feed state (loaded lazily once)
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeLoadedOnce by remember { mutableStateOf(false) }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<SearchResults?>(null) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    fun loadHome() {
        if (homeLoading) return
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
        if (currentDestination != TvDestination.NOW_PLAYING) {
            lastBrowseDestination = currentDestination
            currentDestination = TvDestination.NOW_PLAYING
            sessionManager.ensureLyricsLoaded()
        }
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

    LaunchedEffect(Unit) {
        sessionManager.restorePersistedQueue()
        railHomeFocusRequester.requestFocus()
        if (!homeLoadedOnce) {
            homeLoadedOnce = true
            loadHome()
        }
    }

    BackHandler(enabled = showWebViewLogin || currentDestination != TvDestination.HOME || catalogStack.isNotEmpty()) {
        when {
            showWebViewLogin -> {
                showWebViewLogin = false
            }

            currentDestination == TvDestination.NOW_PLAYING -> {
                currentDestination = lastBrowseDestination
            }

            catalogStack.isNotEmpty() -> {
                catalogStack = catalogStack.dropLast(1)
            }

            else -> {
                currentDestination = TvDestination.HOME
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AlunaraPalette.Background)) {
        Crossfade(
            targetState = currentDestination == TvDestination.NOW_PLAYING,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            modifier = Modifier.fillMaxSize(),
            label = "root_destination",
        ) { isNowPlaying ->
        if (isNowPlaying) {
            NowPlayingScreen(
                sessionState = sessionState,
                onTogglePlayPause = { sessionManager.togglePlayPause() },
                onSkipPrevious = { sessionManager.skipPrevious() },
                onSkipNext = { sessionManager.skipNext() },
                onSeekBy = { delta -> sessionManager.seekBy(delta) },
                onToggleFavorite = { sessionManager.toggleFavorite() },
                onStartRadio = { sessionState.currentTrack?.let { sessionManager.startRadio(it) } },
                onSwitchLyricsProvider = { sessionManager.cycleLyricsProvider() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val browseDest = if (currentDestination == TvDestination.NOW_PLAYING) lastBrowseDestination else currentDestination
            val activeCatalogTarget = catalogStack.lastOrNull()
            Box(modifier = Modifier.fillMaxSize()) {
                // Content reserves constant collapsed rail margin so expanding the overlay rail never shifts layout
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = AlunaraDimens.RailCollapsedWidth),
                ) {
                    Crossfade(
                        targetState = activeCatalogTarget ?: browseDest,
                        animationSpec = tween(durationMillis = 160),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 20.dp),
                        label = "browse_tab",
                    ) { paneTarget ->
                        when (paneTarget) {
                            is CatalogTarget ->
                                CatalogDetailScreen(
                                    target = paneTarget,
                                    musicEngine = musicEngine,
                                    activeTrackId = sessionState.currentTrack?.id,
                                    onBack = { catalogStack = catalogStack.dropLast(1) },
                                    onPlayTracks = { tracks, idx ->
                                        sessionManager.playTracks(tracks, idx)
                                        openNowPlaying()
                                    },
                                    onEnqueueTrack = { track -> sessionManager.enqueue(track) },
                                    onEnqueueAll = { tracks -> sessionManager.enqueueAll(tracks) },
                                    onStartRadio = { seed ->
                                        sessionManager.startRadio(seed)
                                        openNowPlaying()
                                    },
                                    onOpenAlbum = { album -> openAlbumTarget(album) },
                                    onOpenPlaylist = { playlist -> openPlaylistTarget(playlist) },
                                )

                            TvDestination.HOME ->
                                HomeScreen(
                                    sections = homeSections,
                                    isLoading = homeLoading,
                                    errorMessage = homeError,
                                    resumeTrack = sessionState.currentTrack,
                                    recentHistory = sessionState.recentHistory,
                                    favorites = sessionState.favorites,
                                    onRetry = { loadHome() },
                                    onContinueQueue = { openNowPlaying() },
                                    onPlaySongFromSection = { songs, idx ->
                                        sessionManager.playTracks(songs, idx)
                                        openNowPlaying()
                                    },
                                    onOpenAlbum = { album -> openAlbumTarget(album) },
                                )

                            TvDestination.SEARCH ->
                                SearchScreen(
                                    query = searchQuery,
                                    results = searchResults,
                                    isLoading = searchLoading,
                                    errorMessage = searchError,
                                    activeTrackId = sessionState.currentTrack?.id,
                                    onQueryChange = { searchQuery = it },
                                    onSubmitSearch = { executeSearch() },
                                    onPlaySong = { songs, idx ->
                                        sessionManager.playTracks(songs, idx)
                                        openNowPlaying()
                                    },
                                    onEnqueueSong = { track -> sessionManager.enqueue(track) },
                                    onOpenAlbum = { album -> openAlbumTarget(album) },
                                    onOpenArtist = { artist -> openArtistTarget(artist) },
                                    onOpenPlaylist = { playlist -> openPlaylistTarget(playlist) },
                                )

                            TvDestination.LIBRARY ->
                                LibraryScreen(
                                    queueState = sessionState.queueState,
                                    favorites = sessionState.favorites,
                                    recentHistory = sessionState.recentHistory,
                                    accountPlaylists = sessionState.accountPlaylists,
                                    authSession = sessionState.authSession,
                                    activeTrackId = sessionState.currentTrack?.id,
                                    onPlayAt = { idx ->
                                        sessionManager.playTrackAt(idx)
                                        openNowPlaying()
                                    },
                                    onMoveItem = { from, to -> sessionManager.moveQueueItem(from, to) },
                                    onRemoveAt = { idx -> sessionManager.removeFromQueue(idx) },
                                    onClearQueue = { sessionManager.clearQueue() },
                                    onPlayTracks = { tracks, idx ->
                                        sessionManager.playTracks(tracks, idx)
                                        openNowPlaying()
                                    },
                                    onEnqueueTrack = { track -> sessionManager.enqueue(track) },
                                    onToggleFavorite = { track -> sessionManager.toggleFavorite(track) },
                                    onClearHistory = { sessionManager.clearHistory() },
                                    onOpenPlaylist = { playlist -> openPlaylistTarget(playlist) },
                                    onOpenWebViewLogin = { showWebViewLogin = true },
                                    onNavigateToSearch = {
                                        catalogStack = emptyList()
                                        lastBrowseDestination = TvDestination.SEARCH
                                        currentDestination = TvDestination.SEARCH
                                    },
                                    onNavigateToSettings = {
                                        catalogStack = emptyList()
                                        lastBrowseDestination = TvDestination.SETTINGS
                                        currentDestination = TvDestination.SETTINGS
                                    },
                                )

                            TvDestination.SETTINGS ->
                                SettingsScreen(
                                    autoplayEnabled = sessionState.queueState.autoplayEnabled,
                                    authSession = sessionState.authSession,
                                    authLoading = sessionState.authLoading,
                                    authErrorMessage = sessionState.authErrorMessage,
                                    onToggleAutoplay = {
                                        sessionManager.setAutoplay(!sessionState.queueState.autoplayEnabled)
                                    },
                                    onOpenWebViewLogin = { showWebViewLogin = true },
                                    onLogout = { sessionManager.logoutAccount() },
                                )

                            TvDestination.NOW_PLAYING -> Unit
                        }
                    }

                    if (sessionState.currentTrack != null) {
                        DockedMiniPlayer(
                            sessionState = sessionState,
                            onTogglePlayPause = { sessionManager.togglePlayPause() },
                            onOpenNowPlaying = { openNowPlaying() },
                        )
                    }
                }

                CollapsibleNavigationRail(
                    selected = browseDest,
                    expanded = railFocused,
                    firstItemRequester = railHomeFocusRequester,
                    onRailFocusChanged = { railFocused = it },
                    onSelect = { dest ->
                        catalogStack = emptyList()
                        lastBrowseDestination = dest
                        currentDestination = dest
                    },
                )
            }
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

@Composable
private fun CollapsibleNavigationRail(
    selected: TvDestination,
    expanded: Boolean,
    firstItemRequester: FocusRequester,
    onRailFocusChanged: (Boolean) -> Unit,
    onSelect: (TvDestination) -> Unit,
) {
    val railWidth by animateDpAsState(
        targetValue = if (expanded) AlunaraDimens.RailExpandedWidth else AlunaraDimens.RailCollapsedWidth,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "rail_width",
    )

    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .background(AlunaraPalette.Surface)
                    .padding(horizontal = 12.dp, vertical = 18.dp)
                    .onFocusChanged { onRailFocusChanged(it.hasFocus) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AlunaraDimens.RailItemHeight)
                        .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_alunara_mark),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(26.dp),
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(160)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            color = AlunaraPalette.PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
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
                    expanded = expanded,
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
                    .background(AlunaraPalette.SecondarySurface),
        )
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val bgColor by animateColorAsState(
        targetValue = if (selected) AlunaraPalette.SecondarySurface else Color.Transparent,
        animationSpec = tween(150),
        label = "rail_item_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) AlunaraPalette.FocusOutline else Color.Transparent,
        animationSpec = tween(150),
        label = "rail_item_border",
    )
    val tintColor by animateColorAsState(
        targetValue = if (selected || focused) AlunaraPalette.PrimaryAccent else AlunaraPalette.MutedText,
        animationSpec = tween(150),
        label = "rail_item_tint",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AlunaraDimens.RailItemHeight)
                .background(bgColor, shape)
                .border(BorderStroke(AlunaraDimens.FocusBorderWidth, borderColor), shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
