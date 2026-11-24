package com.alunara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvArtwork
import com.alunara.app.ui.components.TvCardRowSkeleton
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
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
    entryFocusRequester: FocusRequester = remember { FocusRequester() },
    authSession: AuthSessionState = AuthSessionState.Guest,
    onOpenPlaylist: (PlaylistSummary) -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val rowContentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "home-header", contentType = "home-header") {
            Column(
                modifier = Modifier.focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text =
                        if (authSession is AuthSessionState.Authenticated) {
                            stringResource(R.string.badge_youtube_synced)
                        } else {
                            stringResource(R.string.status_badge)
                        },
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.label(prefs.textScale),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        if (authSession is AuthSessionState.Authenticated) {
                            stringResource(R.string.home_headline_authenticated, authSession.profile.name)
                        } else {
                            stringResource(R.string.home_headline)
                        },
                    color = colors.primaryText,
                    fontSize = AlunaraTypography.headline(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (resumeTrack != null) {
            item(key = "home-continue", contentType = "home-continue") {
                ContinueQueueCard(
                    track = resumeTrack,
                    onClick = onContinueQueue,
                    modifier = Modifier.focusRequester(entryFocusRequester),
                )
            }
        }

        when {
            isLoading && sections.isEmpty() -> {
                items(2, contentType = { "home-skeleton" }) {
                    TvCardRowSkeleton(count = 5)
                }
            }

            errorMessage != null && sections.isEmpty() -> {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(colors.errorSurface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.home_error)} ($errorMessage)",
                            color = colors.errorText,
                            fontSize = AlunaraTypography.body(prefs.textScale),
                        )
                        TvFocusableButton(
                            text = stringResource(R.string.retry),
                            icon = Icons.Rounded.Refresh,
                            highlighted = true,
                            onClick = onRetry,
                            modifier = if (resumeTrack == null) Modifier.focusRequester(entryFocusRequester) else Modifier,
                        )
                    }
                }
            }

            sections.isEmpty() -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.home_empty),
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                        )
                        TvFocusableButton(
                            text = stringResource(R.string.retry),
                            icon = Icons.Rounded.Refresh,
                            onClick = onRetry,
                            modifier = if (resumeTrack == null) Modifier.focusRequester(entryFocusRequester) else Modifier,
                        )
                    }
                }
            }

            else -> {
                val firstEntrySectionIdx =
                    if (resumeTrack == null) {
                        sections.indexOfFirst { it.playlists.isNotEmpty() || it.songs.isNotEmpty() || it.albums.isNotEmpty() }
                    } else {
                        -1
                    }
                itemsIndexed(
                    items = sections,
                    key = { _, sec -> sec.title },
                    contentType = { _, _ -> "home-section" },
                ) { sectionIndex, section ->
                    val attachEntryInSection = sectionIndex == firstEntrySectionIdx
                    val firstTargetBucket =
                        when {
                            section.playlists.isNotEmpty() -> 0
                            section.songs.isNotEmpty() -> 1
                            else -> 2
                        }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = section.title,
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = rowContentPadding,
                        ) {
                            itemsIndexed(
                                items = section.playlists,
                                key = { _, pl -> "p-${section.title}-${pl.id}" },
                                contentType = { _, _ -> "home-playlist-card" },
                            ) { idx, playlist ->
                                TvMediaCard(
                                    title = playlist.title,
                                    subtitle = playlist.author ?: playlist.trackCountText.orEmpty(),
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    onClick = { onOpenPlaylist(playlist) },
                                    modifier =
                                        if (attachEntryInSection && firstTargetBucket == 0 && idx == 0) {
                                            Modifier.focusRequester(entryFocusRequester)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                            items(
                                count = section.songs.size,
                                key = { idx -> "s-${section.title}-$idx-${section.songs[idx].id}" },
                                contentType = { "home-song-card" },
                            ) { index ->
                                val song = section.songs[index]
                                TvMediaCard(
                                    title = song.title,
                                    subtitle = song.primaryArtistName,
                                    thumbnailUrl = song.thumbnailUrl,
                                    onClick = { onPlaySongFromSection(section.songs, index) },
                                    modifier =
                                        if (attachEntryInSection && firstTargetBucket == 1 && index == 0) {
                                            Modifier.focusRequester(entryFocusRequester)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                            itemsIndexed(
                                items = section.albums,
                                key = { _, alb -> "a-${section.title}-${alb.id}" },
                                contentType = { _, _ -> "home-album-card" },
                            ) { idx, album ->
                                val artistLine = remember(album.artists) { album.artists.joinToString(", ") { it.name } }
                                TvMediaCard(
                                    title = album.title,
                                    subtitle = artistLine,
                                    thumbnailUrl = album.thumbnailUrl,
                                    onClick = { onOpenAlbum(album) },
                                    modifier =
                                        if (attachEntryInSection && firstTargetBucket == 2 && idx == 0) {
                                            Modifier.focusRequester(entryFocusRequester)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (recentHistory.isNotEmpty()) {
            item(key = "home-recent-history", contentType = "home-song-rail") {
                val recentSlice = remember(recentHistory) { recentHistory.take(15) }
                HomeSongRail(
                    title = stringResource(R.string.section_recently_played),
                    keyPrefix = "hist",
                    tracks = recentSlice,
                    onPlayAt = { idx -> onPlaySongFromSection(recentSlice, idx) },
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "home-favorites", contentType = "home-song-rail") {
                val favSlice = remember(favorites) { favorites.take(15) }
                HomeSongRail(
                    title = stringResource(R.string.section_favorites),
                    keyPrefix = "fav",
                    tracks = favSlice,
                    onPlayAt = { idx -> onPlaySongFromSection(favSlice, idx) },
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
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.focusProperties { canFocus = false },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        ) {
            items(
                count = tracks.size,
                key = { idx -> "$keyPrefix-${tracks[idx].id}" },
                contentType = { "home-song-card" },
            ) { index ->
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

private val ContinueCardShape = RoundedCornerShape(AlunaraDimens.RadiusControl)

@Composable
private fun ContinueQueueCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ContinueCardShape)
                .drawBehind {
                    val cr = CornerRadius(AlunaraDimens.RadiusControl.toPx())
                    drawRoundRect(
                        color = if (focused) colors.focusedCardSurface else colors.secondarySurface,
                        cornerRadius = cr,
                    )
                    if (focused) {
                        val strokePx = colors.focusBorderWidth.toPx()
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = colors.focusRingStrong,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = cr,
                            style = Stroke(width = strokePx),
                        )
                    }
                }
                .semantics { role = Role.Button }
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
                color = colors.primaryAccent,
                fontSize = AlunaraTypography.caption(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${track.title} · ${track.primaryArtistName}",
                color = colors.primaryText,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.open_now_playing),
            color = colors.focusRingStrong,
            fontSize = AlunaraTypography.label(prefs.textScale),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
