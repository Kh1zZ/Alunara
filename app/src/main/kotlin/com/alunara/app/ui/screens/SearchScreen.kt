package com.alunara.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.PlaylistSummary
import com.alunara.core.domain.model.SearchResults
import com.alunara.core.domain.model.Track

@Composable
fun SearchScreen(
    query: String,
    results: SearchResults?,
    isLoading: Boolean,
    errorMessage: String?,
    activeTrackId: String?,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onPlaySong: (List<Track>, Int) -> Unit,
    onEnqueueSong: (Track) -> Unit,
    onOpenAlbum: (AlbumRef) -> Unit,
    onOpenArtist: (ArtistRef) -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.search_headline),
            color = AlunaraPalette.PrimaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
            val fieldBorderColor by animateColorAsState(
                targetValue = if (fieldFocused) AlunaraPalette.FocusOutline else AlunaraPalette.SecondarySurface,
                animationSpec = tween(150),
                label = "search_border",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(AlunaraDimens.ControlHeight)
                        .background(AlunaraPalette.Surface, shape)
                        .border(BorderStroke(AlunaraDimens.FocusBorderWidth, fieldBorderColor), shape)
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = AlunaraPalette.DimmedLyrics,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = AlunaraPalette.PrimaryText,
                            fontSize = 15.sp,
                        ),
                    cursorBrush = SolidColor(AlunaraPalette.PrimaryAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { fieldFocused = it.isFocused },
                )
            }

            TvFocusableButton(
                text = stringResource(R.string.search_button),
                icon = Icons.Rounded.Search,
                highlighted = true,
                onClick = onSubmitSearch,
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = false }
                            .padding(vertical = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.search_loading),
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                    )
                }
            }

            errorMessage != null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(AlunaraPalette.ErrorSurface, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.search_error)} ($errorMessage)",
                        color = AlunaraPalette.ErrorText,
                        fontSize = 14.sp,
                    )
                    TvFocusableButton(
                        text = stringResource(R.string.retry),
                        icon = Icons.Rounded.Refresh,
                        onClick = onSubmitSearch,
                    )
                }
            }

            results == null -> {
                Text(
                    text = stringResource(R.string.search_prompt),
                    color = AlunaraPalette.MutedText,
                    fontSize = 15.sp,
                )
            }

            results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty() && results.playlists.isEmpty() -> {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = AlunaraPalette.MutedText,
                    fontSize = 15.sp,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (results.songs.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_songs),
                                color = AlunaraPalette.PrimaryAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(results.songs.size, key = { idx -> "song-${results.songs[idx].id}" }) { index ->
                            val song = results.songs[index]
                            TvSongRow(
                                track = song,
                                isActive = song.id == activeTrackId,
                                onPlayClick = { onPlaySong(results.songs, index) },
                                secondaryActionLabel = stringResource(R.string.add_to_queue),
                                secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                onSecondaryClick = { onEnqueueSong(song) },
                            )
                        }
                    }

                    if (results.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_albums),
                                color = AlunaraPalette.PrimaryAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.albums, key = { "alb-${it.id}" }) { album ->
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

                    if (results.playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_playlists),
                                color = AlunaraPalette.PrimaryAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.playlists, key = { "pl-${it.id}" }) { playlist ->
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

                    if (results.artists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_artists),
                                color = AlunaraPalette.PrimaryAccent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.artists, key = { "art-${it.id ?: it.name}" }) { artist ->
                                    TvMediaCard(
                                        title = artist.name,
                                        subtitle = "",
                                        thumbnailUrl = artist.thumbnailUrl,
                                        circular = true,
                                        onClick = {
                                            if (!artist.id.isNullOrBlank()) {
                                                onOpenArtist(artist)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
