package com.alunara.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.alunara.app.ui.components.TvSongListSkeleton
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumDetail
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistDetail
import com.alunara.core.domain.model.PlaylistDetail
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CatalogTarget {
    data class Album(
        val albumId: String,
        val initialTitle: String,
        val initialSubtitle: String? = null,
        val initialThumbnailUrl: String? = null,
    ) : CatalogTarget

    data class Playlist(
        val playlistId: String,
        val initialTitle: String,
        val initialSubtitle: String? = null,
        val initialThumbnailUrl: String? = null,
    ) : CatalogTarget

    data class Artist(
        val artistId: String,
        val initialName: String,
        val initialThumbnailUrl: String? = null,
    ) : CatalogTarget
}

private sealed interface CatalogDetailUiState {
    data object Loading : CatalogDetailUiState

    data class Error(
        val message: String,
    ) : CatalogDetailUiState

    data class LoadedAlbum(
        val detail: AlbumDetail,
    ) : CatalogDetailUiState

    data class LoadedPlaylist(
        val detail: PlaylistDetail,
    ) : CatalogDetailUiState

    data class LoadedArtist(
        val detail: ArtistDetail,
    ) : CatalogDetailUiState
}

@Composable
fun CatalogDetailScreen(
    target: CatalogTarget,
    musicEngine: MusicEngine,
    activeTrackId: String?,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onEnqueueAll: (List<Track>) -> Unit,
    onStartRadio: (Track) -> Unit,
    onOpenAlbum: (AlbumRef) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var uiState by remember(target) { mutableStateOf<CatalogDetailUiState>(CatalogDetailUiState.Loading) }
    var reloadTrigger by remember(target) { mutableIntStateOf(0) }
    val primaryFocusRequester = remember(target) { FocusRequester() }

    LaunchedEffect(target, reloadTrigger) {
        uiState = CatalogDetailUiState.Loading
        val result: CatalogDetailUiState =
            withContext(Dispatchers.IO) {
                when (target) {
                    is CatalogTarget.Album ->
                        musicEngine.fetchAlbumDetail(target.albumId).fold(
                            onSuccess = { CatalogDetailUiState.LoadedAlbum(it) },
                            onFailure = { CatalogDetailUiState.Error(it.message ?: "Error") },
                        )

                    is CatalogTarget.Playlist ->
                        musicEngine.fetchPlaylistDetail(target.playlistId).fold(
                            onSuccess = { CatalogDetailUiState.LoadedPlaylist(it) },
                            onFailure = { CatalogDetailUiState.Error(it.message ?: "Error") },
                        )

                    is CatalogTarget.Artist ->
                        musicEngine.fetchArtistDetail(target.artistId).fold(
                            onSuccess = { CatalogDetailUiState.LoadedArtist(it) },
                            onFailure = { CatalogDetailUiState.Error(it.message ?: "Error") },
                        )
                }
            }
        uiState = result
    }

    LaunchedEffect(target) {
        withFrameNanos { }
        primaryFocusRequester.safeRequestFocus()
    }

    val badgeLabel =
        when (target) {
            is CatalogTarget.Album -> stringResource(R.string.catalog_album_label)
            is CatalogTarget.Playlist -> stringResource(R.string.catalog_playlist_label)
            is CatalogTarget.Artist -> stringResource(R.string.catalog_artist_label)
        }

    val (title, subtitle, artworkUrl, primaryTracks) =
        when (val state = uiState) {
            is CatalogDetailUiState.LoadedAlbum ->
                CatalogHeaderData(
                    title = state.detail.album.title,
                    subtitle =
                        listOfNotNull(
                            state.detail.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                            state.detail.album.year?.toString(),
                        ).joinToString(" · "),
                    artworkUrl = state.detail.album.thumbnailUrl,
                    tracks = state.detail.tracks,
                )

            is CatalogDetailUiState.LoadedPlaylist ->
                CatalogHeaderData(
                    title = state.detail.playlist.title,
                    subtitle =
                        listOfNotNull(
                            state.detail.playlist.author?.takeIf { it.isNotBlank() },
                            state.detail.playlist.trackCountText?.takeIf { it.isNotBlank() },
                            state.detail.description?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    artworkUrl = state.detail.playlist.thumbnailUrl,
                    tracks = state.detail.tracks,
                )

            is CatalogDetailUiState.LoadedArtist ->
                CatalogHeaderData(
                    title = state.detail.artist.name,
                    subtitle = state.detail.description.orEmpty(),
                    artworkUrl = state.detail.artist.thumbnailUrl,
                    tracks = state.detail.topSongs,
                )

            CatalogDetailUiState.Loading, is CatalogDetailUiState.Error ->
                when (target) {
                    is CatalogTarget.Album ->
                        CatalogHeaderData(target.initialTitle, target.initialSubtitle.orEmpty(), target.initialThumbnailUrl, emptyList())
                    is CatalogTarget.Playlist ->
                        CatalogHeaderData(target.initialTitle, target.initialSubtitle.orEmpty(), target.initialThumbnailUrl, emptyList())
                    is CatalogTarget.Artist ->
                        CatalogHeaderData(target.initialName, "", target.initialThumbnailUrl, emptyList())
                }
        }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CatalogActionHeader(
            badgeLabel = badgeLabel,
            title = title,
            subtitle = subtitle,
            artworkUrl = artworkUrl,
            circularArtwork = target is CatalogTarget.Artist,
            hasTracks = primaryTracks.isNotEmpty(),
            backFocusRequester = primaryFocusRequester,
            onBack = onBack,
            onPlayAll = {
                if (primaryTracks.isNotEmpty()) {
                    onPlayTracks(primaryTracks, 0)
                }
            },
            onStartRadio = {
                primaryTracks.firstOrNull()?.let(onStartRadio)
            },
            onEnqueueAll = {
                if (primaryTracks.isNotEmpty()) {
                    onEnqueueAll(primaryTracks)
                }
            },
        )

        when (val state = uiState) {
            CatalogDetailUiState.Loading -> {
                TvSongListSkeleton(count = 5)
            }

            is CatalogDetailUiState.Error -> {
                val colors = LocalAlunaraColors.current
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.errorSurface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.catalog_error)} (${state.message})",
                        color = colors.errorText,
                        fontSize = 14.sp,
                    )
                    TvFocusableButton(
                        text = stringResource(R.string.retry),
                        icon = Icons.Rounded.Refresh,
                        highlighted = true,
                        onClick = { reloadTrigger++ },
                    )
                }
            }

            is CatalogDetailUiState.LoadedAlbum ->
                CatalogTrackList(
                    tracks = state.detail.tracks,
                    activeTrackId = activeTrackId,
                    onPlayAt = { idx -> onPlayTracks(state.detail.tracks, idx) },
                    onEnqueueTrack = onEnqueueTrack,
                    modifier = Modifier.weight(1f),
                )

            is CatalogDetailUiState.LoadedPlaylist ->
                CatalogTrackList(
                    tracks = state.detail.tracks,
                    activeTrackId = activeTrackId,
                    onPlayAt = { idx -> onPlayTracks(state.detail.tracks, idx) },
                    onEnqueueTrack = onEnqueueTrack,
                    modifier = Modifier.weight(1f),
                )

            is CatalogDetailUiState.LoadedArtist ->
                ArtistDetailContent(
                    detail = state.detail,
                    activeTrackId = activeTrackId,
                    onPlaySong = { idx -> onPlayTracks(state.detail.topSongs, idx) },
                    onEnqueueTrack = onEnqueueTrack,
                    onOpenAlbum = onOpenAlbum,
                    onOpenPlaylist = onOpenPlaylist,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

private data class CatalogHeaderData(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<Track>,
)

@Composable
private fun CatalogActionHeader(
    badgeLabel: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    circularArtwork: Boolean,
    hasTracks: Boolean,
    backFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onStartRadio: () -> Unit,
    onEnqueueAll: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(AlunaraDimens.RadiusCard))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvArtwork(
            url = artworkUrl,
            contentDescription = title,
            size = 112.dp,
            circular = circularArtwork,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = badgeLabel,
                color = colors.primaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.mutedText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvFocusableButton(
                    text = stringResource(R.string.action_back),
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backFocusRequester),
                )
                TvFocusableButton(
                    text = stringResource(R.string.catalog_play_all),
                    icon = Icons.Rounded.PlayArrow,
                    highlighted = hasTracks,
                    enabled = hasTracks,
                    onClick = onPlayAll,
                )
                TvFocusableButton(
                    text = stringResource(R.string.action_start_radio),
                    icon = Icons.Rounded.Radio,
                    enabled = hasTracks,
                    onClick = onStartRadio,
                )
                TvFocusableButton(
                    text = stringResource(R.string.catalog_enqueue_all),
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    enabled = hasTracks,
                    onClick = onEnqueueAll,
                )
            }
        }
    }
}

