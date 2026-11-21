package com.alunara.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.components.TvIconButton
import com.alunara.app.ui.components.formatDurationMs
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.lyrics.LyricsSyncResolver
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaybackStatus

@Composable
fun NowPlayingScreen(
    sessionState: PlaybackSessionUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSwitchLyricsProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = sessionState.currentTrack
    if (track == null) {
        Box(
            modifier = modifier.fillMaxSize().background(AlunaraPalette.Background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.queue_empty),
                color = AlunaraPalette.MutedText,
                fontSize = 18.sp,
            )
        }
        return
    }

    val playButtonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        playButtonRequester.requestFocus()
    }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(AlunaraPalette.Background),
    ) {
        // Full-bleed left pane: edge-to-edge album artwork + vertical & horizontal Dark Forest gradient fade
        Box(
            modifier =
                Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
        ) {
            if (!track.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(AlunaraPalette.SecondarySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = track.title,
                        tint = AlunaraPalette.PrimaryAccent.copy(alpha = 0.45f),
                        modifier = Modifier.size(120.dp),
                    )
                }
            }

            // Vertical scrim ensuring crisp contrast over bright/saturated album art
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to AlunaraPalette.Background.copy(alpha = 0.08f),
                                0.36f to AlunaraPalette.Background.copy(alpha = 0.32f),
                                0.62f to AlunaraPalette.Background.copy(alpha = 0.80f),
                                0.85f to AlunaraPalette.Background.copy(alpha = 0.96f),
                                1.0f to AlunaraPalette.Background,
                            ),
                        ),
            )

            // Horizontal edge fade blending artwork seamlessly into the right-hand lyrics pane
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.56f to Color.Transparent,
                                0.85f to AlunaraPalette.Background.copy(alpha = 0.75f),
                                1.0f to AlunaraPalette.Background,
                            ),
                        ),
            )

            // Foreground track metadata, status, zero-shift seek timeline & Material 3 transport controls
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 36.dp, end = 36.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Non-focusable, non-clickable metadata (UI/UX Spec §5.1 / UX-12)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = false },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = track.title,
                        color = AlunaraPalette.PrimaryText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle =
                        listOfNotNull(
                            track.primaryArtistName.takeIf { it.isNotBlank() },
                            track.album?.title?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = AlunaraPalette.MutedText,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

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
                        val statusIcon =
                            when (sessionState.status) {
                                PlaybackStatus.PLAYING -> Icons.Rounded.GraphicEq
                                PlaybackStatus.PAUSED, PlaybackStatus.ENDED -> Icons.Rounded.PauseCircleOutline
                                PlaybackStatus.ERROR -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.HourglassTop
                            }
                        val statusColor =
                            if (sessionState.status == PlaybackStatus.ERROR) {
                                AlunaraPalette.ErrorText
                            } else {
                                AlunaraPalette.PrimaryAccent
                            }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                SeekableProgressBar(
                    positionMs = sessionState.positionMs,
                    durationMs = sessionState.durationMs,
                    onSeekBy = onSeekBy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.action_prev),
                        size = 46.dp,
                        iconSize = 24.dp,
                        enabled = sessionState.queueState.items.isNotEmpty(),
                        onClick = onSkipPrevious,
                    )
                    TvIconButton(
                        icon = if (sessionState.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription =
                            if (sessionState.status == PlaybackStatus.PLAYING) {
                                stringResource(R.string.action_pause)
                            } else {
                                stringResource(R.string.action_play)
                            },
                        size = 56.dp,
                        iconSize = 30.dp,
                        highlighted = true,
                        onClick = onTogglePlayPause,
                        modifier = Modifier.focusRequester(playButtonRequester),
                    )
                    TvIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.action_next),
                        size = 46.dp,
                        iconSize = 24.dp,
                        enabled = sessionState.queueState.currentIndex < sessionState.queueState.items.lastIndex,
                        onClick = onSkipNext,
                    )
                }
            }
        }

        // Right pane: 32-inch TV large lyrics viewport + compact source attribution & 32dp switcher button
        PersistentReadOnlyLyricsPanel(
            lyrics = sessionState.lyrics,
            isLoading = sessionState.lyricsLoading,
            selectedProvider = sessionState.selectedLyricsProvider,
            activeLineIndex = sessionState.activeLyricIndex,
            positionMs = sessionState.positionMs,
            onSwitchLyricsProvider = onSwitchLyricsProvider,
            modifier =
                Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .background(AlunaraPalette.Background)
                    .padding(start = 20.dp, end = 36.dp, top = 26.dp, bottom = 28.dp),
        )
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
    val bgColor by animateColorAsState(
        targetValue = if (focused) AlunaraPalette.Background.copy(alpha = 0.72f) else Color.Transparent,
        animationSpec = tween(150),
        label = "seek_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) AlunaraPalette.FocusOutline else Color.Transparent,
        animationSpec = tween(150),
        label = "seek_border",
    )
    val barThicknessFactor by animateFloatAsState(
        targetValue = if (focused) 6f else 4f,
        animationSpec = tween(150),
        label = "seek_bar_h",
    )
    val thumbRadiusDp by animateFloatAsState(
        targetValue = if (focused) 6.5f else 5f,
        animationSpec = tween(150),
        label = "seek_thumb",
    )
    val hintAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(150),
        label = "seek_hint_alpha",
    )
    val trackBg = AlunaraPalette.SecondarySurface
    val trackFill = AlunaraPalette.PrimaryAccent
    val thumbColor by animateColorAsState(
        targetValue = if (focused) AlunaraPalette.FocusOutline else AlunaraPalette.PrimaryAccent,
        animationSpec = tween(150),
        label = "seek_thumb_color",
    )

    Column(
        modifier =
            modifier
                .background(bgColor, shape)
                .border(BorderStroke(AlunaraDimens.FocusBorderWidth, borderColor), shape)
                .onFocusChanged { focused = it.isFocused }
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
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(14.dp),
        ) {
            val barHeight = barThicknessFactor.dp.toPx()
            val barTop = (size.height - barHeight) / 2f
            val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(0f, barTop),
                size = Size(size.width, barHeight),
                cornerRadius = corner,
            )
            val activeWidth = (size.width * fraction).coerceIn(0f, size.width)
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = trackFill,
                    topLeft = Offset(0f, barTop),
                    size = Size(activeWidth, barHeight),
                    cornerRadius = corner,
                )
            }
            val thumbRadius = thumbRadiusDp.dp.toPx()
            val thumbX = activeWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDurationMs(positionMs),
                color = AlunaraPalette.PrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.seek_bar_label),
                color = AlunaraPalette.FocusOutline,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(hintAlpha),
            )
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
    selectedProvider: LyricsProviderId?,
    activeLineIndex: Int,
    positionMs: Long,
    onSwitchLyricsProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProvider =
        selectedProvider
            ?: when (lyrics) {
                is LyricsDocument.Synced -> lyrics.provider
                is LyricsDocument.Plain -> lyrics.provider
                is LyricsDocument.Unavailable -> null
            }

    val modeLabel =
        when {
            lyrics is LyricsDocument.Synced && lyrics.isWordSynced -> stringResource(R.string.lyrics_mode_word)
            lyrics is LyricsDocument.Synced -> stringResource(R.string.lyrics_mode_line)
            lyrics is LyricsDocument.Plain -> stringResource(R.string.lyrics_mode_plain)
            else -> null
        }

    val sourceCaption =
        activeProvider?.let { provider ->
            val base = stringResource(R.string.lyrics_source_format, providerDisplayName(provider))
            if (modeLabel != null) "$base · $modeLabel" else base
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = AlunaraPalette.PrimaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.lyrics_heading),
                        color = AlunaraPalette.PrimaryAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (sourceCaption != null) {
                    Text(
                        text = sourceCaption,
                        color = AlunaraPalette.MutedText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            TvIconButton(
                icon = Icons.Rounded.SwapHoriz,
                contentDescription = stringResource(R.string.lyrics_switch_provider),
                size = 32.dp,
                iconSize = 18.dp,
                onClick = onSwitchLyricsProvider,
            )
        }

        // Strictly read-only lyrics viewport tailored for 32-inch 10-foot viewing distance
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusProperties { canFocus = false },
        ) {
            when {
                isLoading -> {
                    Text(
                        text = stringResource(R.string.lyrics_loading),
                        color = AlunaraPalette.MutedText,
                        fontSize = 19.sp,
                    )
                }

                lyrics is LyricsDocument.Synced -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(activeLineIndex) {
                        if (activeLineIndex >= 0) {
                            listState.animateScrollToItem((activeLineIndex - 1).coerceAtLeast(0))
                        }
                    }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = false },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(lyrics.lines) { idx, line ->
                            val isActive = idx == activeLineIndex
                            if (isActive && line.hasWordSync) {
                                val activeWordIdx = LyricsSyncResolver.findActiveWordIndex(line.words, positionMs)
                                val annotated =
                                    buildAnnotatedString {
                                        line.words.forEachIndexed { wIdx, word ->
                                            val style =
                                                when {
                                                    wIdx == activeWordIdx ->
                                                        SpanStyle(
                                                            color = AlunaraPalette.FocusOutline,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    wIdx < activeWordIdx ->
                                                        SpanStyle(
                                                            color = AlunaraPalette.PrimaryText,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    else ->
                                                        SpanStyle(
                                                            color = AlunaraPalette.MutedText.copy(alpha = 0.50f),
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                }
                                            withStyle(style) {
                                                append(word.text)
                                            }
                                        }
                                    }
                                Text(
                                    text = annotated,
                                    fontSize = 26.sp,
                                    lineHeight = 36.sp,
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    color = if (isActive) AlunaraPalette.PrimaryText else AlunaraPalette.DimmedLyrics,
                                    fontSize = if (isActive) 26.sp else 19.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = if (isActive) 36.sp else 28.sp,
                                )
                            }
                        }
                    }
                }

                lyrics is LyricsDocument.Plain -> {
                    val plainLines = remember(lyrics.text) { lyrics.text.lines() }
                    LazyColumn(
                        userScrollEnabled = false,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = false },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(plainLines) { _, line ->
                            Text(
                                text = line,
                                color = AlunaraPalette.PrimaryText.copy(alpha = 0.88f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 30.sp,
                            )
                        }
                    }
                }

                else -> {
                    val emptyMsg =
                        selectedProvider?.let {
                            stringResource(R.string.lyrics_unavailable_provider, providerDisplayName(it))
                        } ?: stringResource(R.string.lyrics_unavailable_generic)
                    Text(
                        text = emptyMsg,
                        color = AlunaraPalette.MutedText,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                    )
                }
            }
        }
    }
}

private fun providerDisplayName(provider: LyricsProviderId): String =
    when (provider) {
        LyricsProviderId.SIMPMUSIC -> "SimpMusic"
        LyricsProviderId.LRCLIB -> "LrcLib"
        LyricsProviderId.KUGOU -> "KuGou"
    }
