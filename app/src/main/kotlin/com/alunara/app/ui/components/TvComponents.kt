package com.alunara.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
            Text(
                text = "♪",
                color = AlunaraPalette.PrimaryAccent,
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun TvFocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bgColor =
        when {
            !enabled -> AlunaraPalette.Surface
            highlighted -> AlunaraPalette.PrimaryAccent
            focused -> AlunaraPalette.SecondarySurface
            else -> AlunaraPalette.SecondarySurface.copy(alpha = 0.7f)
        }
    val textColor =
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
                .height(40.dp)
                .background(bgColor, shape)
                .border(border, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (highlighted || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvSongRow(
    track: Track,
    isActive: Boolean = false,
    onPlayClick: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var rowFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bgColor =
        when {
            isActive -> AlunaraPalette.SecondarySurface
            rowFocused -> AlunaraPalette.SecondarySurface.copy(alpha = 0.85f)
            else -> AlunaraPalette.Background.copy(alpha = 0.45f)
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
                    if (focused) AlunaraPalette.SecondarySurface else AlunaraPalette.Background.copy(alpha = 0.55f),
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = AlunaraPalette.MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(AlunaraPalette.SecondarySurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
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
        TvFocusableButton(
            text =
                if (sessionState.status == PlaybackStatus.PLAYING) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
            highlighted = true,
            onClick = onTogglePlayPause,
        )
        Spacer(modifier = Modifier.width(4.dp))
        TvFocusableButton(
            text = stringResource(R.string.open_now_playing),
            onClick = onOpenNowPlaying,
        )
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
