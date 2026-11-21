package com.alunara.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

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

    val Background = Color(BACKGROUND_HEX)
    val Surface = Color(SURFACE_HEX)
    val SecondarySurface = Color(SECONDARY_SURFACE_HEX)
    val PrimaryAccent = Color(PRIMARY_ACCENT_HEX)
    val PrimaryText = Color(PRIMARY_TEXT_HEX)
    val MutedText = Color(MUTED_TEXT_HEX)
    val FocusOutline = Color(FOCUS_OUTLINE_HEX)
}

private val AlunaraColorScheme =
    darkColorScheme(
        primary = AlunaraPalette.PrimaryAccent,
        onPrimary = AlunaraPalette.Background,
        secondary = AlunaraPalette.SecondarySurface,
        onSecondary = AlunaraPalette.PrimaryText,
        background = AlunaraPalette.Background,
        onBackground = AlunaraPalette.PrimaryText,
        surface = AlunaraPalette.Surface,
        onSurface = AlunaraPalette.PrimaryText,
        surfaceVariant = AlunaraPalette.SecondarySurface,
        onSurfaceVariant = AlunaraPalette.MutedText,
        border = AlunaraPalette.FocusOutline,
    )

@Composable
fun AlunaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlunaraColorScheme,
        content = content,
    )
}
