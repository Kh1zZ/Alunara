package com.alunara.app

import com.alunara.app.ui.theme.AlunaraPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class AlunaraThemeTokensTest {
    @Test
    fun forestGreenPalette_matchesLockedUiUxSpecification() {
        assertEquals(0xFF101813L, AlunaraPalette.BACKGROUND_HEX)
        assertEquals(0xFF1D2B22L, AlunaraPalette.SURFACE_HEX)
        assertEquals(0xFF85BDA0L, AlunaraPalette.PRIMARY_ACCENT_HEX)
        assertEquals(0xFFE3EEE5L, AlunaraPalette.PRIMARY_TEXT_HEX)
    }
}