@Composable
private fun CatalogTrackList(
    tracks: List<Track>,
    activeTrackId: String?,
    onPlayAt: (Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.catalog_empty),
                color = colors.mutedText,
                fontSize = 15.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(tracks.size, key = { idx -> "cat-trk-${tracks[idx].id}" }) { index ->
            val track = tracks[index]
            TvSongRow(
                track = track,
                isActive = track.id == activeTrackId,
                onPlayClick = { onPlayAt(index) },
                secondaryActionLabel = stringResource(R.string.add_to_queue),
                secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                onSecondaryClick = { onEnqueueTrack(track) },
            )
        }
    }
}

@Composable
private fun ArtistDetailContent(
    detail: ArtistDetail,
    activeTrackId: String?,
    onPlaySong: (Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onOpenAlbum: (AlbumRef) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    if (detail.topSongs.isEmpty() && detail.albums.isEmpty() && detail.playlists.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.catalog_empty),
                color = colors.mutedText,
                fontSize = 15.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (detail.topSongs.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.catalog_top_songs),
                    color = colors.primaryAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(detail.topSongs.size, key = { idx -> "art-song-${detail.topSongs[idx].id}" }) { index ->
                val song = detail.topSongs[index]
                TvSongRow(
                    track = song,
                    isActive = song.id == activeTrackId,
                    onPlayClick = { onPlaySong(index) },
                    secondaryActionLabel = stringResource(R.string.add_to_queue),
                    secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    onSecondaryClick = { onEnqueueTrack(song) },
                )
            }
        }

        if (detail.albums.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.section_albums),
                    color = colors.primaryAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(detail.albums, key = { "art-alb-${it.id}" }) { album ->
                        TvMediaCard(
                            title = album.title,
                            subtitle = album.year?.toString() ?: album.artists.joinToString(", ") { it.name },
                            thumbnailUrl = album.thumbnailUrl,
                            onClick = { onOpenAlbum(album) },
                        )
                    }
                }
            }
        }

        if (detail.playlists.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.section_playlists),
                    color = colors.primaryAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(detail.playlists, key = { "art-pl-${it.id}" }) { playlist ->
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
