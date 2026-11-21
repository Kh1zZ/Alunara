package com.alunara.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeMatrixTest {
    @Test
    fun encode_selectsVersion3ForShortLanUrlAndDrawsFinderPatterns() {
        val matrix = QrCodeMatrix.encode("http://192.168.100.124:45821/l?t=Ab3X9k")

        assertEquals(3, matrix.version)
        assertEquals(29, matrix.size)

        // Top-left finder pattern center 3x3 solid black and outer separator white
        for (r in 2..4) {
            for (c in 2..4) {
                assertTrue("Expected dark at ($r,$c)", matrix.get(r, c))
            }
        }
        assertFalse(matrix.get(1, 1))
        assertFalse(matrix.get(7, 7))
        assertTrue(matrix.get(0, 0))
        assertTrue(matrix.get(28, 0))
        assertTrue(matrix.get(0, 28))

        // Alignment pattern center at (22, 22) and dark module at (21, 8)
        assertTrue(matrix.get(22, 22))
        assertFalse(matrix.get(21, 22))
        assertTrue(matrix.get(20, 22))
        assertTrue(matrix.get(21, 8))
    }

    @Test
    fun encode_scalesUpToVersion4ForLongerUrls() {
        val longUrl = "http://192.168.100.124:45821/login?t=Ab3X9k&host=alunara-tv-livingroom"
        val matrix = QrCodeMatrix.encode(longUrl)

        assertEquals(4, matrix.version)
        assertEquals(33, matrix.size)
        assertTrue(matrix.get(26, 26))
        assertTrue(matrix.get(25, 8))
    }

    @Test
    fun formatBits_matchesIso18004VectorForEccLMask0() {
        assertEquals(0x77C4, QrCodeMatrix.formatBitsForEccL(0))
    }
}
