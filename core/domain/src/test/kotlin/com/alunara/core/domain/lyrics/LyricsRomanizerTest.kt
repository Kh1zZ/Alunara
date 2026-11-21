package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
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
    fun romanize_mixedKanjiAndKana_convertsKanaWithoutFabricatingKanjiReadings() {
        val mixed = LyricsRomanizer.romanize("僕のそばで鳴るよ")
        assertTrue(mixed.hasRomanization)
        assertEquals(LyricScript.JAPANESE, mixed.script)
        // Kanji characters 僕 and 鳴 remain untouched instead of inventing ambiguous readings
        assertEquals("僕nosobade鳴ruyo", mixed.text)
        assertEquals(2, mixed.unsupportedKanjiChars)
        assertTrue(mixed.coverageRatio < 1.0f)
    }

    @Test
    fun romanize_latinOnlyLyrics_reportsNoRomanizationNeeded() {
        val english = LyricsRomanizer.romanize("You know me the best")
        assertFalse(english.hasRomanization)
        assertEquals(LyricScript.NONE, english.script)
        assertEquals(0f, english.coverageRatio, 0.001f)
    }

    @Test
    fun attachRomanization_populatesSyncedDocumentAndDetectsKanjiNotice() {
        val doc =
            LyricsDocument.Synced(
                provider = LyricsProviderId.SIMPMUSIC,
                lines =
                    listOf(
                        SyncedLyricLine(startTimeMs = 1_000L, text = "I'll be by your side"),
                        SyncedLyricLine(startTimeMs = 4_000L, text = "きみの そばで"),
                        SyncedLyricLine(startTimeMs = 8_000L, text = "僕の名前はありふれた"),
                    ),
            )

        val summary = LyricsRomanizer.attachRomanization(doc)
        assertTrue(summary.hasRomanizableLines)
        assertTrue(summary.hasUnsupportedKanji)

        val synced = summary.document as LyricsDocument.Synced
        assertNull(synced.lines[0].romanizedText)
        assertEquals("kimino sobade", synced.lines[1].romanizedText)
        assertEquals("僕no名前haarifureta", synced.lines[2].romanizedText)
    }
}
