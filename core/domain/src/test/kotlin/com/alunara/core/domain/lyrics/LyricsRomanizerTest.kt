package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRomanizerTest {
    @Test
    fun romanize_koreanHangul_producesAccurateRevisedRomanizationWithFullCoverage() {
        val result = LyricsRomanizer.romanize("서투른 우리를 헤맨대도")
        assertTrue(result.hasRomanization)
        assertEquals(LyricScript.KOREAN, result.script)
        assertEquals("seotureun urireul hemaendaedo", result.text)
        assertEquals(0, result.unsupportedKanjiChars)
        assertEquals(1.0f, result.coverageRatio, 0.001f)

        val liaisonResult = LyricsRomanizer.romanize("사랑해 있어")
        assertEquals("saranghae isseo", liaisonResult.text)
    }

    @Test
    fun romanize_japaneseHiraganaAndKatakana_handlesYoonSokuonProlongedVowelAndN() {
        val hiragana = LyricsRomanizer.romanize("ずっと きみと いっしょに しんあい")
        assertTrue(hiragana.hasRomanization)
        assertEquals(LyricScript.JAPANESE, hiragana.script)
        assertEquals("zutto kimito isshoni shin'ai", hiragana.text)
        assertEquals(0, hiragana.unsupportedKanjiChars)

        val katakana = LyricsRomanizer.romanize("ビート ストーリー マッチ")
        assertEquals("biito sutoorii matchi", katakana.text)
    }

    @Test
    fun romanize_mixedKanjiAndKana_convertsFullLineViaKuromojiWithoutKanjiLeftovers() {
        val mixed = LyricsRomanizer.romanize("散々な思い出は悲しみを穿つほど")
        assertTrue(mixed.hasRomanization)
        assertEquals(LyricScript.JAPANESE, mixed.script)
        assertEquals("sanzan na omoide wa kanashimi o ugatsu hodo", mixed.text)
        assertEquals(0, mixed.unsupportedKanjiChars)
        assertEquals(1.0f, mixed.coverageRatio, 0.001f)

        val night = LyricsRomanizer.romanize("夜に駆ける")
        assertEquals("yoru ni kakeru", night.text)
        assertEquals(0, night.unsupportedKanjiChars)
    }

    @Test
    fun romanize_latinOnlyLyrics_reportsNoRomanizationNeeded() {
        val english = LyricsRomanizer.romanize("You know me the best")
        assertFalse(english.hasRomanization)
        assertEquals(LyricScript.NONE, english.script)
        assertEquals(0f, english.coverageRatio, 0.001f)
    }

    @Test
    fun attachRomanization_populatesSyncedDocumentAndWordRomanizationWithKuromoji() {
        val doc =
            LyricsDocument.Synced(
                provider = LyricsProviderId.SIMPMUSIC,
                lines =
                    listOf(
                        SyncedLyricLine(startTimeMs = 1_000L, text = "I'll be by your side"),
                        SyncedLyricLine(startTimeMs = 4_000L, text = "きみの そばで"),
                        SyncedLyricLine(
                            startTimeMs = 8_000L,
                            text = "僕の名前はありふれた",
                            words =
                                listOf(
                                    SyncedLyricWord(8_000L, "僕の"),
                                    SyncedLyricWord(8_500L, "名前は"),
                                    SyncedLyricWord(9_000L, "ありふれた"),
                                ),
                        ),
                    ),
            )

        val summary = LyricsRomanizer.attachRomanization(doc)
        assertTrue(summary.hasRomanizableLines)
        assertFalse(summary.hasUnsupportedKanji)

        val synced = summary.document as LyricsDocument.Synced
        assertNull(synced.lines[0].romanizedText)
        assertEquals("kimino sobade", synced.lines[1].romanizedText)
        assertEquals("boku no namae wa arifure ta", synced.lines[2].romanizedText)
        assertEquals("boku no ", synced.lines[2].words[0].romanizedText)
        assertEquals("namae wa ", synced.lines[2].words[1].romanizedText)
        assertEquals("arifureta", synced.lines[2].words[2].romanizedText)
    }
}
