package com.alunara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.Track

private enum class LibraryTab {
    PLAYLISTS,
    FAVORITES,
    HISTORY,
}

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
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.PLAYLISTS) }

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
                color = AlunaraPalette.PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                authSession is AuthSessionState.Authenticated && selectedTab != LibraryTab.HISTORY -> {
                    TvFocusableButton(
                        text = stringResource(R.string.action_sync_library),
                        icon = Icons.Rounded.Refresh,
                        onClick = onRefreshLibrary,
                    )
                }

                selectedTab == LibraryTab.HISTORY && localHistoryCount > 0 -> {
                    TvFocusableButton(
                        text = stringResource(R.string.clear_history),
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = onClearHistory,
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
            TvFocusableButton(
                text = stringResource(R.string.library_tab_playlists, accountPlaylists.size),
                icon = Icons.Rounded.LibraryMusic,
                highlighted = selectedTab == LibraryTab.PLAYLISTS,
                onClick = { selectedTab = LibraryTab.PLAYLISTS },
            )
            TvFocusableButton(
                text = stringResource(R.string.library_tab_favorites, favorites.size),
                icon = Icons.Rounded.Favorite,
                highlighted = selectedTab == LibraryTab.FAVORITES,
                onClick = { selectedTab = LibraryTab.FAVORITES },
            )
            TvFocusableButton(
                text = stringResource(R.string.library_tab_history, recentHistory.size),
                icon = Icons.Rounded.History,
                highlighted = selectedTab == LibraryTab.HISTORY,
                onClick = { selectedTab = LibraryTab.HISTORY },
            )
        }

        if (authSession !is AuthSessionState.Authenticated) {
            Text(
                text = stringResource(R.string.library_guest_note),
                color = AlunaraPalette.MutedText,
                fontSize = 13.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(12.dp),
            )
        }

        when (selectedTab) {
            LibraryTab.PLAYLISTS ->
                if (authSession !is AuthSessionState.Authenticated) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(AlunaraPalette.Surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusCard))
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.account_playlists_guest_title),
                            color = AlunaraPalette.PrimaryAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.account_playlists_guest_desc),
                            color = AlunaraPalette.MutedText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvFocusableButton(
                                text = stringResource(R.string.login_webview_button),
                                icon = Icons.AutoMirrored.Rounded.Login,
                                highlighted = true,
                                onClick = onOpenWebViewLogin,
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
                    Text(
                        text = stringResource(R.string.catalog_loading),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (accountPlaylists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.account_playlists_empty),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 188.dp),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(accountPlaylists, key = { "acc-pl-${it.id}" }) { playlist ->
                            TvMediaCard(
                                title = playlist.title,
                                subtitle = playlist.author ?: playlist.trackCountText ?: stringResource(R.string.badge_youtube_synced),
                                thumbnailUrl = playlist.thumbnailUrl,
                                onClick = { onOpenPlaylist(playlist) },
                            )
                        }
                    }
                }

            LibraryTab.FAVORITES ->
                if (favorites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(favorites.size, key = { idx -> "fav-${favorites[idx].id}" }) { index ->
                            val track = favorites[index]
                            TvSongRow(
                                track = track,
                                isActive = track.id == activeTrackId,
                                onPlayClick = { onPlayTracks(favorites, index) },
                                secondaryActionLabel = stringResource(R.string.remove_favorite),
                                secondaryActionIcon = Icons.Rounded.DeleteOutline,
                                onSecondaryClick = { onToggleFavorite(track) },
                            )
                        }
                    }
                }

            LibraryTab.HISTORY ->
                if (recentHistory.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(recentHistory.size, key = { idx -> "hist-${recentHistory[idx].id}" }) { index ->
                            val track = recentHistory[index]
                            TvSongRow(
                                track = track,
                                isActive = track.id == activeTrackId,
                                onPlayClick = { onPlayTracks(recentHistory, index) },
                                secondaryActionLabel = stringResource(R.string.add_to_queue),
                                secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                onSecondaryClick = { onEnqueueTrack(track) },
                            )
                        }
                    }
                }
        }
    }
}
