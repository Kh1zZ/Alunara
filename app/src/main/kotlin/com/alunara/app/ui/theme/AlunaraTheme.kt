package com.alunara.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@Immutable
data class AlunaraColorTokens(
    val background: Color,
    val surface: Color,
    val secondarySurface: Color,
    val primaryAccent: Color,
    val primaryText: Color,
    val mutedText: Color,
    val dimmedLyrics: Color,
    val focusOutline: Color,
    val focusedButtonBg: Color,
    val focusedButtonContent: Color,
    val focusedCardSurface: Color,
    val focusRingStrong: Color,
    val errorSurface: Color,
    val errorText: Color,
    val focusBorderWidth: Dp,
)

@Immutable
data class AlunaraDisplayPrefs(
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
) {
    val textScale: Float
        get() = if (largeText) LARGE_TEXT_SCALE else 1.0f

    val focusAnimMs: Int
        get() = if (reduceMotion) 0 else 150

    val navAnimMs: Int
        get() = if (reduceMotion) 0 else 200

    companion object {
        const val LARGE_TEXT_SCALE: Float = 1.15f
    }
}

/**
 * Locked Fixed Dark Forest Green design tokens (UI/UX Spec §2.1).
 * Dynamic color from wallpaper or album artwork is strictly prohibited.
 */
object AlunaraPalette {
    const val BACKGROUND_HEX: Long = 0xFF101813L
    const val SURFACE_HEX: Long = 0xFF1D2B22L
    const val PRIMARY_ACCENT_HEX: Long = 0xFF85BDA0L
    const val PRIMARY_TEXT_HEX: Long = 0xFFE3EEE5L
    const val FOCUS_OUTLINE_HEX: Long = 0xFFB5E6CCL
    const val SECONDARY_SURFACE_HEX: Long = 0xFF26382DL
    const val MUTED_TEXT_HEX: Long = 0xFFB2C4B7L
    const val DIMMED_LYRICS_HEX: Long = 0xFF7E9486L
    const val ERROR_SURFACE_HEX: Long = 0xFF3E2224L
    const val ERROR_TEXT_HEX: Long = 0xFFF4B8BCL

    val Background = Color(BACKGROUND_HEX)
    val Surface = Color(SURFACE_HEX)
    val SecondarySurface = Color(SECONDARY_SURFACE_HEX)
    val PrimaryAccent = Color(PRIMARY_ACCENT_HEX)
    val PrimaryText = Color(PRIMARY_TEXT_HEX)
    val MutedText = Color(MUTED_TEXT_HEX)
    val DimmedLyrics = Color(DIMMED_LYRICS_HEX)
    val FocusOutline = Color(FOCUS_OUTLINE_HEX)
    val FocusedButtonBg = Color(0xFFC8F0DAL)
    val FocusedButtonContent = Color(0xFF0B120EL)
    val FocusedCardSurface = Color(0xFF2E493AL)
    val FocusRingStrong = Color(0xFFFFFFFFL)
    val ErrorSurface = Color(ERROR_SURFACE_HEX)
    val ErrorText = Color(ERROR_TEXT_HEX)

    val DefaultTokens =
        AlunaraColorTokens(
            background = Background,
            surface = Surface,
            secondarySurface = SecondarySurface,
            primaryAccent = PrimaryAccent,
            primaryText = PrimaryText,
            mutedText = MutedText,
            dimmedLyrics = DimmedLyrics,
            focusOutline = FocusOutline,
            focusedButtonBg = FocusedButtonBg,
            focusedButtonContent = FocusedButtonContent,
            focusedCardSurface = FocusedCardSurface,
            focusRingStrong = FocusRingStrong,
            errorSurface = ErrorSurface,
            errorText = ErrorText,
            focusBorderWidth = 2.5.dp,
        )

    val HighContrastTokens =
        AlunaraColorTokens(
            background = Color(0xFF080E0BL),
            surface = Color(0xFF14221AL),
            secondarySurface = Color(0xFF1F3629L),
            primaryAccent = Color(0xFFA2DEC0L),
            primaryText = Color(0xFFF3FAF5L),
            mutedText = Color(0xFFD0E6D8L),
            dimmedLyrics = Color(0xFFA7C1B1L),
            focusOutline = Color(0xFFD6F7E5L),
            focusedButtonBg = Color(0xFFE0FAECL),
            focusedButtonContent = Color(0xFF050A07L),
            focusedCardSurface = Color(0xFF284836L),
            focusRingStrong = Color(0xFFFFFFFFL),
            errorSurface = Color(0xFF4A2023L),
            errorText = Color(0xFFFFD0D3L),
            focusBorderWidth = 3.dp,
        )
}

