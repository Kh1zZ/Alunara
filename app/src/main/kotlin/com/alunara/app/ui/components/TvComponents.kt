package com.alunara.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import coil3.transform.Transformation
import com.alunara.app.R
import com.alunara.app.playback.PlaybackSessionUiState
import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.app.ui.theme.AlunaraTypography
import com.alunara.app.ui.theme.LocalAlunaraColors
import com.alunara.app.ui.theme.LocalAlunaraDisplayPrefs
import com.alunara.core.domain.media.ArtworkTier
import com.alunara.core.domain.media.ArtworkUrlResolver
import com.alunara.core.domain.model.PlaybackStatus
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Crops out the top and bottom 12.5% black letterbox bars baked into 4:3 YouTube thumbnails
 * (`hqdefault` 480x360, `sddefault` 640x480) while behaving identically to [ContentScale.Crop]
 * on 1:1 square covers and 16:9 frames.
 */
object TvArtworkContentScale : ContentScale {
    override fun computeScaleFactor(
        srcSize: Size,
        dstSize: Size,
    ): ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f) return ScaleFactor(1f, 1f)
        val srcAspect = srcSize.width / srcSize.height
        val effectiveSrcHeight =
            if (srcAspect in 1.28f..1.39f) {
                srcSize.height * 0.74f
            } else {
                srcSize.height
            }
        val widthScale = dstSize.width / srcSize.width
        val heightScale = dstSize.height / effectiveSrcHeight
        val maxScale = maxOf(widthScale, heightScale)
        return ScaleFactor(maxScale, maxScale)
    }
}

/**
 * Downsamples artwork to a 360px canvas, crops out any 4:3 YouTube black letterbox bars,
 * and applies a subtle ~20% soft-focus blur once per track so Now Playing backdrops remain
 * recognizable across Android 9–11 TV (API 28+) with zero per-frame GPU blur cost.
 */
object TvBackdropBlurTransformation : Transformation() {
    private const val TARGET_MAX_PX = 240
    private const val BLUR_RADIUS = 2
    private const val PASSES = 2

    override val cacheKey: String = "alunara_tv_backdrop_blur_r2_p2_v4_baked"

    override suspend fun transform(
        input: Bitmap,
        size: CoilSize,
    ): Bitmap {
        val safeSrc =
            if (input.config == Bitmap.Config.HARDWARE) {
                input.copy(Bitmap.Config.ARGB_8888, false) ?: input
            } else {
                input
            }
        val srcW = safeSrc.width.coerceAtLeast(1)
        val srcH = safeSrc.height.coerceAtLeast(1)
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val cropTop = if (srcAspect in 1.28f..1.39f) (srcH * 0.13f).toInt() else 0
        val cropH = (srcH - cropTop * 2).coerceAtLeast(1)

        val scale = TARGET_MAX_PX.toFloat() / maxOf(srcW, cropH).toFloat()
        val dstW = (srcW * scale).roundToInt().coerceIn(24, TARGET_MAX_PX)
        val dstH = (cropH * scale).roundToInt().coerceIn(24, TARGET_MAX_PX)

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            safeSrc,
            Rect(0, cropTop, srcW, cropTop + cropH),
            Rect(0, 0, dstW, dstH),
            paint,
        )
        if (safeSrc !== input && !safeSrc.isRecycled) {
            safeSrc.recycle()
        }

