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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvArtwork
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongListSkeleton
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.contract.MusicEngine
import com.alunara.core.domain.model.AlbumDetail
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistDetail
import com.alunara.core.domain.model.AuthSessionState
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

internal sealed interface CatalogDetailUiState {
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
internal fun CatalogDetailScreen(
    target: CatalogTarget,
    musicEngine: MusicEngine,
    detailsCache: MutableMap<CatalogTarget, CatalogDetailUiState>,
    activeTrackId: String?,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onEnqueueTrack: (Track) -> Unit,
    onEnqueueAll: (List<Track>) -> Unit,
    onStartRadio: (Track) -> Unit,
    onOpenAlbum: (AlbumRef) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester = remember { FocusRequester() },
    authSession: AuthSessionState = AuthSessionState.Guest,
    savedPlaylistIds: Set<String> = emptySet(),
    subscribedArtistIds: Set<String> = emptySet(),
    onToggleSavePlaylist: (PlaylistSummary) -> Unit = {},
    onToggleArtistSubscription: (String, String?, String?) -> Unit = { _, _, _ -> },
    onAddTrackToPlaylist: ((Track) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((String, Track, () -> Unit) -> Unit)? = null,
) {
    var uiState by remember(target, detailsCache) {
        mutableStateOf(detailsCache[target] ?: CatalogDetailUiState.Loading)
    }
    var reloadTrigger by remember(target) { mutableIntStateOf(0) }
    val primaryFocusRequester = entryFocusRequester

    LaunchedEffect(target, reloadTrigger, detailsCache) {
        if (reloadTrigger == 0 && detailsCache.containsKey(target)) return@LaunchedEffect
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
        if (result !is CatalogDetailUiState.Error) {
            detailsCache[target] = result
            if (detailsCache.size > 8) detailsCache.remove(detailsCache.keys.first())
        }
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

    val isAuthenticated = authSession is AuthSessionState.Authenticated
    val targetPlaylistSummary: PlaylistSummary? =
        when (val state = uiState) {
            is CatalogDetailUiState.LoadedPlaylist -> state.detail.playlist
            is CatalogDetailUiState.LoadedAlbum ->
                state.detail.album.playlistId?.takeIf { it.isNotBlank() }?.let { plId ->
                    PlaylistSummary(
                        id = plId,
                        title = state.detail.album.title,
                        author = state.detail.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                        thumbnailUrl = state.detail.album.thumbnailUrl,
                    )
                }
            else -> null
        }
    val cleanTargetPlaylistId = targetPlaylistSummary?.id?.removePrefix("VL")
    val isPlaylistSaved = cleanTargetPlaylistId != null && cleanTargetPlaylistId in savedPlaylistIds
    val loadedArtistDetail = (uiState as? CatalogDetailUiState.LoadedArtist)?.detail
    val isArtistSubscribed =
        loadedArtistDetail != null && loadedArtistDetail.artist.id in subscribedArtistIds

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
            savePlaylistLabel =
                if (isAuthenticated && targetPlaylistSummary != null) {
                    if (isPlaylistSaved) stringResource(R.string.action_saved_to_library) else stringResource(R.string.action_save_to_library)
                } else {
                    null
                },
            savePlaylistHighlighted = isPlaylistSaved,
            onToggleSavePlaylist = targetPlaylistSummary?.let { pl -> { onToggleSavePlaylist(pl) } },
            subscribeArtistLabel =
                if (isAuthenticated && loadedArtistDetail != null && !loadedArtistDetail.artist.id.isNullOrBlank()) {
                    if (isArtistSubscribed) stringResource(R.string.action_subscribed) else stringResource(R.string.action_subscribe)
                } else {
                    null
                },
            subscribeArtistHighlighted = isArtistSubscribed,
            onToggleSubscribeArtist =
                loadedArtistDetail?.artist?.id?.takeIf { it.isNotBlank() }?.let { artId ->
                    { onToggleArtistSubscription(artId, loadedArtistDetail.channelId, loadedArtistDetail.artist.name) }
                },
        )

        when (val state = uiState) {
            CatalogDetailUiState.Loading -> {
                TvSongListSkeleton(count = 5)
            }

            is CatalogDetailUiState.Error -> {
                val colors = LocalAlunaraColors.current
                val prefs = LocalAlunaraDisplayPrefs.current
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
                        fontSize = AlunaraTypography.body(prefs.textScale),
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
                    onAddTrackToPlaylist = onAddTrackToPlaylist,
                    modifier = Modifier.weight(1f),
                )

            is CatalogDetailUiState.LoadedPlaylist ->
                CatalogTrackList(
                    tracks = state.detail.tracks,
                    activeTrackId = activeTrackId,
                    onPlayAt = { idx -> onPlayTracks(state.detail.tracks, idx) },
                    onEnqueueTrack = onEnqueueTrack,
                    onAddTrackToPlaylist = onAddTrackToPlaylist,
                    onRemoveTrackFromPlaylist =
                        if (isAuthenticated && isPlaylistSaved && onRemoveTrackFromPlaylist != null) {
                            { track ->
                                onRemoveTrackFromPlaylist(state.detail.playlist.id, track) {
                                    detailsCache.remove(target)
                                    reloadTrigger++
                                }
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.weight(1f),
                )

            is CatalogDetailUiState.LoadedArtist ->
                ArtistDetailContent(
                    detail = state.detail,
                    activeTrackId = activeTrackId,
                    onPlaySong = { idx -> onPlayTracks(state.detail.topSongs, idx) },
                    onEnqueueTrack = onEnqueueTrack,
                    onAddTrackToPlaylist = onAddTrackToPlaylist,
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
    savePlaylistLabel: String? = null,
    savePlaylistHighlighted: Boolean = false,
    onToggleSavePlaylist: (() -> Unit)? = null,
    subscribeArtistLabel: String? = null,
    subscribeArtistHighlighted: Boolean = false,
    onToggleSubscribeArtist: (() -> Unit)? = null,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
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
                fontSize = AlunaraTypography.caption(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = AlunaraTypography.title(prefs.textScale),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(prefs.textScale),
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
                if (savePlaylistLabel != null && onToggleSavePlaylist != null) {
                    TvFocusableButton(
                        text = savePlaylistLabel,
                        icon = if (savePlaylistHighlighted) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        highlighted = savePlaylistHighlighted,
                        onClick = onToggleSavePlaylist,
                    )
                }
                if (subscribeArtistLabel != null && onToggleSubscribeArtist != null) {
                    TvFocusableButton(
                        text = subscribeArtistLabel,
                        icon = if (subscribeArtistHighlighted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        highlighted = subscribeArtistHighlighted,
                        onClick = onToggleSubscribeArtist,
                    )
                }
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
    onAddTrackToPlaylist: ((Track) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.catalog_empty),
                color = colors.mutedText,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(
            count = tracks.size,
            key = { idx -> "cat-trk-${tracks[idx].setVideoId ?: idx}-${tracks[idx].id}" },
            contentType = { "catalog-song-row" },
        ) { index ->
            val track = tracks[index]
            TvSongRow(
                track = track,
                isActive = track.id == activeTrackId,
                onPlayClick = { onPlayAt(index) },
                secondaryActionLabel = stringResource(R.string.add_to_queue),
                secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                onSecondaryClick = { onEnqueueTrack(track) },
                tertiaryActionContentDescription =
                    when {
                        onRemoveTrackFromPlaylist != null -> stringResource(R.string.action_remove_from_playlist)
                        onAddTrackToPlaylist != null -> stringResource(R.string.action_add_to_playlist)
                        else -> null
                    },
                tertiaryActionIcon =
                    when {
                        onRemoveTrackFromPlaylist != null -> Icons.Rounded.DeleteOutline
                        onAddTrackToPlaylist != null -> Icons.AutoMirrored.Rounded.PlaylistAdd
                        else -> null
                    },
                onTertiaryClick =
                    when {
                        onRemoveTrackFromPlaylist != null -> { { onRemoveTrackFromPlaylist(track) } }
                        onAddTrackToPlaylist != null -> { { onAddTrackToPlaylist(track) } }
                        else -> null
                    },
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
    onAddTrackToPlaylist: ((Track) -> Unit)? = null,
    onOpenAlbum: (AlbumRef) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    if (detail.topSongs.isEmpty() && detail.albums.isEmpty() && detail.playlists.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.catalog_empty),
                color = colors.mutedText,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
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
            item(key = "hdr-top-songs", contentType = "catalog-header") {
                Text(
                    text = stringResource(R.string.catalog_top_songs),
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                count = detail.topSongs.size,
                key = { idx -> "art-song-${detail.topSongs[idx].id}" },
                contentType = { "catalog-song-row" },
            ) { index ->
                val song = detail.topSongs[index]
                TvSongRow(
                    track = song,
                    isActive = song.id == activeTrackId,
                    onPlayClick = { onPlaySong(index) },
                    secondaryActionLabel = stringResource(R.string.add_to_queue),
                    secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    onSecondaryClick = { onEnqueueTrack(song) },
                    tertiaryActionContentDescription = onAddTrackToPlaylist?.let { stringResource(R.string.action_add_to_playlist) },
                    tertiaryActionIcon = onAddTrackToPlaylist?.let { Icons.AutoMirrored.Rounded.PlaylistAdd },
                    onTertiaryClick = onAddTrackToPlaylist?.let { cb -> { cb(song) } },
                )
            }
        }

        if (detail.albums.isNotEmpty()) {
            item(key = "hdr-albums", contentType = "catalog-header") {
                Text(
                    text = stringResource(R.string.section_albums),
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "rail-albums", contentType = "catalog-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(
                        items = detail.albums,
                        key = { "art-alb-${it.id}" },
                        contentType = { "catalog-album-card" },
                    ) { album ->
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
            item(key = "hdr-playlists", contentType = "catalog-header") {
                Text(
                    text = stringResource(R.string.section_playlists),
                    color = colors.primaryAccent,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "rail-playlists", contentType = "catalog-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(
                        items = detail.playlists,
                        key = { "art-pl-${it.id}" },
                        contentType = { "catalog-playlist-card" },
                    ) { playlist ->
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
