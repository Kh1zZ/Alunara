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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvMediaCard
import com.alunara.app.ui.components.TvSongListSkeleton
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
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
    entryFocusRequester: FocusRequester = remember { FocusRequester() },
    requestInputFocus: Boolean = false,
    onInputFocusHandled: () -> Unit = {},
    onRetrySearch: () -> Unit = onSubmitSearch,
    onAddToPlaylist: ((Track) -> Unit)? = null,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFieldRequester = entryFocusRequester
    var fieldFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (textFieldValue.text != query) {
            textFieldValue = TextFieldValue(query, TextRange(query.length))
        }
    }
    LaunchedEffect(requestInputFocus) {
        if (requestInputFocus) {
            withFrameNanos { }
            if (searchFieldRequester.safeRequestFocus()) {
                keyboardController?.show()
            }
            onInputFocusHandled()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.search_headline),
            color = colors.primaryText,
            fontSize = AlunaraTypography.headline(prefs.textScale),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.focusProperties { canFocus = false },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
            val fieldBorderColor by animateColorAsState(
                targetValue = if (fieldFocused) colors.focusRingStrong else colors.secondarySurface,
                animationSpec = tween(prefs.focusAnimMs),
                label = "search_border",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = AlunaraDimens.ControlHeight)
                        .background(colors.surface, shape)
                        .border(BorderStroke(colors.focusBorderWidth, fieldBorderColor), shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = colors.dimmedLyrics,
                        fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    )
                }
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { updated ->
                        textFieldValue = updated
                        if (updated.text != query) {
                            onQueryChange(updated.text)
                        }
                    },
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = colors.primaryText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                        ),
                    cursorBrush = SolidColor(colors.primaryAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSubmitSearch()
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldRequester)
                            .onFocusChanged { fieldFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val sel = textFieldValue.selection
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        keyboardController?.hide()
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                    Key.DirectionUp -> {
                                        keyboardController?.hide()
                                        focusManager.moveFocus(FocusDirection.Up)
                                    }
                                    Key.DirectionLeft ->
                                        if (sel.collapsed && sel.start == 0) {
                                            keyboardController?.hide()
                                            focusManager.moveFocus(FocusDirection.Left)
                                        } else {
                                            false
                                        }
                                    Key.DirectionRight ->
                                        if (sel.collapsed && sel.end == textFieldValue.text.length) {
                                            keyboardController?.hide()
                                            focusManager.moveFocus(FocusDirection.Right)
                                        } else {
                                            false
                                        }
                                    else -> false
                                }
                            },
                )
            }

            TvFocusableButton(
                text = stringResource(R.string.search_button),
                icon = Icons.Rounded.Search,
                highlighted = true,
                onClick = {
                    keyboardController?.hide()
                    onSubmitSearch()
                },
            )
        }

        when {
            isLoading -> {
                TvSongListSkeleton(count = 5)
            }

            errorMessage != null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.errorSurface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.search_error)} ($errorMessage)",
                        color = colors.errorText,
                        fontSize = AlunaraTypography.body(prefs.textScale),
                    )
                    TvFocusableButton(
                        text = stringResource(R.string.retry),
                        icon = Icons.Rounded.Refresh,
                        onClick = onRetrySearch,
                    )
                }
            }

            results == null -> {
                Text(
                    text = stringResource(R.string.search_prompt),
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                )
            }

            results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty() && results.playlists.isEmpty() -> {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (results.songs.isNotEmpty()) {
                        item(key = "hdr-songs", contentType = "search-header") {
                            Text(
                                text = stringResource(R.string.section_songs),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(
                            count = results.songs.size,
                            key = { idx -> "song-$idx-${results.songs[idx].id}" },
                            contentType = { "search-song-row" },
                        ) { index ->
                            val song = results.songs[index]
                            TvSongRow(
                                track = song,
                                isActive = song.id == activeTrackId,
                                onPlayClick = { onPlaySong(results.songs, index) },
                                secondaryActionLabel = stringResource(R.string.add_to_queue),
                                secondaryActionIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                onSecondaryClick = { onEnqueueSong(song) },
                                tertiaryActionContentDescription = onAddToPlaylist?.let { stringResource(R.string.action_add_to_playlist) },
                                tertiaryActionIcon = onAddToPlaylist?.let { Icons.AutoMirrored.Rounded.PlaylistAdd },
                                onTertiaryClick = onAddToPlaylist?.let { cb -> { cb(song) } },
                            )
                        }
                    }

                    if (results.albums.isNotEmpty()) {
                        item(key = "hdr-albums", contentType = "search-header") {
                            Text(
                                text = stringResource(R.string.section_albums),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item(key = "rail-albums", contentType = "search-rail") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                items(
                                    items = results.albums,
                                    key = { "alb-${it.id}" },
                                    contentType = { "search-album-card" },
                                ) { album ->
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
                        item(key = "hdr-playlists", contentType = "search-header") {
                            Text(
                                text = stringResource(R.string.section_playlists),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item(key = "rail-playlists", contentType = "search-rail") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                items(
                                    items = results.playlists,
                                    key = { "pl-${it.id}" },
                                    contentType = { "search-playlist-card" },
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

                    if (results.artists.isNotEmpty()) {
                        item(key = "hdr-artists", contentType = "search-header") {
                            Text(
                                text = stringResource(R.string.section_artists),
                                color = colors.primaryAccent,
                                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        item(key = "rail-artists", contentType = "search-rail") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                items(
                                    items = results.artists,
                                    key = { "art-${it.id ?: it.name}" },
                                    contentType = { "search-artist-card" },
                                ) { artist ->
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
