package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parse_extractsSortedSyncedLinesAndMultiTimestamps() {
        val raw =
            """
            [ti:Sample Song]
            [ar:Sample Artist]
            [00:20.50]Second line
            [00:10.00][01:05.250]Chorus line
            [bad:timestamp]ignored as plain or malformed
            """.trimIndent()

        val result = LrcParser.parse(LyricsProviderId.LRCLIB, raw)
        assertTrue(result is LyricsDocument.Synced)
        val synced = result as LyricsDocument.Synced
        assertEquals(3, synced.lines.size)
        assertEquals(10_000L, synced.lines[0].startTimeMs)
        assertEquals("Chorus line", synced.lines[0].text)
        assertEquals(20_500L, synced.lines[1].startTimeMs)
        assertEquals("Second line", synced.lines[1].text)
        assertEquals(65_250L, synced.lines[2].startTimeMs)
        assertEquals("Chorus line", synced.lines[2].text)
    }

    @Test
    fun parse_fallsBackToPlainWhenNoTimestampsExist() {
        val raw = "Line one\nLine two"
        val result = LrcParser.parse(LyricsProviderId.SIMPMUSIC, raw)
        assertTrue(result is LyricsDocument.Plain)
        assertEquals("Line one\nLine two", (result as LyricsDocument.Plain).text)
    }

    @Test
    fun parse_returnsUnavailableForBlankInput() {
        val result = LrcParser.parse(LyricsProviderId.KUGOU, "   \n  ")
        assertTrue(result is LyricsDocument.Unavailable)
    }

    @Test
    fun parse_extractsWordTimestampsFromEnhancedLrcAndSyllableLines() {
        val raw =
            """
            [00:04.54] <00:04.54>Hari <00:05.00>berulang &amp; <00:05.50>tenang
            <00:08.10>Kita <00:08.60>jalan
            """.trimIndent()

        val result = LrcParser.parse(LyricsProviderId.SIMPMUSIC, raw) as LyricsDocument.Synced
        assertTrue(result.isWordSynced)
        assertEquals(2, result.lines.size)

        val line0 = result.lines[0]
        assertTrue(line0.hasWordSync)
        assertEquals(4_540L, line0.startTimeMs)
        assertEquals("Hari berulang & tenang", line0.text)
        assertEquals(3, line0.words.size)
        assertEquals(5_000L, line0.words[1].startTimeMs)
        assertEquals("berulang & ", line0.words[1].text)

        val line1 = result.lines[1]
        assertTrue(line1.hasWordSync)
        assertEquals(8_100L, line1.startTimeMs)
        assertEquals("Kita jalan", line1.text)
        assertEquals(2, line1.words.size)
    }

    @Test
    fun parse_marksStandardLrcLinesAsNotWordSynced() {
        val result = LrcParser.parse(LyricsProviderId.LRCLIB, "[00:05.00]Plain line synced") as LyricsDocument.Synced
        assertFalse(result.isWordSynced)
        assertFalse(result.lines.single().hasWordSync)
    }
}
