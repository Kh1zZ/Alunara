package com.alunara.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.Track
import java.util.Locale

@Composable
fun TvArtwork(
    url: String?,
    contentDescription: String?,
    size: Dp,
    circular: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape: Shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(AlunaraPalette.SecondarySurface),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = AlunaraPalette.PrimaryAccent,
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val bgColor =
        when {
            !enabled -> AlunaraPalette.Surface
            highlighted -> AlunaraPalette.PrimaryAccent
            focused -> AlunaraPalette.SecondarySurface
            else -> AlunaraPalette.SecondarySurface.copy(alpha = 0.72f)
        }
    val contentColor =
        when {
            !enabled -> AlunaraPalette.DimmedLyrics
            highlighted -> AlunaraPalette.Background
            else -> AlunaraPalette.PrimaryText
        }
    val border =
        if (focused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Row(
        modifier =
            modifier
                .height(40.dp)
                .background(bgColor, shape)
                .border(border, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            fontSize = 14.sp,
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
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor =
        when {
            !enabled -> AlunaraPalette.Surface.copy(alpha = 0.7f)
            highlighted -> AlunaraPalette.PrimaryAccent
            focused -> AlunaraPalette.SecondarySurface
            else -> AlunaraPalette.Surface.copy(alpha = 0.85f)
        }
    val tintColor =
        when {
            !enabled -> AlunaraPalette.DimmedLyrics
            highlighted -> AlunaraPalette.Background
            else -> AlunaraPalette.PrimaryText
        }
    val border =
        if (focused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor, CircleShape)
                .border(border, CircleShape)
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
    isActive: Boolean = false,
    onPlayClick: () -> Unit,
    secondaryActionLabel: String? = null,
    secondaryActionIcon: ImageVector? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bgColor =
        when {
            isActive -> AlunaraPalette.SecondarySurface
            rowFocused -> AlunaraPalette.SecondarySurface.copy(alpha = 0.85f)
            else -> AlunaraPalette.Surface.copy(alpha = 0.7f)
        }
    val border =
        if (rowFocused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .background(bgColor, shape)
                    .border(border, shape)
                    .onFocusChanged { rowFocused = it.isFocused }
                    .clickable(onClick = onPlayClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvArtwork(url = track.thumbnailUrl, contentDescription = null, size = 42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isActive) AlunaraPalette.PrimaryAccent else AlunaraPalette.PrimaryText,
                    fontSize = 15.sp,
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
                        color = AlunaraPalette.MutedText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val durationText = formatDurationSeconds(track.durationSeconds)
            if (durationText.isNotEmpty()) {
                Text(
                    text = durationText,
                    color = AlunaraPalette.MutedText,
                    fontSize = 13.sp,
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
    circular: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val border =
        if (focused) {
            BorderStroke(2.dp, AlunaraPalette.FocusOutline)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        }

    Column(
        modifier =
            modifier
                .width(136.dp)
                .background(
                    if (focused) AlunaraPalette.SecondarySurface else AlunaraPalette.Surface.copy(alpha = 0.72f),
                    shape,
                ).border(border, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(10.dp),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvArtwork(
            url = thumbnailUrl,
            contentDescription = null,
            size = 116.dp,
            circular = circular,
        )
        Text(
            text = title,
            color = AlunaraPalette.PrimaryText,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = AlunaraPalette.MutedText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DockedMiniPlayer(
    sessionState: PlaybackSessionUiState,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Column(modifier = modifier.fillMaxWidth().background(AlunaraPalette.Surface)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AlunaraPalette.SecondarySurface),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvArtwork(url = track.thumbnailUrl, contentDescription = null, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = AlunaraPalette.PrimaryText,
                    fontSize = 15.sp,
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
                    color = if (sessionState.status == PlaybackStatus.ERROR) AlunaraPalette.ErrorText else AlunaraPalette.PrimaryAccent,
                    fontSize = 12.sp,
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