        val pixels = IntArray(dstW * dstH)
        val scratch = IntArray(dstW * dstH)
        out.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        repeat(PASSES) {
            boxBlurHorizontal(pixels, scratch, dstW, dstH, BLUR_RADIUS)
            boxBlurVertical(scratch, pixels, dstW, dstH, BLUR_RADIUS)
        }
        out.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH)

        // Bake horizontal & vertical Forest Green darkening gradients once per track on Coil's worker
        // thread so NowPlayingScreen draws a single layer with zero per-frame shader overdraw.
        val baseRgb = (AlunaraPalette.BACKGROUND_HEX and 0x00FFFFFFL).toInt()
        val horizColors =
            intArrayOf(
                bgColorWithAlpha(baseRgb, 0.48f),
                bgColorWithAlpha(baseRgb, 0.60f),
                bgColorWithAlpha(baseRgb, 0.72f),
            )
        val horizPaint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        dstW.toFloat(),
                        0f,
                        horizColors,
                        floatArrayOf(0.0f, 0.45f, 1.0f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, dstW.toFloat(), dstH.toFloat(), horizPaint)

        val vertColors =
            intArrayOf(
                bgColorWithAlpha(baseRgb, 0.10f),
                bgColorWithAlpha(baseRgb, 0.28f),
                bgColorWithAlpha(baseRgb, 0.68f),
            )
        val vertPaint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        dstH.toFloat(),
                        vertColors,
                        floatArrayOf(0.0f, 0.55f, 1.0f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, dstW.toFloat(), dstH.toFloat(), vertPaint)
        return out
    }

    private fun bgColorWithAlpha(
        baseRgb: Int,
        alpha: Float,
    ): Int {
        val alphaByte = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return (alphaByte shl 24) or baseRgb
    }

    private fun boxBlurHorizontal(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        radius: Int,
    ) {
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val rowOffset = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -radius..radius) {
                val px = src[rowOffset + k.coerceIn(0, w - 1)]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (x in 0 until w) {
                dst[rowOffset + x] =
                    (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val removePx = src[rowOffset + (x - radius).coerceIn(0, w - 1)]
                val addPx = src[rowOffset + (x + radius + 1).coerceIn(0, w - 1)]
                rSum += ((addPx shr 16) and 0xFF) - ((removePx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((removePx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (removePx and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(
        src: IntArray,
        dst: IntArray,
        w: Int,
        h: Int,
        radius: Int,
    ) {
        val div = radius * 2 + 1
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (k in -radius..radius) {
                val px = src[k.coerceIn(0, h - 1) * w + x]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] =
                    (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val removePx = src[(y - radius).coerceIn(0, h - 1) * w + x]
                val addPx = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                rSum += ((addPx shr 16) and 0xFF) - ((removePx shr 16) and 0xFF)
                gSum += ((addPx shr 8) and 0xFF) - ((removePx shr 8) and 0xFF)
                bSum += (addPx and 0xFF) - (removePx and 0xFF)
            }
        }
    }
}

private val CardCornerShape = RoundedCornerShape(AlunaraDimens.RadiusCard)
private val ControlCornerShape = RoundedCornerShape(AlunaraDimens.RadiusControl)
private val ArtworkCornerShape = RoundedCornerShape(10.dp)
private val SkeletonThumbCornerShape = RoundedCornerShape(8.dp)
private val SkeletonBarCornerShape = RoundedCornerShape(6.dp)

@Composable
fun TvArtwork(
    url: String?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    fillWidthSquare: Boolean = false,
) {
    val colors = LocalAlunaraColors.current
    val context = LocalContext.current
    val tier = if (fillWidthSquare || size >= 110.dp) ArtworkTier.CARD else ArtworkTier.THUMBNAIL
    val resolvedUrl = remember(url, tier) { ArtworkUrlResolver.resolve(url, tier) }
    var useFallbackOriginal by remember(url, tier) { mutableStateOf(false) }
    val activeUrl = if (useFallbackOriginal && !url.isNullOrBlank()) url else resolvedUrl
    val pxSize =
        if (fillWidthSquare) {
            tier.maxPx
        } else {
            with(LocalDensity.current) { size.toPx().roundToInt().coerceIn(64, tier.maxPx) }
        }
    val shape: Shape = if (circular) CircleShape else ArtworkCornerShape
    val boxSizeModifier =
        if (fillWidthSquare) {
            modifier.fillMaxWidth().aspectRatio(1f)
        } else {
            modifier.size(size)
        }

    Box(
        modifier =
            boxSizeModifier
                .clip(shape)
                .background(colors.secondarySurface),
        contentAlignment = Alignment.Center,
    ) {
        if (!activeUrl.isNullOrBlank()) {
            val request =
                remember(activeUrl, pxSize) {
                    ImageRequest
                        .Builder(context)
                        .data(activeUrl)
                        .size(pxSize)
                        .crossfade(false)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = TvArtworkContentScale,
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
internal fun rememberFocusScale(
    focused: Boolean,
    targetScale: Float,
    reduceMotion: Boolean,
): Float =
    animateFloatAsState(
        targetValue = if (focused && !reduceMotion) targetScale else 1f,
        animationSpec =
            if (reduceMotion) {
                snap()
            } else {
                tween(durationMillis = 120, easing = FastOutSlowInEasing)
            },
        label = "tv_focus_scale",
    ).value

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
    val focusScale = rememberFocusScale(focused = focused && enabled, targetScale = 1.02f, reduceMotion = prefs.reduceMotion)
    val shape = ControlCornerShape
    val bgColor =
        when {
            !enabled -> colors.surface.copy(alpha = 0.45f)
            focused -> colors.focusedButtonBg
            highlighted -> colors.secondarySurface
            else -> colors.surface
        }
    val contentColor =
        when {
            !enabled -> colors.dimmedLyrics
            focused -> colors.focusedButtonContent
            highlighted -> colors.primaryAccent
            else -> colors.primaryText
        }
    val borderColor =
        when {
            focused -> colors.focusRingStrong
            highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
            else -> colors.secondarySurface
        }

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
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
            fontWeight = FontWeight.SemiBold,
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
    animateIconChange: Boolean = false,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var focused by remember { mutableStateOf(false) }
    val focusScale = rememberFocusScale(focused = focused && enabled, targetScale = 1.04f, reduceMotion = prefs.reduceMotion)
    val bgColor =
        when {
            !enabled -> colors.surface.copy(alpha = 0.45f)
            focused -> colors.focusedButtonBg
            highlighted -> colors.secondarySurface
            else -> colors.surface
        }
    val tintColor =
        when {
            !enabled -> colors.dimmedLyrics
            focused -> colors.focusedButtonContent
            highlighted -> colors.primaryAccent
            else -> colors.primaryText
        }
    val borderColor =
        when {
            focused -> colors.focusRingStrong
            highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
            else -> colors.secondarySurface
        }

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
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
        if (animateIconChange && !prefs.reduceMotion) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(130)) + scaleIn(animationSpec = tween(130), initialScale = 0.82f)) togetherWith
                        (fadeOut(tween(100)) + scaleOut(animationSpec = tween(100), targetScale = 0.82f))
                },
                label = "tv_icon_morph",
            ) { currentIcon ->
                Icon(
                    imageVector = currentIcon,
                    contentDescription = contentDescription,
                    tint = tintColor,
                    modifier = Modifier.size(iconSize),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(iconSize),
            )
        }
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
    tertiaryActionContentDescription: String? = null,
    tertiaryActionIcon: ImageVector? = null,
    onTertiaryClick: (() -> Unit)? = null,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    var rowFocused by remember { mutableStateOf(false) }
    val rowFocusScale = rememberFocusScale(focused = rowFocused, targetScale = 1.01f, reduceMotion = prefs.reduceMotion)
    val hasSecondary = secondaryActionLabel != null && onSecondaryClick != null
    val hasTertiary = tertiaryActionContentDescription != null && tertiaryActionIcon != null && onTertiaryClick != null
    val mainRowRequester = remember { FocusRequester() }
    val secondaryActionRequester = remember { FocusRequester() }
    val tertiaryActionRequester = remember { FocusRequester() }
    val activeBorderColor = remember(colors.primaryAccent) { colors.primaryAccent.copy(alpha = 0.55f) }
    val subtitle =
        remember(track.primaryArtistName, track.album?.title) {
            listOfNotNull(
                track.primaryArtistName.takeIf { it.isNotBlank() },
                track.album?.title?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
    val durationText = remember(track.durationSeconds) { formatDurationSeconds(track.durationSeconds) }

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
                    .graphicsLayer {
                        scaleX = rowFocusScale
                        scaleY = rowFocusScale
                    }
                    .focusRequester(mainRowRequester)
                    .then(
                        if (hasSecondary) {
                            Modifier.focusProperties { right = secondaryActionRequester }
                        } else {
                            Modifier
                        },
                    )
                    .clip(ControlCornerShape)
                    .drawBehind {
                        val cr = CornerRadius(AlunaraDimens.RadiusControl.toPx())
                        val bg =
                            when {
                                rowFocused -> colors.focusedCardSurface
                                isActive -> colors.secondarySurface
                                else -> colors.surface
                            }
                        drawRoundRect(color = bg, cornerRadius = cr)
                        val strokeColor =
                            when {
                                rowFocused -> colors.focusRingStrong
                                isActive -> activeBorderColor
                                else -> Color.Transparent
                            }
                        if (strokeColor != Color.Transparent) {
                            val strokePx = colors.focusBorderWidth.toPx()
                            val inset = strokePx / 2f
                            drawRoundRect(
                                color = strokeColor,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - strokePx, size.height - strokePx),
                                cornerRadius = cr,
                                style = Stroke(width = strokePx),
                            )
                        }
                    }
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            if (durationText.isNotEmpty()) {
                Text(
                    text = durationText,
                    color = colors.mutedText,
                    fontSize = AlunaraTypography.label(prefs.textScale),
                )
            }
        }

        if (hasSecondary) {
            TvFocusableButton(
                text = secondaryActionLabel!!,
                icon = secondaryActionIcon,
                onClick = onSecondaryClick!!,
                modifier =
                    Modifier
                        .focusRequester(secondaryActionRequester)
                        .focusProperties {
                            left = mainRowRequester
                            if (hasTertiary) right = tertiaryActionRequester
                        },
            )
        }
        if (hasTertiary) {
            TvIconButton(
                icon = tertiaryActionIcon!!,
                contentDescription = tertiaryActionContentDescription!!,
                onClick = onTertiaryClick!!,
                modifier =
                    Modifier
                        .focusRequester(tertiaryActionRequester)
                        .focusProperties {
                            left = if (hasSecondary) secondaryActionRequester else mainRowRequester
                        },
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
    val focusScale = rememberFocusScale(focused = focused, targetScale = 1.025f, reduceMotion = prefs.reduceMotion)

    Column(
        modifier =
            modifier
                .width(AlunaraDimens.CardWidth)
                .heightIn(min = AlunaraDimens.CardHeight)
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                }
                .clip(CardCornerShape)
                .drawBehind {
                    val cr = CornerRadius(AlunaraDimens.RadiusCard.toPx())
                    drawRoundRect(
                        color = if (focused) colors.focusedCardSurface else colors.surface,
                        cornerRadius = cr,
                    )
                    if (focused) {
                        val strokePx = colors.focusBorderWidth.toPx()
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = colors.focusRingStrong,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = cr,
                            style = Stroke(width = strokePx),
                        )
                    }
                }
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
            fillWidthSquare = !circular,
        )
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = AlunaraTypography.body(prefs.textScale),
            fontWeight = FontWeight.SemiBold,
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
    val cardBg = remember(colors.surface) { colors.surface.copy(alpha = 0.55f) }
    val accentBg = remember(colors.secondarySurface) { colors.secondarySurface.copy(alpha = 0.7f) }
    val subBg = remember(colors.secondarySurface) { colors.secondarySurface.copy(alpha = 0.5f) }
    LazyRow(
        modifier = modifier.focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(count, contentType = { "skeleton-card" }) {
            Column(
                modifier =
                    Modifier
                        .width(AlunaraDimens.CardWidth)
                        .height(AlunaraDimens.CardHeight)
                        .background(cardBg, CardCornerShape)
                        .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(AlunaraDimens.CardArtworkSize)
                            .clip(ArtworkCornerShape)
                            .background(accentBg),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(SkeletonBarCornerShape)
                            .background(accentBg),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(SkeletonBarCornerShape)
                            .background(subBg),
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
    val rowBg = remember(colors.surface) { colors.surface.copy(alpha = 0.55f) }
    val accentBg = remember(colors.secondarySurface) { colors.secondarySurface.copy(alpha = 0.7f) }
    val subBg = remember(colors.secondarySurface) { colors.secondarySurface.copy(alpha = 0.5f) }
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
                        .background(rowBg, ControlCornerShape)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(SkeletonThumbCornerShape)
                            .background(accentBg),
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
                                .clip(SkeletonBarCornerShape)
                                .background(accentBg),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.28f)
                                .height(11.dp)
                                .clip(SkeletonBarCornerShape)
                                .background(subBg),
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
    positionMsFlow: StateFlow<Long>? = null,
    playPauseFocusRequester: FocusRequester? = null,
) {
    val track = sessionState.currentTrack ?: return
    DockedMiniPlayerContent(
        track = track,
        status = sessionState.status,
        errorMessage = sessionState.errorMessage,
        durationMs = sessionState.durationMs,
        fallbackPositionMs = if (positionMsFlow != null) 0L else sessionState.positionMs,
        positionMsFlow = positionMsFlow,
        onTogglePlayPause = onTogglePlayPause,
        onOpenNowPlaying = onOpenNowPlaying,
        playPauseFocusRequester = playPauseFocusRequester,
        modifier = modifier,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DockedMiniPlayerContent(
    track: Track,
    status: PlaybackStatus,
    errorMessage: String?,
    durationMs: Long,
    fallbackPositionMs: Long,
    positionMsFlow: StateFlow<Long>?,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    playPauseFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlunaraColors.current
    val prefs = LocalAlunaraDisplayPrefs.current
    val playPauseRequester = playPauseFocusRequester ?: remember { FocusRequester() }
    val openNowPlayingRequester = remember { FocusRequester() }
    val statusText =
        when (status) {
            PlaybackStatus.RESOLVING -> stringResource(R.string.status_resolving)
            PlaybackStatus.BUFFERING -> stringResource(R.string.status_buffering)
            PlaybackStatus.PLAYING -> stringResource(R.string.status_playing)
            PlaybackStatus.PAUSED -> stringResource(R.string.status_paused)
            PlaybackStatus.ENDED -> stringResource(R.string.status_ended)
            PlaybackStatus.ERROR -> errorMessage ?: stringResource(R.string.status_error)
            PlaybackStatus.IDLE -> ""
        }
    val subtitleText =
        remember(track.primaryArtistName, statusText) {
            listOfNotNull(
                track.primaryArtistName.takeIf { it.isNotBlank() },
                statusText.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface)
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Right) {
                            cancelFocusChange()
                        }
                    }
                }
                .focusGroup(),
    ) {
        MiniPlayerProgressStrip(
            durationMs = durationMs,
            fallbackPositionMs = fallbackPositionMs,
            positionMsFlow = positionMsFlow,
        )
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
                    text = subtitleText,
                    color = if (status == PlaybackStatus.ERROR) colors.errorText else colors.primaryAccent,
                    fontSize = AlunaraTypography.caption(prefs.textScale),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvIconButton(
                icon = if (status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                    if (status == PlaybackStatus.PLAYING) {
                        stringResource(R.string.action_pause)
                    } else {
                        stringResource(R.string.action_play)
                    },
                highlighted = true,
                animateIconChange = true,
                onClick = onTogglePlayPause,
                modifier =
                    Modifier
                        .focusRequester(playPauseRequester)
                        .focusProperties {
                            right = openNowPlayingRequester
                        },
            )
            TvFocusableButton(
                text = stringResource(R.string.open_now_playing),
                icon = Icons.Rounded.OpenInFull,
                onClick = onOpenNowPlaying,
                modifier =
                    Modifier
                        .focusRequester(openNowPlayingRequester)
                        .focusProperties {
                            left = playPauseRequester
                        },
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressStrip(
    durationMs: Long,
    fallbackPositionMs: Long,
    positionMsFlow: StateFlow<Long>?,
) {
    val colors = LocalAlunaraColors.current
    val posMsState = positionMsFlow?.collectAsStateWithLifecycle()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    drawRect(color = colors.secondarySurface)
                    if (durationMs > 0L) {
                        val posMs = posMsState?.value ?: fallbackPositionMs
                        val fraction = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        val fillWidth = (size.width * fraction).coerceIn(0f, size.width)
                        if (fillWidth > 0f) {
                            drawRect(
                                color = colors.primaryAccent,
                                size = Size(fillWidth, size.height),
                            )
                        }
                    }
                },
    )
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
