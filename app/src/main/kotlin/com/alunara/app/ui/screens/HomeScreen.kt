package com.alunara.app.ui.screens

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
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.HomeSection
import com.alunara.core.domain.model.Track

@Composable
fun HomeScreen(
    sections: List<HomeSection>,
    isLoading: Boolean,
    errorMessage: String?,
    resumeTrack: Track?,
    onRetry: () -> Unit,
    onContinueQueue: () -> Unit,
    onPlaySongFromSection: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.status_badge),
                    color = AlunaraPalette.PrimaryAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.home_headline),
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
                            items(section.songs.size, key = { idx -> "s-${section.songs[idx].id}" }) { index ->
                                val song = section.songs[index]
                                TvMediaCard(
                                    title = song.title,
                                    subtitle = song.primaryArtistName,
                                    thumbnailUrl = song.thumbnailUrl,
                                    onClick = { onPlaySongFromSection(section.songs, index) },
                                )
                            }
                            items(section.albums, key = { alb -> "a-${alb.id}" }) { album ->
                                TvMediaCard(
                                    title = album.title,
                                    subtitle = album.artists.joinToString(", ") { it.name },
                                    thumbnailUrl = album.thumbnailUrl,
                                    onClick = {
                                        // Album browsing arrives in M3; keeps D-pad navigation safe in M2
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

@Composable
private fun ContinueQueueCard(
    track: Track,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val border =
        if (focused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AlunaraPalette.SecondarySurface, shape)
                .border(border, shape)
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
