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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
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
import com.alunara.app.ui.components.TvIconButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track

private enum class LibraryTab {
    QUEUE,
    FAVORITES,
    HISTORY,
    ACCOUNT_PLAYLISTS,
}

@Composable
fun LibraryScreen(
    queueState: QueueState,
    favorites: List<Track>,
    recentHistory: List<Track>,
    accountPlaylists: List<PlaylistSummary>,
    authSession: AuthSessionState,
    activeTrackId: String?,
    onPlayAt: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onClearHistory: () -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.QUEUE) }

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
                selectedTab == LibraryTab.QUEUE && queueState.items.isNotEmpty() -> {
                    TvFocusableButton(
                        text = stringResource(R.string.clear_queue),
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = onClearQueue,
                    )
                }

                selectedTab == LibraryTab.HISTORY && recentHistory.isNotEmpty() -> {
                    TvFocusableButton(
                        text = stringResource(R.string.clear_history),
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = onClearHistory,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvFocusableButton(
                text = stringResource(R.string.library_tab_queue, queueState.items.size),
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                highlighted = selectedTab == LibraryTab.QUEUE,
                onClick = { selectedTab = LibraryTab.QUEUE },
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
            TvFocusableButton(
                text = stringResource(R.string.library_tab_account_playlists),
                icon = Icons.Rounded.LibraryMusic,
                highlighted = selectedTab == LibraryTab.ACCOUNT_PLAYLISTS,
                onClick = { selectedTab = LibraryTab.ACCOUNT_PLAYLISTS },
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
            LibraryTab.QUEUE ->
                if (queueState.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.queue_empty),
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
                        items(queueState.items.size, key = { idx -> queueState.items[idx].queueItemId }) { index ->
                            val item = queueState.items[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TvSongRow(
                                    track = item.track,
                                    isActive = index == queueState.currentIndex,
                                    onPlayClick = { onPlayAt(index) },
                                    modifier = Modifier.weight(1f),
                                )
                                TvIconButton(
                                    icon = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.move_up),
                                    enabled = index > 0,
                                    onClick = { onMoveItem(index, index - 1) },
                                )
                                TvIconButton(
                                    icon = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.move_down),
                                    enabled = index < queueState.items.lastIndex,
                                    onClick = { onMoveItem(index, index + 1) },
                                )
                                TvIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.remove_item),
                                    onClick = { onRemoveAt(index) },
                                )
                            }
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

            LibraryTab.ACCOUNT_PLAYLISTS ->
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
                                text = stringResource(R.string.open_search),
                                icon = Icons.Rounded.Search,
                                highlighted = true,
                                onClick = onNavigateToSearch,
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.open_account_status),
                                icon = Icons.Rounded.Settings,
                                onClick = onNavigateToSettings,
                            )
                        }
                    }
                } else if (accountPlaylists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.account_playlists_empty),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(accountPlaylists, key = { "acc-pl-${it.id}" }) { playlist ->
                            TvMediaCard(
                                title = playlist.title,
                                subtitle = playlist.author ?: playlist.trackCountText.orEmpty(),
                                thumbnailUrl = playlist.thumbnailUrl,
                                onClick = { onOpenPlaylist(playlist) },
                            )
                        }
                    }
                }
        }
    }
}
