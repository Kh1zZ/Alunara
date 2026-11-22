package com.alunara.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.components.TvArtworkContentScale
import com.alunara.app.ui.components.TvBackdropBlurTransformation
import com.alunara.app.ui.components.TvFocusableButton
import com.alunara.app.ui.components.TvIconButton
import com.alunara.app.ui.components.TvSongRow
import com.alunara.app.ui.components.formatDurationMs
import com.alunara.app.ui.components.safeRequestFocus
import com.alunara.app.ui.theme.AlunaraColorTokens
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.media.ArtworkTier
import com.alunara.core.domain.media.ArtworkUrlResolver
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.QueueFilterChip
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.SyncedLyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private const val IDLE_DIM_TIMEOUT_MS = 10_000L
private val SmoothDecelerateEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private enum class NowPlayingRightPanelMode {
    LYRICS,
    QUEUE,
}

@Immutable
private data class StableSyncedLyricLine(
    val value: SyncedLyricLine,
)

@Composable
fun NowPlayingScreen(
    sessionState: PlaybackSessionUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onStartRadio: () -> Unit,
    onSwitchLyricsProvider: () -> Unit,
    modifier: Modifier = Modifier,
    positionMsFlow: StateFlow<Long>? = null,
    onAdjustLyricsSync: (Long) -> Unit = {},
    onResetLyricsSync: () -> Unit = {},
    onToggleRomanizedLyrics: () -> Unit = {},
    onPlayQueueAt: (Int) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onRefreshRadio: () -> Unit = onStartRadio,
    onSelectQueueChip: (QueueFilterChip?) -> Unit = {},
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val context = LocalContext.current
    val track = sessionState.currentTrack
    if (track == null) {
        Box(
            modifier = modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.queue_empty),
                color = colors.mutedText,
                fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
            )
        }
        return
    }

    val playButtonRequester = remember { FocusRequester() }
    var rightPanelMode by remember { mutableStateOf(NowPlayingRightPanelMode.LYRICS) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleDimmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        playButtonRequester.safeRequestFocus()
    }

    LaunchedEffect(lastInteractionMs, sessionState.status) {
        isIdleDimmed = false
        if (sessionState.status == PlaybackStatus.PLAYING) {
            delay(IDLE_DIM_TIMEOUT_MS)
            isIdleDimmed = true
        }
    }

    val rawThumb = track.thumbnailUrl
    val heroUrl = remember(rawThumb) { ArtworkUrlResolver.resolve(rawThumb, ArtworkTier.HERO) }
    var useFallbackArt by remember(rawThumb) { mutableStateOf(false) }
    val activeArtUrl = if (useFallbackArt && !rawThumb.isNullOrBlank()) rawThumb else heroUrl

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .onPreviewKeyEvent {
                    // Track remote activity for UX-17 idle dimming without consuming navigation
                    lastInteractionMs = System.currentTimeMillis()
                    false
                },
    ) {
        // Layer 1: Full-screen softened & darkened cover artwork spanning behind both left panel and lyrics
        if (!activeArtUrl.isNullOrBlank() && !prefs.highContrast) {
            val backdropRequest =
                remember(activeArtUrl, prefs.reduceMotion) {
                    ImageRequest
                        .Builder(context)
                        .data(activeArtUrl)
                        .size(256)
                        .allowHardware(false)
                        .transformations(TvBackdropBlurTransformation)
                        .crossfade(!prefs.reduceMotion)
                        .build()
                }
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                contentScale = TvArtworkContentScale,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to colors.background.copy(alpha = 0.60f),
                                0.45f to colors.background.copy(alpha = 0.68f),
                                1.0f to colors.background.copy(alpha = 0.78f),
                            ),
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to colors.background.copy(alpha = 0.16f),
                                0.65f to colors.background.copy(alpha = 0.36f),
                                1.0f to colors.background.copy(alpha = 0.76f),
                            ),
                        ),
            )
        }

        // Layer 2: Two-column TV foreground over the blurred full-screen artwork backdrop
        Row(modifier = Modifier.fillMaxSize()) {
            // Left pane: crisp unblurred album card + track metadata + progress + transport controls
            Column(
                modifier =
                    Modifier
                        .weight(0.92f)
                        .fillMaxHeight()
                        .padding(
                            start = AlunaraDimens.OverscanHorizontal,
                            end = 28.dp,
                            top = AlunaraDimens.OverscanVertical,
                            bottom = 30.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val cardShape = RoundedCornerShape(18.dp)
                Box(
                    modifier =
                        Modifier
                            .size(212.dp)
                            .clip(cardShape)
                            .background(colors.secondarySurface, cardShape)
                            .border(1.dp, colors.primaryAccent.copy(alpha = 0.25f), cardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!activeArtUrl.isNullOrBlank()) {
                        val heroRequest =
                            remember(activeArtUrl, prefs.reduceMotion) {
                                ImageRequest
                                    .Builder(context)
                                    .data(activeArtUrl)
                                    .size(ArtworkTier.HERO.maxPx)
                                    .crossfade(!prefs.reduceMotion)
                                    .build()
                            }
                        AsyncImage(
                            model = heroRequest,
                            contentDescription = track.title,
                            contentScale = TvArtworkContentScale,
                            onError = {
                                if (!useFallbackArt && activeArtUrl != rawThumb && !rawThumb.isNullOrBlank()) {
                                    useFallbackArt = true
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = track.title,
                            tint = colors.primaryAccent.copy(alpha = 0.55f),
                            modifier = Modifier.size(72.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
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
                        color = colors.primaryText,
                        fontSize = AlunaraTypography.headline(prefs.textScale),
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
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
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
                                colors.errorText
                            } else {
                                colors.primaryAccent
                            }
                        val showCodec =
                            sessionState.status == PlaybackStatus.PLAYING ||
                                sessionState.status == PlaybackStatus.PAUSED ||
                                sessionState.status == PlaybackStatus.BUFFERING
                        val statusWithCodec =
                            if (showCodec && !sessionState.activeStreamCodecLabel.isNullOrBlank()) {
                                "$statusLabel · ${sessionState.activeStreamCodecLabel}"
                            } else {
                                statusLabel
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
                                text = statusWithCodec,
                                color = statusColor,
                                fontSize = AlunaraTypography.label(prefs.textScale),
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IdleDimmableControl(isIdleDimmed = isIdleDimmed) {
                        TvIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.action_prev),
                            size = AlunaraDimens.ControlHeight,
                            iconSize = 24.dp,
                            enabled = sessionState.queueState.items.isNotEmpty(),
                            onClick = onSkipPrevious,
                        )
                    }
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
                    IdleDimmableControl(isIdleDimmed = isIdleDimmed) {
                        TvIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.action_next),
                            size = AlunaraDimens.ControlHeight,
                            iconSize = 24.dp,
                            enabled = sessionState.queueState.currentIndex < sessionState.queueState.items.lastIndex,
                            onClick = onSkipNext,
                        )
                    }
                    IdleDimmableControl(isIdleDimmed = isIdleDimmed) {
                        TvIconButton(
                            icon = if (sessionState.isCurrentTrackFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription =
                                if (sessionState.isCurrentTrackFavorite) {
                                    stringResource(R.string.action_unfavorite)
                                } else {
                                    stringResource(R.string.action_favorite)
                                },
                            size = AlunaraDimens.ControlHeight,
                            iconSize = 22.dp,
                            highlighted = sessionState.isCurrentTrackFavorite,
                            onClick = onToggleFavorite,
                        )
                    }
                    IdleDimmableControl(isIdleDimmed = isIdleDimmed) {
                        TvIconButton(
                            icon = Icons.Rounded.Radio,
                            contentDescription = stringResource(R.string.action_start_radio),
                            size = AlunaraDimens.ControlHeight,
                            iconSize = 22.dp,
                            highlighted = sessionState.radioLoading,
                            onClick = onStartRadio,
                        )
                    }
                }
            }

            // Right pane: toggleable between read-only Lyrics viewport and YouTube Algorithm Queue + chips over blurred artwork
            Column(
                modifier =
                    Modifier
                        .weight(1.08f)
                    .fillMaxHeight()
                    .padding(
                        start = 20.dp,
                        end = AlunaraDimens.OverscanHorizontal,
                        top = AlunaraDimens.OverscanVertical,
                        bottom = AlunaraDimens.OverscanVertical,
                    ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvFocusableButton(
                    text = stringResource(R.string.now_playing_tab_lyrics),
                    icon = Icons.Rounded.Lyrics,
                    highlighted = rightPanelMode == NowPlayingRightPanelMode.LYRICS,
                    onClick = { rightPanelMode = NowPlayingRightPanelMode.LYRICS },
                )
                TvFocusableButton(
                    text = stringResource(R.string.now_playing_tab_queue, sessionState.queueState.items.size),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    highlighted = rightPanelMode == NowPlayingRightPanelMode.QUEUE,
                    onClick = { rightPanelMode = NowPlayingRightPanelMode.QUEUE },
                )
                if (sessionState.hasRomanizedLyrics && rightPanelMode == NowPlayingRightPanelMode.LYRICS) {
                    TvFocusableButton(
                        text =
                            if (sessionState.showRomanizedLyrics) {
                                stringResource(R.string.lyrics_toggle_romaji_active)
                            } else {
                                stringResource(R.string.lyrics_toggle_romaji)
                            },
                        icon = Icons.Rounded.Translate,
                        highlighted = sessionState.showRomanizedLyrics,
                        onClick = onToggleRomanizedLyrics,
                    )
                }
            }

            Crossfade(
                targetState = rightPanelMode,
                animationSpec = tween(durationMillis = prefs.navAnimMs),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "now_playing_right_panel",
            ) { mode ->
                when (mode) {
                    NowPlayingRightPanelMode.LYRICS ->
                        PersistentReadOnlyLyricsPanel(
                            lyrics = sessionState.lyrics,
                            isLoading = sessionState.lyricsLoading,
                            selectedProvider = sessionState.selectedLyricsProvider,
                            activeLineIndex = sessionState.activeLyricIndex,
                            positionMsFlow = positionMsFlow,
                            fallbackPositionMs = if (positionMsFlow != null) 0L else sessionState.positionMs,
                            lyricsSyncOffsetMs = sessionState.lyricsSyncOffsetMs,
                            showRomanizedLyrics = sessionState.showRomanizedLyrics,
                            hasPartialKanjiNotice = sessionState.hasPartialKanjiNotice,
                            queueCount = sessionState.queueState.items.size,
                            onSwitchLyricsProvider = onSwitchLyricsProvider,
                            onAdjustLyricsSync = onAdjustLyricsSync,
                            onResetLyricsSync = onResetLyricsSync,
                            onOpenQueue = { rightPanelMode = NowPlayingRightPanelMode.QUEUE },
                            modifier = Modifier.fillMaxSize(),
                        )

                    NowPlayingRightPanelMode.QUEUE ->
                        NowPlayingQueuePanel(
                            queueState = sessionState.queueState,
                            queueChips = sessionState.queueChips,
                            activeChipLabel = sessionState.activeQueueChipLabel,
                            radioLoading = sessionState.radioLoading,
                            onPlayQueueAt = onPlayQueueAt,
                            onMoveQueueItem = onMoveQueueItem,
                            onRemoveQueueItem = onRemoveQueueItem,
                            onClearQueue = onClearQueue,
                            onRefreshRadio = onRefreshRadio,
                            onSelectQueueChip = onSelectQueueChip,
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
        }
        }
    }
}

@Composable
private fun IdleDimmableControl(
    isIdleDimmed: Boolean,
    content: @Composable () -> Unit,
) {
    val prefs = LocalAlunaraDisplayPrefs.current
    var hasFocus by remember { mutableStateOf(false) }
    val targetAlpha = if (isIdleDimmed && !hasFocus) 0.55f else 1.0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(prefs.focusAnimMs),
        label = "idle_ctrl_alpha",
    )
    Box(
        modifier =
            Modifier
                .alpha(alpha)
                .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        content()
    }
}

@Composable
private fun NowPlayingQueuePanel(
    queueState: QueueState,
    queueChips: List<QueueFilterChip>,
    activeChipLabel: String?,
    radioLoading: Boolean,
    onPlayQueueAt: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onRefreshRadio: () -> Unit,
    onSelectQueueChip: (QueueFilterChip?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var confirmingClear by remember { mutableStateOf(false) }
    val cancelConfirmRequester = remember { FocusRequester() }

    LaunchedEffect(confirmingClear) {
        if (confirmingClear) {
            withFrameNanos { }
            cancelConfirmRequester.safeRequestFocus()
        }
    }
    LaunchedEffect(queueState.items.isEmpty()) {
        if (queueState.items.isEmpty()) {
            confirmingClear = false
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.queue_panel_title),
                color = colors.primaryAccent,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.queue_refresh_radio),
                    size = AlunaraDimens.QueueActionButtonSize,
                    iconSize = 18.dp,
                    highlighted = radioLoading,
                    onClick = onRefreshRadio,
                )
                if (queueState.items.isNotEmpty() && !confirmingClear) {
                    TvIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.clear_queue),
                        size = AlunaraDimens.QueueActionButtonSize,
                        iconSize = 18.dp,
                        onClick = { confirmingClear = true },
                    )
                }
            }
        }

        if (confirmingClear) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.errorSurface, RoundedCornerShape(AlunaraDimens.RadiusControl))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.clear_queue_confirm_prompt),
                    color = colors.errorText,
                    fontSize = AlunaraTypography.label(prefs.textScale),
                    modifier = Modifier.weight(1f),
                )
                TvFocusableButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmingClear = false },
                    modifier = Modifier.focusRequester(cancelConfirmRequester),
                )
                TvFocusableButton(
                    text = stringResource(R.string.confirm_delete_action),
                    highlighted = true,
                    onClick = {
                        confirmingClear = false
                        onClearQueue()
                    },
                )
            }
        }

        if (queueChips.isNotEmpty()) {
            val hasResetChip =
                queueChips.any {
                    it.label.equals("All", ignoreCase = true) ||
                        it.label.equals("Semua", ignoreCase = true)
                }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasResetChip) {
                    item(key = "chip-all") {
                        TvFocusableButton(
                            text = stringResource(R.string.queue_chip_all),
                            highlighted = activeChipLabel == null,
                            onClick = { onSelectQueueChip(null) },
                        )
                    }
                }
                items(queueChips, key = { "chip-${it.label}-${it.params.orEmpty()}" }) { chip ->
                    TvFocusableButton(
                        text = chip.label,
                        highlighted = activeChipLabel == chip.label || (activeChipLabel == null && chip.selected),
                        onClick = { onSelectQueueChip(chip) },
                    )
                }
            }
        }

        if (queueState.items.isEmpty()) {
            Text(
                text = stringResource(R.string.queue_empty),
                color = colors.mutedText,
                fontSize = AlunaraTypography.rowTitle(prefs.textScale),
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TvSongRow(
                            track = item.track,
                            isActive = index == queueState.currentIndex,
                            onPlayClick = { onPlayQueueAt(index) },
                            modifier = Modifier.weight(1f),
                        )
                        TvIconButton(
                            icon = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.move_up),
                            size = AlunaraDimens.QueueActionButtonSize,
                            iconSize = 18.dp,
                            enabled = index > 0,
                            onClick = { onMoveQueueItem(index, index - 1) },
                        )
                        TvIconButton(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.move_down),
                            size = AlunaraDimens.QueueActionButtonSize,
                            iconSize = 18.dp,
                            enabled = index < queueState.items.lastIndex,
                            onClick = { onMoveQueueItem(index, index + 1) },
                        )
                        TvIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.remove_item),
                            size = AlunaraDimens.QueueActionButtonSize,
                            iconSize = 18.dp,
                            onClick = { onRemoveQueueItem(index) },
                        )
                    }
                }
            }
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
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val shape = RoundedCornerShape(10.dp)
    val bgColor by animateColorAsState(
        targetValue = if (focused) colors.background.copy(alpha = 0.78f) else Color.Transparent,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.focusRingStrong else Color.Transparent,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_border",
    )
    val barThicknessFactor by animateFloatAsState(
        targetValue = if (focused) 6f else 4f,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_bar_h",
    )
    val thumbRadiusDp by animateFloatAsState(
        targetValue = if (focused) 6.5f else 5f,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_thumb",
    )
    val hintAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_hint_alpha",
    )
    val trackBg = colors.secondarySurface
    val trackFill = colors.primaryAccent
    val thumbColor by animateColorAsState(
        targetValue = if (focused) colors.focusOutline else colors.primaryAccent,
        animationSpec = tween(prefs.focusAnimMs),
        label = "seek_thumb_color",
    )
    val progressDesc = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}"

    Column(
        modifier =
            modifier
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics {
                    stateDescription = progressDesc
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                }
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
                color = colors.primaryText,
                fontSize = AlunaraTypography.caption(prefs.textScale),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.seek_bar_label),
                color = colors.focusOutline,
                fontSize = AlunaraTypography.caption(prefs.textScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(hintAlpha),
            )
            Text(
                text = formatDurationMs(durationMs),
                color = colors.mutedText,
                fontSize = AlunaraTypography.caption(prefs.textScale),
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
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    lyricsSyncOffsetMs: Long,
    showRomanizedLyrics: Boolean,
    hasPartialKanjiNotice: Boolean,
    queueCount: Int,
    onSwitchLyricsProvider: () -> Unit,
    onAdjustLyricsSync: (Long) -> Unit,
    onResetLyricsSync: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
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

    val partialKanjiSuffix =
        if (showRomanizedLyrics && hasPartialKanjiNotice) {
            " · ${stringResource(R.string.lyrics_partial_kanji_note)}"
        } else {
            ""
        }

    val sourceCaption =
        activeProvider?.let { provider ->
            val base = stringResource(R.string.lyrics_source_format, providerDisplayName(provider))
            if (modeLabel != null) "$base · $modeLabel$partialKanjiSuffix" else "$base$partialKanjiSuffix"
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = sourceCaption ?: stringResource(R.string.lyrics_switch_provider),
                color = colors.mutedText,
                fontSize = AlunaraTypography.label(prefs.textScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
            )

            if (lyrics is LyricsDocument.Synced) {
                CompactLyricSyncControls(
                    offsetMs = lyricsSyncOffsetMs,
                    onStep = onAdjustLyricsSync,
                    onReset = onResetLyricsSync,
                )
            }

            TvIconButton(
                icon = Icons.Rounded.SwapHoriz,
                contentDescription = stringResource(R.string.lyrics_switch_provider),
                size = AlunaraDimens.QueueActionButtonSize,
                iconSize = 18.dp,
                onClick = onSwitchLyricsProvider,
            )
        }

        // Read-only ArchiveTune-animated lyrics viewport tailored for 1 GB Android TV
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            when {
                isLoading -> {
                    Text(
                        text = stringResource(R.string.lyrics_loading),
                        color = colors.mutedText,
                        fontSize = AlunaraTypography.lyricInactive(prefs.textScale),
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                }

                lyrics is LyricsDocument.Synced -> {
                    val stableLines = remember(lyrics) { lyrics.lines.map(::StableSyncedLyricLine) }
                    val listState = rememberLazyListState()
                    LaunchedEffect(activeLineIndex, prefs.reduceMotion) {
                        if (activeLineIndex >= 0) {
                            val target = (activeLineIndex - 1).coerceAtLeast(0)
                            if (prefs.reduceMotion) {
                                listState.scrollToItem(target)
                            } else {
                                listState.animateScrollToItem(target)
                            }
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
                        itemsIndexed(
                            items = stableLines,
                            key = { idx, line -> "${idx}_${line.value.startTimeMs}" },
                        ) { idx, line ->
                            val isActive = idx == activeLineIndex
                            val distance = if (activeLineIndex < 0) 2 else abs(idx - activeLineIndex)
                            val nextLineStartMs =
                                stableLines
                                    .getOrNull(idx + 1)
                                    ?.value
                                    ?.startTimeMs
                                    ?: (line.value.startTimeMs + 5_000L)
                            SyncedLyricRowItem(
                                line = line,
                                nextLineStartMs = nextLineStartMs,
                                distanceFromActive = distance,
                                isActive = isActive,
                                showRomanized = showRomanizedLyrics,
                                positionMsFlow = positionMsFlow,
                                fallbackPositionMs = fallbackPositionMs,
                                lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                                reduceMotion = prefs.reduceMotion,
                                colors = colors,
                                textScale = prefs.textScale,
                            )
                        }
                    }
                }

                lyrics is LyricsDocument.Plain -> {
                    val plainLines = remember(lyrics.text) { lyrics.text.lines() }
                    val romanLines =
                        remember(lyrics.romanizedText) {
                            lyrics.romanizedText?.lines()
                        }
                    val plainFontSize = AlunaraTypography.lyricPlain(prefs.textScale)
                    LazyColumn(
                        userScrollEnabled = false,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = false },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(plainLines) { idx, line ->
                            val roman = romanLines?.getOrNull(idx)
                            if (showRomanizedLyrics && !roman.isNullOrBlank() && roman != line) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = roman,
                                        color = colors.primaryText.copy(alpha = 0.94f),
                                        fontSize = plainFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = plainFontSize * 1.38f,
                                    )
                                    Text(
                                        text = line,
                                        color = colors.mutedText.copy(alpha = 0.68f),
                                        fontSize = plainFontSize * 0.74f,
                                        fontWeight = FontWeight.Normal,
                                        lineHeight = plainFontSize * 1.05f,
                                    )
                                }
                            } else {
                                Text(
                                    text = line,
                                    color = colors.primaryText.copy(alpha = 0.90f),
                                    fontSize = plainFontSize,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = plainFontSize * 1.48f,
                                )
                            }
                        }
                    }
                }

                else -> {
                    val emptyMsg =
                        selectedProvider?.let {
                            stringResource(R.string.lyrics_unavailable_provider, providerDisplayName(it))
                        } ?: stringResource(R.string.lyrics_unavailable_generic)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surface.copy(alpha = 0.72f), RoundedCornerShape(AlunaraDimens.RadiusCard))
                                .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = emptyMsg,
                            color = colors.mutedText,
                            fontSize = AlunaraTypography.sectionTitle(prefs.textScale),
                            lineHeight = AlunaraTypography.sectionTitle(prefs.textScale) * 1.4f,
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvFocusableButton(
                                text = stringResource(R.string.open_queue_action, queueCount),
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                highlighted = true,
                                onClick = onOpenQueue,
                            )
                            TvFocusableButton(
                                text = stringResource(R.string.lyrics_switch_provider),
                                icon = Icons.Rounded.SwapHoriz,
                                onClick = onSwitchLyricsProvider,
                            )
                        }
                    }
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