object AlunaraTypography {
    fun headline(scale: Float): TextUnit = (24f * scale).sp

    fun title(scale: Float): TextUnit = (20f * scale).sp

    fun sectionTitle(scale: Float): TextUnit = (18f * scale).sp

    fun rowTitle(scale: Float): TextUnit = (15f * scale).sp

    fun body(scale: Float): TextUnit = (14f * scale).sp

    fun label(scale: Float): TextUnit = (13f * scale).sp

    fun caption(scale: Float): TextUnit = (12f * scale).sp

    fun lyricActive(scale: Float): TextUnit = (26f * scale).sp

    fun lyricInactive(scale: Float): TextUnit = (19f * scale).sp

    fun lyricPlain(scale: Float): TextUnit = (20f * scale).sp
}

/**
 * Shared 10-foot Android TV layout & geometry tokens (960x540 dp logical 720p grid).
 */
object AlunaraDimens {
    val RailCollapsedWidth = 72.dp
    val RailExpandedWidth = 216.dp
    val RailItemHeight = 48.dp
    val ControlHeight = 48.dp
    val QueueActionButtonSize = 40.dp
    val SongRowHeight = 60.dp
    val MiniPlayerHeight = 68.dp
    val CardWidth = 152.dp
    val CardHeight = 216.dp
    val CardArtworkSize = 132.dp
    val RadiusControl = 12.dp
    val RadiusCard = 14.dp
    val FocusBorderWidth = 2.5.dp
    val OverscanHorizontal = 36.dp
    val OverscanVertical = 22.dp
}

val LocalAlunaraDisplayPrefs = staticCompositionLocalOf { AlunaraDisplayPrefs() }
val LocalAlunaraColors = staticCompositionLocalOf { AlunaraPalette.DefaultTokens }

class AlunaraDisplayPrefsStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AlunaraDisplayPrefs {
        val systemReduceMotion = isSystemReduceMotionEnabled(appContext)
        val reduceMotion =
            if (prefs.contains(KEY_REDUCE_MOTION)) {
                prefs.getBoolean(KEY_REDUCE_MOTION, systemReduceMotion)
            } else {
                systemReduceMotion
            }
        return AlunaraDisplayPrefs(
            largeText = prefs.getBoolean(KEY_LARGE_TEXT, false),
            highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false),
            reduceMotion = reduceMotion,
        )
    }

    fun save(displayPrefs: AlunaraDisplayPrefs) {
        prefs
            .edit()
            .putBoolean(KEY_LARGE_TEXT, displayPrefs.largeText)
            .putBoolean(KEY_HIGH_CONTRAST, displayPrefs.highContrast)
            .putBoolean(KEY_REDUCE_MOTION, displayPrefs.reduceMotion)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "alunara_display_prefs"
        private const val KEY_LARGE_TEXT = "large_text"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"

        fun isSystemReduceMotionEnabled(context: Context): Boolean =
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
                ) == 0.0f
            }.getOrDefault(false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlunaraTheme(
    displayPrefs: AlunaraDisplayPrefs = AlunaraDisplayPrefs(),
    content: @Composable () -> Unit,
) {
    val tokens = if (displayPrefs.highContrast) AlunaraPalette.HighContrastTokens else AlunaraPalette.DefaultTokens
    val tvBringIntoViewSpec =
        remember(displayPrefs.reduceMotion) {
            object : BringIntoViewSpec {
                @Suppress("OVERRIDE_DEPRECATION")
                override val scrollAnimationSpec: AnimationSpec<Float> =
                    if (displayPrefs.reduceMotion) {
                        snap()
                    } else {
                        tween(durationMillis = 165, easing = FastOutSlowInEasing)
                    }
            }
        }
    val colorScheme =
        darkColorScheme(
            primary = tokens.primaryAccent,
            onPrimary = tokens.background,
            secondary = tokens.secondarySurface,
            onSecondary = tokens.primaryText,
            background = tokens.background,
            onBackground = tokens.primaryText,
            surface = tokens.surface,
            onSurface = tokens.primaryText,
            surfaceVariant = tokens.secondarySurface,
            onSurfaceVariant = tokens.mutedText,
            border = tokens.focusOutline,
        )
    CompositionLocalProvider(
        LocalAlunaraDisplayPrefs provides displayPrefs,
        LocalAlunaraColors provides tokens,
        LocalBringIntoViewSpec provides tvBringIntoViewSpec,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
