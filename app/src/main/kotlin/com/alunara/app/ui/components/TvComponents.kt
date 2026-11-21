package com.alunara.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.media.ArtworkTier
import com.alunara.core.domain.media.ArtworkUrlResolver
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.Track
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TvArtwork(
    url: String?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val context = LocalContext.current
    val tier = if (size >= 110.dp) ArtworkTier.CARD else ArtworkTier.THUMBNAIL
    val resolvedUrl = remember(url, tier) { ArtworkUrlResolver.resolve(url, tier) }
    var useFallbackOriginal by remember(url, tier) { mutableStateOf(false) }
    val activeUrl = if (useFallbackOriginal && !url.isNullOrBlank()) url else resolvedUrl
    val pxSize = with(LocalDensity.current) { size.toPx().roundToInt().coerceAtLeast(tier.maxPx) }
    val shape: Shape = if (circular) CircleShape else RoundedCornerShape(10.dp)

    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(colors.secondarySurface),
        contentAlignment = Alignment.Center,
    ) {
        if (!activeUrl.isNullOrBlank()) {
            val request =
                remember(activeUrl, pxSize, prefs.reduceMotion) {
                    ImageRequest
                        .Builder(context)
                        .data(activeUrl)
                        .size(pxSize)
                        .crossfade(!prefs.reduceMotion)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = {
                    if (!useFallbackOriginal && activeUrl != url && !url.isNullOrBlank()) {
                        useFallbackOriginal = true
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = colors.primaryAccent,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

@Composable
fun TvFocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val bgColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> colors.surface.copy(alpha = 0.45f)
                focused -> colors.focusedButtonBg
                highlighted -> colors.secondarySurface
                else -> colors.surface
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "btn_bg",
    )
    val contentColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> colors.dimmedLyrics
                focused -> colors.focusedButtonContent
                highlighted -> colors.primaryAccent
                else -> colors.primaryText
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "btn_fg",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                focused -> colors.focusRingStrong
                highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
                else -> colors.secondarySurface
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "btn_border",
    )

    Row(
        modifier =
            modifier
                .heightIn(min = AlunaraDimens.ControlHeight)
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics {
                    role = Role.Button
                    selected = highlighted
                }
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = AlunaraTypography.body(prefs.textScale),
            fontWeight = if (highlighted || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AlunaraDimens.ControlHeight,
    iconSize: Dp = 22.dp,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> colors.surface.copy(alpha = 0.45f)
                focused -> colors.focusedButtonBg
                highlighted -> colors.secondarySurface
                else -> colors.surface
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "icon_btn_bg",
    )
    val tintColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> colors.dimmedLyrics
                focused -> colors.focusedButtonContent
                highlighted -> colors.primaryAccent
                else -> colors.primaryText
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "icon_btn_tint",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                focused -> colors.focusRingStrong
                highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
                else -> colors.secondarySurface
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "icon_btn_border",
    )

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor, CircleShape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), CircleShape)
                .semantics {
                    role = Role.Button
                    selected = highlighted
                }
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun TvSongRow(
    track: Track,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var rowFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    val bgColor by animateColorAsState(
        targetValue =
            when {
                rowFocused -> colors.focusedCardSurface
                isActive -> colors.secondarySurface
                else -> colors.surface.copy(alpha = 0.72f)
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "row_bg",
    )
    val borderColor by animateColorAsState(
        targetValue =
            when {
                rowFocused -> colors.focusRingStrong
                isActive -> colors.primaryAccent.copy(alpha = 0.55f)
                else -> Color.Transparent
            },
        animationSpec = tween(prefs.focusAnimMs),
        label = "row_border",
    )

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = AlunaraDimens.SongRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = AlunaraDimens.SongRowHeight)
                    .background(bgColor, shape)
                    .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                    .semantics {
                        role = Role.Button
                        selected = isActive
                    }
                    .onFocusChanged { rowFocused = it.isFocused }
                    .clickable(onClick = onPlayClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvArtwork(url = track.thumbnailUrl, contentDescription = null, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isActive) colors.primaryAccent else colors.primaryText,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    fontWeight = if (isActive || rowFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
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
                        fontSize = AlunaraTypography.label(prefs.textScale),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val durationText = formatDurationSeconds(track.durationSeconds)
            if (durationText.isNotEmpty()) {
                Text(
                    text = durationText,
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(prefs.textScale),
                )
            }
        }

        if (secondaryActionLabel != null && onSecondaryClick != null) {
            TvFocusableButton(
                text = secondaryActionLabel,
                icon = secondaryActionIcon,
                onClick = onSecondaryClick,
            )
        }
    }
}

@Composable
fun TvMediaCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AlunaraDimens.RadiusCard)
    val bgColor by animateColorAsState(
        targetValue = if (focused) colors.focusedCardSurface else colors.surface.copy(alpha = 0.72f),
        animationSpec = tween(prefs.focusAnimMs),
        label = "card_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.focusRingStrong else Color.Transparent,
        animationSpec = tween(prefs.focusAnimMs),
        label = "card_border",
    )

    Column(
        modifier =
            modifier
                .width(AlunaraDimens.CardWidth)
                .heightIn(min = AlunaraDimens.CardHeight)
                .background(bgColor, shape)
                .border(BorderStroke(colors.focusBorderWidth, borderColor), shape)
                .semantics { role = Role.Button }
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(10.dp),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvArtwork(
            url = thumbnailUrl,
            contentDescription = null,
            size = AlunaraDimens.CardArtworkSize,
            circular = circular,
        )
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = AlunaraTypography.body(prefs.textScale),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            lineHeight = (18f * prefs.textScale).sp,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle.ifBlank { " " },
            color = colors.mutedText,
            fontSize = AlunaraTypography.caption(prefs.textScale),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvCardRowSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    val colors = LocalAlunaraColors.current
    val shape = RoundedCornerShape(AlunaraDimens.RadiusCard)
    LazyRow(
        modifier = modifier.focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            Column(
                modifier =
                    Modifier
                        .width(AlunaraDimens.CardWidth)
                        .height(AlunaraDimens.CardHeight)
                        .background(colors.surface.copy(alpha = 0.55f), shape)
                        .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(AlunaraDimens.CardArtworkSize)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.secondarySurface.copy(alpha = 0.7f)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.secondarySurface.copy(alpha = 0.7f)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.secondarySurface.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

@Composable
fun TvSongListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    val colors = LocalAlunaraColors.current
    val shape = RoundedCornerShape(AlunaraDimens.RadiusControl)
    Column(
        modifier = modifier.fillMaxWidth().focusProperties { canFocus = false },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AlunaraDimens.SongRowHeight)
                        .background(colors.surface.copy(alpha = 0.55f), shape)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.secondarySurface.copy(alpha = 0.7f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.secondarySurface.copy(alpha = 0.7f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.28f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.secondarySurface.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
fun DockedMiniPlayer(
    sessionState: PlaybackSessionUiState,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val track = sessionState.currentTrack ?: return
    val statusText =
        when (sessionState.status) {
            PlaybackStatus.RESOLVING -> stringResource(R.string.status_resolving)
            PlaybackStatus.BUFFERING -> stringResource(R.string.status_buffering)
            PlaybackStatus.PLAYING -> stringResource(R.string.status_playing)
            PlaybackStatus.PAUSED -> stringResource(R.string.status_paused)
            PlaybackStatus.ENDED -> stringResource(R.string.status_ended)
            PlaybackStatus.ERROR -> sessionState.errorMessage ?: stringResource(R.string.status_error)
            PlaybackStatus.IDLE -> ""
        }
    val progressFraction =
        if (sessionState.durationMs > 0L) {
            (sessionState.positionMs.toFloat() / sessionState.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.secondarySurface),
        ) {
            if (progressFraction > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progressFraction)
                            .height(2.dp)
                            .background(colors.primaryAccent),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AlunaraDimens.MiniPlayerHeight)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvArtwork(url = track.thumbnailUrl, contentDescription = null, size = 44.dp)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
            ) {
                Text(
                    text = track.title,
                    color = colors.primaryText,
                    fontSize = AlunaraTypography.rowTitle(prefs.textScale),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                            track.primaryArtistName.takeIf { it.isNotBlank() },
                            statusText.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    color = if (sessionState.status == PlaybackStatus.ERROR) colors.errorText else colors.primaryAccent,
                    fontSize = AlunaraTypography.caption(prefs.textScale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                highlighted = true,
                onClick = onTogglePlayPause,
            )
            TvFocusableButton(
                text = stringResource(R.string.open_now_playing),
                icon = Icons.Rounded.OpenInFull,
                onClick = onOpenNowPlaying,
            )
        }
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

fun formatDurationSeconds(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    return formatDurationMs(seconds.toLong() * 1000L)
}

fun FocusRequester.safeRequestFocus(): Boolean =
    runCatching {
        requestFocus()
        true
    }.getOrDefault(false)