@Composable
private fun SyncedLyricRowItem(
    line: StableSyncedLyricLine,
    nextLineStartMs: Long,
    distanceFromActive: Int,
    isActive: Boolean,
    showRomanized: Boolean,
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    lyricsSyncOffsetMs: Long,
    reduceMotion: Boolean,
    colors: AlunaraColorTokens,
    textScale: Float,
) {
    val density = LocalDensity.current
    val targetAlpha =
        when (distanceFromActive) {
            0 -> 1.0f
            1 -> 0.54f
            2 -> 0.34f
            else -> 0.20f
        }

    // Single Animatable per row drives alpha, scale, and lift together via GPU graphicsLayer
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec =
            if (reduceMotion) {
                snap()
            } else {
                tween(durationMillis = 360, easing = SmoothDecelerateEasing)
            },
        label = "lyric_row_alpha",
    )

    // Constant base font size prevents text relayout when active line switches; emphasis uses GPU graphicsLayer scale
    val baseFontSize = AlunaraTypography.lyricActive(textScale) * 0.94f
    val lineHeight = baseFontSize * 1.40f
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .padding(end = 18.dp)
            .graphicsLayer {
                val activeProgress = ((animatedAlpha - 0.54f) / 0.46f).coerceIn(0f, 1f)
                val scale = if (reduceMotion) 1f else 1f + 0.04f * activeProgress
                val liftDp = if (reduceMotion) 0f else -2f * activeProgress
                alpha = animatedAlpha
                scaleX = scale
                scaleY = scale
                translationY = liftDp * density.density
                transformOrigin = TransformOrigin(0f, 0.5f)
            }

    val romanized = line.value.romanizedText
    val hasDualLine = showRomanized && !romanized.isNullOrBlank()
    val hasRomanWords = hasDualLine && line.value.words.any { !it.romanizedText.isNullOrBlank() }

    if (hasDualLine) {
        val primaryLineHeight = baseFontSize * 1.32f
        val subFontSize = baseFontSize * 0.72f
        val subLineHeight = subFontSize * 1.30f
        Column(
            modifier = rowModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (isActive && line.value.hasWordSync && hasRomanWords) {
                ActiveWordSyncedLyricText(
                    line = line,
                    nextLineStartMs = nextLineStartMs,
                    positionMsFlow = positionMsFlow,
                    fallbackPositionMs = fallbackPositionMs,
                    lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                    reduceMotion = reduceMotion,
                    useRomanizedWords = true,
                    baseFontSize = baseFontSize,
                    lineHeight = primaryLineHeight,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = romanized ?: line.value.text,
                    color = colors.primaryText,
                    fontSize = baseFontSize,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    lineHeight = primaryLineHeight,
                )
            }
            Text(
                text = line.value.text,
                color = if (isActive) colors.primaryText.copy(alpha = 0.62f) else colors.mutedText.copy(alpha = 0.68f),
                fontSize = subFontSize,
                fontWeight = FontWeight.Normal,
                lineHeight = subLineHeight,
            )
        }
    } else if (isActive && line.value.hasWordSync) {
        ActiveWordSyncedLyricText(
            line = line,
            nextLineStartMs = nextLineStartMs,
            positionMsFlow = positionMsFlow,
            fallbackPositionMs = fallbackPositionMs,
            lyricsSyncOffsetMs = lyricsSyncOffsetMs,
            reduceMotion = reduceMotion,
            useRomanizedWords = false,
            baseFontSize = baseFontSize,
            lineHeight = lineHeight,
            colors = colors,
            modifier = rowModifier,
        )
    } else {
        Text(
            text = line.value.text,
            color = colors.primaryText,
            fontSize = baseFontSize,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            lineHeight = lineHeight,
            modifier = rowModifier,
        )
    }
}

