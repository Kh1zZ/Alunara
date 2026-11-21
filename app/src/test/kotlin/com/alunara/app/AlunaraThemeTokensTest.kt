package com.alunara.app

import com.alunara.app.ui.theme.AlunaraDimens
import com.alunara.app.ui.theme.AlunaraDisplayPrefs
import com.alunara.app.ui.theme.AlunaraPalette
import com.alunara.app.ui.theme.AlunaraTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlunaraThemeTokensTest {
    @Test
    fun forestGreenPalette_matchesLockedUiUxSpecification() {
        assertEquals(0xFF101813L, AlunaraPalette.BACKGROUND_HEX)
        assertEquals(0xFF1D2B22L, AlunaraPalette.SURFACE_HEX)
        assertEquals(0xFF85BDA0L, AlunaraPalette.PRIMARY_ACCENT_HEX)
        assertEquals(0xFFE3EEE5L, AlunaraPalette.PRIMARY_TEXT_HEX)
    }

    @Test
    fun displayPrefsAndDimens_enforceTvFocusTargetsAndReducedMotion() {
        assertTrue(AlunaraDimens.ControlHeight.value >= 48f)

        val normal = AlunaraDisplayPrefs()
        assertEquals(1.0f, normal.textScale, 0.001f)
        assertEquals(150, normal.focusAnimMs)
        assertEquals(24f, AlunaraTypography.headline(normal.textScale).value, 0.01f)

        val accessible = AlunaraDisplayPrefs(largeText = true, highContrast = true, reduceMotion = true)
        assertEquals(AlunaraDisplayPrefs.LARGE_TEXT_SCALE, accessible.textScale, 0.001f)
        assertEquals(0, accessible.focusAnimMs)
        assertEquals(0, accessible.navAnimMs)
        assertTrue(AlunaraTypography.lyricActive(accessible.textScale).value > 26f)
        assertTrue(AlunaraPalette.HighContrastTokens.focusBorderWidth >= AlunaraPalette.DefaultTokens.focusBorderWidth)
    }
}
