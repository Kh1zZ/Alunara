package com.alunara.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvArtwork
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.AuthSessionState
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.Track

@Composable
fun HomeScreen(
    sections: List<HomeSection>,
    isLoading: Boolean,
    errorMessage: String?,
    resumeTrack: Track?,
    recentHistory: List<Track>,
    favorites: List<Track>,
    onRetry: () -> Unit,
    onContinueQueue: () -> Unit,
    onPlaySongFromSection: (List<Track>, Int) -> Unit,
    onOpenAlbum: (AlbumRef) -> Unit,
    modifier: Modifier = Modifier,
    authSession: AuthSessionState = AuthSessionState.Guest,
    onOpenPlaylist: (PlaylistSummary) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text =
                        if (authSession is AuthSessionState.Authenticated) {
                            stringResource(R.string.badge_youtube_synced)
                        } else {
                            stringResource(R.string.status_badge)
                        },
                    color = AlunaraPalette.PrimaryAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        if (authSession is AuthSessionState.Authenticated) {
                            stringResource(R.string.home_headline_authenticated, authSession.profile.name)
                        } else {
                            stringResource(R.string.home_headline)
                        },
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (resumeTrack != null) {
            item {
                ContinueQueueCard(
                    track = resumeTrack,
                    onClick = onContinueQueue,
                )
            }
        }

        when {
            isLoading && sections.isEmpty() -> {
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusProperties { canFocus = false }
                                .padding(vertical = 24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_loading),
                            color = AlunaraPalette.MutedText,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            errorMessage != null && sections.isEmpty() -> {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(AlunaraPalette.ErrorSurface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.home_error)} ($errorMessage)",
                            color = AlunaraPalette.ErrorText,
                            fontSize = 14.sp,
                        )
                        TvFocusableButton(
                            text = stringResource(R.string.retry),
                            icon = Icons.Rounded.Refresh,
                            highlighted = true,
                            onClick = onRetry,
                        )
                    }
                }
            }

            sections.isEmpty() -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.home_empty),
                            color = AlunaraPalette.MutedText,
                            fontSize = 15.sp,
                        )
                        TvFocusableButton(
                            text = stringResource(R.string.retry),
                            icon = Icons.Rounded.Refresh,
                            onClick = onRetry,
                        )
                    }
                }
            }

            else -> {
                items(sections, key = { it.title }) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = section.title,
                            color = AlunaraPalette.PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.playlists, key = { pl -> "p-${section.title}-${pl.id}" }) { playlist ->
                                TvMediaCard(
                                    title = playlist.title,
                                    subtitle = playlist.author ?: playlist.trackCountText.orEmpty(),
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    onClick = { onOpenPlaylist(playlist) },
                                )
                            }
                            items(section.songs.size, key = { idx -> "s-${section.title}-$idx-${section.songs[idx].id}" }) { index ->
                                val song = section.songs[index]
                                TvMediaCard(
                                    title = song.title,
                                    subtitle = song.primaryArtistName,
                                    thumbnailUrl = song.thumbnailUrl,
                                    onClick = { onPlaySongFromSection(section.songs, index) },
                                )
                            }
                            items(section.albums, key = { alb -> "a-${section.title}-${alb.id}" }) { album ->
                                TvMediaCard(
                                    title = album.title,
                                    subtitle = album.artists.joinToString(", ") { it.name },
                                    thumbnailUrl = album.thumbnailUrl,
                                    onClick = { onOpenAlbum(album) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (recentHistory.isNotEmpty()) {
            item(key = "home-recent-history") {
                HomeSongRail(
                    title = stringResource(R.string.section_recently_played),
                    keyPrefix = "hist",
                    tracks = recentHistory.take(15),
                    onPlayAt = { idx -> onPlaySongFromSection(recentHistory.take(15), idx) },
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "home-favorites") {
                HomeSongRail(
                    title = stringResource(R.string.section_favorites),
                    keyPrefix = "fav",
                    tracks = favorites.take(15),
                    onPlayAt = { idx -> onPlaySongFromSection(favorites.take(15), idx) },
                )
            }
        }
    }
}

@Composable
private fun HomeSongRail(
    title: String,
    keyPrefix: String,
    tracks: List<Track>,
    onPlayAt: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = AlunaraPalette.PrimaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks.size, key = { idx -> "$keyPrefix-${tracks[idx].id}" }) { index ->
                val song = tracks[index]
                TvMediaCard(
                    title = song.title,
                    subtitle = song.primaryArtistName,
                    thumbnailUrl = song.thumbnailUrl,
                    onClick = { onPlayAt(index) },
                )
            }
        }
    }
}

@Composable
private fun ContinueQueueCard(
    track: Track,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val borderColor by animateColorAsState(
        targetValue = if (focused) AlunaraPalette.FocusOutline else Color.Transparent,
        animationSpec = tween(150),
        label = "continue_border",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AlunaraPalette.SecondarySurface, shape)
                .border(BorderStroke(AlunaraDimens.FocusBorderWidth, borderColor), shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvArtwork(url = track.thumbnailUrl, contentDescription = null, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.continue_queue),
                color = AlunaraPalette.PrimaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${track.title} · ${track.primaryArtistName}",
                color = AlunaraPalette.PrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.open_now_playing),
            color = AlunaraPalette.FocusOutline,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