@Composable
private fun ActiveWordSyncedLyricText(
    line: StableSyncedLyricLine,
    nextLineStartMs: Long,
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    lyricsSyncOffsetMs: Long,
    reduceMotion: Boolean,
    useRomanizedWords: Boolean,
    baseFontSize: TextUnit,
    lineHeight: TextUnit,
    colors: AlunaraColorTokens,
    modifier: Modifier = Modifier,
) {
    val rawPosMs =
        if (positionMsFlow != null) {
            positionMsFlow.collectAsStateWithLifecycle().value
        } else {
            fallbackPositionMs
        }
    val posMs = (rawPosMs + lyricsSyncOffsetMs).coerceAtLeast(0L)
    val words = line.value.words
    var activeWordIndex = -1
    var passedWordCount = 0
    var quantizedSmoothStep = 0
    for (wIdx in words.indices) {
        val wordStartMs = words[wIdx].startTimeMs
        val wordEndMs =
            words
                .getOrNull(wIdx + 1)
                ?.startTimeMs
                ?.coerceAtLeast(wordStartMs + 1L)
                ?: minOf(nextLineStartMs, wordStartMs + 650L).coerceAtLeast(wordStartMs + 1L)
        if (posMs > wordEndMs) {
            passedWordCount = wIdx + 1
        } else if (posMs >= wordStartMs) {
            activeWordIndex = wIdx
            val linear =
                ((posMs - wordStartMs).toFloat() / (wordEndMs - wordStartMs).coerceAtLeast(1L))
                    .coerceIn(0f, 1f)
            val smooth =
                if (reduceMotion) {
                    1f
                } else {
                    linear * linear * (3f - 2f * linear)
                }
            quantizedSmoothStep = if (reduceMotion) 4 else (smooth * 4f).toInt().coerceIn(0, 4)
            break
        } else {
            break
        }
    }

    val annotated =
        remember(line, activeWordIndex, passedWordCount, quantizedSmoothStep, reduceMotion, useRomanizedWords, colors) {
            val smoothProgress = quantizedSmoothStep / 4f
            buildAnnotatedString {
                words.forEachIndexed { wIdx, word ->
                    val wordColor =
                        when {
                            wIdx < passedWordCount -> colors.primaryText
                            wIdx == activeWordIndex ->
                                lerp(colors.primaryText.copy(alpha = 0.52f), colors.focusOutline, smoothProgress)
                            else -> colors.mutedText.copy(alpha = 0.38f)
                        }
                    withStyle(
                        SpanStyle(
                            color = wordColor,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        val wordText =
                            if (useRomanizedWords && !word.romanizedText.isNullOrBlank()) {
                                word.romanizedText
                            } else {
                                word.text
                            }
                        append(wordText)
                    }
                }
            }
        }

    Text(
        text = annotated,
        fontSize = baseFontSize,
        lineHeight = lineHeight,
        modifier = modifier,
    )
}

@Composable
private fun CompactLyricSyncControls(
    offsetMs: Long,
    onStep: (Long) -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var resetFocused by remember { mutableStateOf(false) }
    val offsetLabel =
        when {
            offsetMs == 0L -> stringResource(R.string.lyrics_sync_zero)
            offsetMs > 0L -> "+${offsetMs} ms"
            else -> "${offsetMs} ms"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LyricSyncStepButton(
            label = stringResource(R.string.lyrics_sync_minus),
            accessibilityLabel = stringResource(R.string.lyrics_sync_minus_cd),
            deltaMs = -10L,
            onStep = onStep,
        )
        Text(
            text = offsetLabel,
            color = if (offsetMs != 0L) colors.primaryAccent else colors.mutedText,
            fontSize = AlunaraTypography.label(prefs.textScale),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.focusProperties { canFocus = false },
        )
        LyricSyncStepButton(
            label = stringResource(R.string.lyrics_sync_plus),
            accessibilityLabel = stringResource(R.string.lyrics_sync_plus_cd),
            deltaMs = 10L,
            onStep = onStep,
        )
        val resetShape = RoundedCornerShape(8.dp)
        val resetBg by animateColorAsState(
            targetValue =
                when {
                    resetFocused -> colors.focusedButtonBg
                    offsetMs != 0L -> colors.secondarySurface
                    else -> colors.surface
                },
            animationSpec = tween(prefs.focusAnimMs),
            label = "lyric_sync_reset_bg",
        )
        val resetBorder by animateColorAsState(
            targetValue =
                when {
                    resetFocused -> colors.focusRingStrong
                    offsetMs != 0L -> colors.primaryAccent.copy(alpha = 0.65f)
                    else -> colors.secondarySurface
                },
            animationSpec = tween(prefs.focusAnimMs),
            label = "lyric_sync_reset_border",
        )
        val resetCd = stringResource(R.string.lyrics_sync_reset_cd)
        Box(
            modifier =
                Modifier
                    .height(AlunaraDimens.QueueActionButtonSize)
                    .clip(resetShape)
                    .background(resetBg, resetShape)
                    .border(BorderStroke(colors.focusBorderWidth, resetBorder), resetShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = resetCd
                    }
                    .onFocusChanged { resetFocused = it.isFocused }
                    .clickable(onClick = onReset)
                    .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.lyrics_sync_reset),
                color =
                    when {
                        resetFocused -> colors.focusedButtonContent
                        offsetMs != 0L -> colors.primaryAccent
                        else -> colors.primaryText
                    },
                fontSize = AlunaraTypography.label(prefs.textScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LyricSyncStepButton(
    label: String,
    accessibilityLabel: String,
    deltaMs: Long,
    onStep: (Long) -> Unit,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    var didLongRepeat by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    val bg by animateColorAsState(
        targetValue = if (focused) colors.focusedButtonBg else colors.secondarySurface,
        animationSpec = tween(prefs.focusAnimMs),
        label = "lyric_step_bg",
    )
    val border by animateColorAsState(
        targetValue = if (focused) colors.focusRingStrong else colors.surface,
        animationSpec = tween(prefs.focusAnimMs),
        label = "lyric_step_border",
    )

    Box(
        modifier =
            Modifier
                .size(AlunaraDimens.QueueActionButtonSize)
                .clip(shape)
                .background(bg, shape)
                .border(BorderStroke(colors.focusBorderWidth, border), shape)
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    val isSelectKey =
                        keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                    if (!isSelectKey) return@onPreviewKeyEvent false
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        if (keyEvent.nativeKeyEvent.repeatCount > 0) {
                            didLongRepeat = true
                            onStep(deltaMs * 10L)
                            true
                        } else {
                            didLongRepeat = false
                            false
                        }
                    } else {
                        false
                    }
                }
                .clickable {
                    if (didLongRepeat) {
                        didLongRepeat = false
                    } else {
                        onStep(deltaMs)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) colors.focusedButtonContent else colors.primaryText,
            fontSize = AlunaraTypography.rowTitle(prefs.textScale),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
