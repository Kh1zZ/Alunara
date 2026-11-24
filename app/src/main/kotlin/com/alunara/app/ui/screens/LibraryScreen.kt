package com.alunara.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvCardRowSkeleton
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.Track

private enum class LibraryTab {
    PLAYLISTS,
    FAVORITES,
    HISTORY,
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    favorites: List<Track>,
    recentHistory: List<Track>,
    localHistoryCount: Int,
    accountPlaylists: List<PlaylistSummary>,
    authSession: AuthSessionState,
    librarySyncing: Boolean,
    activeTrackId: String?,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onOpenWebViewLogin: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester = remember { FocusRequester() },
    onCreatePlaylistClick: () -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.PLAYLISTS) }
    var confirmingClearHistory by remember { mutableStateOf(false) }
    val cancelClearHistoryRequester = remember { FocusRequester() }
    val topRightActionRequester = remember { FocusRequester() }
    val createPlaylistRequester = remember { FocusRequester() }
    val tabPlaylistsRequester = remember { FocusRequester() }
    val tabFavoritesRequester = remember { FocusRequester() }
    val tabHistoryRequester = remember { FocusRequester() }
    val contentAreaRequester = remember { FocusRequester() }
    val firstContentItemRequester = remember { FocusRequester() }
    var restoreHistoryAction by remember { mutableStateOf(false) }

    val hasContentDownTarget =
        when (selectedTab) {
            LibraryTab.PLAYLISTS -> authSession !is AuthSessionState.Authenticated || accountPlaylists.isNotEmpty()
            LibraryTab.FAVORITES -> favorites.isNotEmpty()
            LibraryTab.HISTORY -> recentHistory.isNotEmpty()
        }
    val hasTopRightAction =
        (authSession is AuthSessionState.Authenticated && selectedTab != LibraryTab.HISTORY) ||
            (selectedTab == LibraryTab.HISTORY && localHistoryCount > 0 && !confirmingClearHistory)

    LaunchedEffect(confirmingClearHistory) {
        if (confirmingClearHistory) {
            restoreHistoryAction = true
            withFrameNanos { }
            cancelClearHistoryRequester.safeRequestFocus()
        } else if (restoreHistoryAction) {
            restoreHistoryAction = false
            withFrameNanos { }
            if (localHistoryCount <= 0 || !topRightActionRequester.safeRequestFocus()) {
                tabHistoryRequester.safeRequestFocus()
            }
        }
    }
    BackHandler(enabled = confirmingClearHistory) {
        confirmingClearHistory = false
    }
    LaunchedEffect(localHistoryCount) {
        if (localHistoryCount <= 0) {
            confirmingClearHistory = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.library_headline),
                color = colors.primaryText,
                fontSize = AlunaraTypography.headline(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.focusProperties { canFocus = false },
            )
            when {
                authSession is AuthSessionState.Authenticated && selectedTab != LibraryTab.HISTORY -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedTab == LibraryTab.PLAYLISTS) {
                            TvFocusableButton(
                                text = stringResource(R.string.playlist_create_short),
                                icon = Icons.Rounded.Add,
                                highlighted = true,
                                onClick = onCreatePlaylistClick,
                                modifier =
                                    Modifier
                                        .focusRequester(createPlaylistRequester)
                                        .focusProperties {
                                            left = tabHistoryRequester
                                            right = topRightActionRequester
                                            down = tabPlaylistsRequester
                                        },
                            )
                        }
                        TvFocusableButton(
                            text = stringResource(R.string.action_sync_library),
                            icon = Icons.Rounded.Refresh,
                            onClick = onRefreshLibrary,
                            modifier =
                                Modifier
                                    .focusRequester(topRightActionRequester)
                                    .focusProperties {
                                        left = if (selectedTab == LibraryTab.PLAYLISTS) createPlaylistRequester else tabHistoryRequester
                                        down = tabPlaylistsRequester
                                    },
                        )
                    }
                }

                selectedTab == LibraryTab.HISTORY && localHistoryCount > 0 && !confirmingClearHistory -> {
                    TvFocusableButton(
                        text = stringResource(R.string.clear_history),
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = { confirmingClearHistory = true },
                        modifier =
                            Modifier
                                .focusRequester(topRightActionRequester)
                                .focusProperties {
                                    left = tabHistoryRequester
                                    down = tabHistoryRequester
                                },
                    )
                }
            }
        }

        // Exact 3-tab order: 1. Playlist > 2. Favorit > 3. Riwayat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val handleTabDownKey: (KeyEvent) -> Boolean = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && hasContentDownTarget) {
                    contentAreaRequester.safeRequestFocus() || firstContentItemRequester.safeRequestFocus()
                } else {
                    false
                }
            }
            TvFocusableButton(
                text = stringResource(R.string.library_tab_playlists, accountPlaylists.size),
                icon = Icons.Rounded.LibraryMusic,
                highlighted = selectedTab == LibraryTab.PLAYLISTS,
                onClick = {
                    confirmingClearHistory = false
                    selectedTab = LibraryTab.PLAYLISTS
                },
                modifier =
                    Modifier
                        .focusRequester(tabPlaylistsRequester)
                        .then(if (selectedTab == LibraryTab.PLAYLISTS) Modifier.focusRequester(entryFocusRequester) else Modifier)
                        .onPreviewKeyEvent(handleTabDownKey)
                        .focusProperties {
                            right = tabFavoritesRequester
                            if (hasTopRightAction) {
                                up =
                                    if (authSession is AuthSessionState.Authenticated && selectedTab == LibraryTab.PLAYLISTS) {
                                        createPlaylistRequester
                                    } else {
                                        topRightActionRequester
                                    }
                            }
                        },
            )
            TvFocusableButton(
                text = stringResource(R.string.library_tab_favorites, favorites.size),
                icon = Icons.Rounded.Favorite,
                highlighted = selectedTab == LibraryTab.FAVORITES,
                onClick = {
                    confirmingClearHistory = false
                    selectedTab = LibraryTab.FAVORITES
                },
                modifier =
                    Modifier
                        .focusRequester(tabFavoritesRequester)
                        .then(if (selectedTab == LibraryTab.FAVORITES) Modifier.focusRequester(entryFocusRequester) else Modifier)
                        .onPreviewKeyEvent(handleTabDownKey)
                        .focusProperties {
                            left = tabPlaylistsRequester
                            right = tabHistoryRequester
                            if (hasTopRightAction) up = topRightActionRequester
                        },
            )
            TvFocusableButton(
                text = stringResource(R.string.library_tab_history, recentHistory.size),
                icon = Icons.Rounded.History,
                highlighted = selectedTab == LibraryTab.HISTORY,
                onClick = { selectedTab = LibraryTab.HISTORY },
                modifier =
                    Modifier
                        .focusRequester(tabHistoryRequester)
                        .then(if (selectedTab == LibraryTab.HISTORY) Modifier.focusRequester(entryFocusRequester) else Modifier)
                        .onPreviewKeyEvent(handleTabDownKey)
                        .focusProperties {
                            left = tabFavoritesRequester
                            if (hasTopRightAction) {
                                right =
                                    if (authSession is AuthSessionState.Authenticated && selectedTab == LibraryTab.PLAYLISTS) {
                                        createPlaylistRequester
                                    } else {
                                        topRightActionRequester
                                    }
                                up = topRightActionRequester
                            }
                        },
            )
        }

        if (confirmingClearHistory && selectedTab == LibraryTab.HISTORY) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.errorSurface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.clear_history_confirm_prompt),
                    color = colors.errorText,
                    fontSize = AlunaraTypography.body(prefs.textScale),
                    modifier = Modifier.weight(1f),
                )
                TvFocusableButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmingClearHistory = false },
                    modifier = Modifier.focusRequester(cancelClearHistoryRequester),
                )
                TvFocusableButton(
                    text = stringResource(R.string.confirm_delete_action),
                    highlighted = true,
                    onClick = {
                        confirmingClearHistory = false
                        onClearHistory()
                    },
                )
            }
        }

        if (authSession !is AuthSessionState.Authenticated) {
            Text(
                text = stringResource(R.string.library_guest_note),
                color = colors.mutedText,
                fontSize = AlunaraTypography.label(prefs.textScale),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(12.dp)
                        .focusProperties { canFocus = false },
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(contentAreaRequester)
                    .focusRestorer()
                    .focusGroup(),
        ) {
            when (selectedTab) {
                LibraryTab.PLAYLISTS ->
                    if (authSession !is AuthSessionState.Authenticated) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                                    .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.account_playlists_guest_title),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.account_playlists_guest_desc),
                                color = colors.mutedText,
                                fontSize = AlunaraTypography.body(prefs.textScale),
                                lineHeight = AlunaraTypography.body(prefs.textScale) * 1.45f,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvFocusableButton(
                                    text = stringResource(R.string.login_webview_button),
                                    icon = Icons.AutoMirrored.Rounded.Login,
                                    highlighted = true,
                                    onClick = onOpenWebViewLogin,
                                    modifier = Modifier.focusRequester(firstContentItemRequester),
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.open_search),
                                    icon = Icons.Rounded.Search,
                                    onClick = onNavigateToSearch,
                                )
                                TvFocusableButton(
                                    text = stringResource(R.string.open_account_status),
                                    icon = Icons.Rounded.Settings,
                                    onClick = onNavigateToSettings,
                                )
                            }
                        }
                    } else if (librarySyncing && accountPlaylists.isEmpty()) {
                        TvCardRowSkeleton(count = 5)
                    } else if (accountPlaylists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.account_playlists_empty),
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = AlunaraDimens.CardWidth),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 16.dp),
                        ) {
                            itemsIndexed(
                                items = accountPlaylists,
                                key = { _, pl -> "acc-pl-${pl.id}" },
                                contentType = { _, _ -> "library-playlist-card" },
                            ) { idx, playlist ->
                                TvMediaCard(
                                    title = playlist.title,
                                    subtitle = playlist.author ?: playlist.trackCountText ?: stringResource(R.string.badge_youtube_synced),
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    onClick = { onOpenPlaylist(playlist) },
                                    modifier = if (idx == 0) Modifier.focusRequester(firstContentItemRequester) else Modifier,
                                )
                            }
                        }
                    }

                LibraryTab.FAVORITES ->
                    if (favorites.isEmpty()) {
                        Text(
                            text = stringResource(R.string.favorites_empty),
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            items(
                                count = favorites.size,
                                key = { idx -> "fav-${favorites[idx].id}" },
                                contentType = { "library-song-row" },
                            ) { index ->
                                val track = favorites[index]
                                TvSongRow(
                                    track = track,
                                    isActive = track.id == activeTrackId,
                                    onPlayClick = { onPlayTracks(favorites, index) },
                                    secondaryActionLabel = stringResource(R.string.remove_favorite),
                                    secondaryActionIcon = Icons.Rounded.DeleteOutline,
                                    onSecondaryClick = { onToggleFavorite(track) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstContentItemRequester) else Modifier,
                                )
                            }
                        }
                    }

                LibraryTab.HISTORY ->
                    if (recentHistory.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            items(
                                count = recentHistory.size,
                                key = { idx -> "hist-${recentHistory[idx].id}" },
                                contentType = { "library-song-row" },
                            ) { index ->
                                val track = recentHistory[index]
                                TvSongRow(
                                    track = track,
                                    isActive = track.id == activeTrackId,
                                    onPlayClick = { onPlayTracks(recentHistory, index) },
                                    secondaryActionLabel = stringResource(R.string.add_to_queue),
                                    secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    onSecondaryClick = { onEnqueueTrack(track) },
                                    modifier = if (index == 0) Modifier.focusRequester(firstContentItemRequester) else Modifier,
                                )
                            }
                        }
                    }
            }
        }
    }
}
