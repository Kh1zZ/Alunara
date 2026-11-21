package com.alunara.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.components.TvArtwork
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.formatDurationMs
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.PlaybackStatus

@Composable
fun NowPlayingScreen(
    sessionState: PlaybackSessionUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = sessionState.currentTrack
    if (track == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.queue_empty),
                color = AlunaraPalette.MutedText,
                fontSize = 16.sp,
            )
        }
        return
    }

    val playButtonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        playButtonRequester.requestFocus()
    }

    val showLyricsPanel =
        sessionState.lyricsLoading ||
            sessionState.lyrics is LyricsDocument.Synced ||
            sessionState.lyrics is LyricsDocument.Plain

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TvArtwork(
                url = track.thumbnailUrl,
                contentDescription = track.title,
                size = if (showLyricsPanel) 148.dp else 176.dp,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Non-focusable, non-clickable metadata (UI/UX Spec §5.1 / UX-12)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.title,
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (track.primaryArtistName.isNotBlank()) {
                    Text(
                        text = track.primaryArtistName,
                        color = AlunaraPalette.MutedText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                track.album?.title?.takeIf { it.isNotBlank() }?.let { albumName ->
                    Text(
                        text = albumName,
                        color = AlunaraPalette.DimmedLyrics,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val statusLabel =
                when (sessionState.status) {
                    PlaybackStatus.RESOLVING -> stringResource(R.string.status_resolving)
                    PlaybackStatus.BUFFERING -> stringResource(R.string.status_buffering)
                    PlaybackStatus.PLAYING -> stringResource(R.string.status_playing)
                    PlaybackStatus.PAUSED -> stringResource(R.string.status_paused)
                    PlaybackStatus.ENDED -> stringResource(R.string.status_ended)
                    PlaybackStatus.ERROR -> sessionState.errorMessage ?: stringResource(R.string.status_error)
                    PlaybackStatus.IDLE -> ""
                }
            if (statusLabel.isNotEmpty()) {
                Text(
                    text = statusLabel,
                    color = if (sessionState.status == PlaybackStatus.ERROR) AlunaraPalette.ErrorText else AlunaraPalette.PrimaryAccent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SeekableProgressBar(
                positionMs = sessionState.positionMs,
                durationMs = sessionState.durationMs,
                onSeekBy = onSeekBy,
                modifier = Modifier.fillMaxWidth(0.92f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvFocusableButton(
                    text = stringResource(R.string.action_prev),
                    enabled = sessionState.queueState.items.isNotEmpty(),
                    onClick = onSkipPrevious,
                )
                TvFocusableButton(
                    text =
                        if (sessionState.status == PlaybackStatus.PLAYING) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                    highlighted = true,
                    onClick = onTogglePlayPause,
                    modifier = Modifier.focusRequester(playButtonRequester),
                )
                TvFocusableButton(
                    text = stringResource(R.string.action_next),
                    enabled = sessionState.queueState.currentIndex < sessionState.queueState.items.lastIndex,
                    onClick = onSkipNext,
                )
            }
        }

        if (showLyricsPanel) {
            PersistentReadOnlyLyricsPanel(
                lyrics = sessionState.lyrics,
                isLoading = sessionState.lyricsLoading,
                activeIndex = sessionState.activeLyricIndex,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SeekableProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier =
            modifier
                .background(AlunaraPalette.Background.copy(alpha = 0.55f), shape)
                .border(
                    if (focused) BorderStroke(2.dp, AlunaraPalette.FocusOutline) else BorderStroke(1.dp, Color.Transparent),
                    shape,
                ).onFocusChanged { focused = it.isFocused }
                .focusable()
                .onPreviewKeyEvent { event ->
                    // Seek happens strictly when progress bar is focused (UI/UX Spec §5.2 / UX-16)
                    if (focused && event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onSeekBy(-10_000L)
                                true
                            }
                            Key.DirectionRight -> {
                                onSeekBy(10_000L)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AlunaraPalette.SecondarySurface),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(AlunaraPalette.PrimaryAccent),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(positionMs),
                color = AlunaraPalette.PrimaryText,
                fontSize = 12.sp,
            )
            if (focused) {
                Text(
                    text = stringResource(R.string.seek_bar_label),
                    color = AlunaraPalette.FocusOutline,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDurationMs(durationMs),
                color = AlunaraPalette.MutedText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PersistentReadOnlyLyricsPanel(
    lyrics: LyricsDocument,
    isLoading: Boolean,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    // Persistent & strictly read-only lyrics panel: never focusable/clickable (UI/UX Spec §5.3 / UX-13)
    Column(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .background(AlunaraPalette.Background.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_heading),
            color = AlunaraPalette.PrimaryAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.lyrics_loading),
                    color = AlunaraPalette.MutedText,
                    fontSize = 15.sp,
                )
            }

            lyrics is LyricsDocument.Synced -> {
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) {
                        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                    }
                }
                LazyColumn(
                    state = listState,
                    userScrollEnabled = false,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = false },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lyrics.lines) { idx, line ->
                        val isActive = idx == activeIndex
                        Text(
                            text = line.text,
                            color = if (isActive) AlunaraPalette.PrimaryText else AlunaraPalette.DimmedLyrics,
                            fontSize = if (isActive) 18.sp else 15.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }

            lyrics is LyricsDocument.Plain -> {
                Text(
                    text = stringResource(R.string.lyrics_plain_label),
                    color = AlunaraPalette.DimmedLyrics,
                    fontSize = 12.sp,
                )
                val plainLines = remember(lyrics.text) { lyrics.text.lines() }
                LazyColumn(
                    userScrollEnabled = false,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = false },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(plainLines) { _, line ->
                        Text(
                            text = line,
                            color = AlunaraPalette.MutedText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}
