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
    fun romanize_koreanHangul_producesArchiveTuneRevisedRomanizationWithPhonologicalAssimilation() {
        val result = LyricsRomanizer.romanize("서투른 우리를 헤맨대도")
        assertTrue(result.hasRomanization)
        assertEquals(LyricScript.KOREAN, result.script)
        assertEquals("seotureun urireul hemaendaedo", result.text)
        assertEquals(0, result.unsupportedKanjiChars)
        assertEquals(1.0f, result.coverageRatio, 0.001f)

        assertEquals("saranghae isseo", LyricsRomanizer.romanize("사랑해 있어").text)
        assertEquals("eomneun", LyricsRomanizer.romanize("없는").text)
        assertEquals("silla", LyricsRomanizer.romanize("신라").text)
    }

    @Test
    fun romanize_japaneseLines_joinsConjugationalSuffixesAndSokuonNaturallyViaKuromoji() {
        assertEquals(
            "tomodachi no mae no boku wa yasashikute",
            LyricsRomanizer.romanize("友達の前の僕は優しくて").text,
        )
        assertEquals(
            "hontou no boku wa dare mo shiranai",
            LyricsRomanizer.romanize("本当の僕は誰も知らない").text,
        )
        assertEquals(
            "omotta kokoro ni futa o shita",
            LyricsRomanizer.romanize("思った心に蓋をした").text,
        )
        assertEquals(
            "iitai koto o gaman shite",
            LyricsRomanizer.romanize("言いたいことを我慢して").text,
        )

        val night = LyricsRomanizer.romanize("夜に駆ける")
        assertEquals(LyricScript.JAPANESE, night.script)
        assertEquals("yoru ni kakeru", night.text)
        assertEquals(0, night.unsupportedKanjiChars)

        val mixed = LyricsRomanizer.romanize("散々な思い出は悲しみを穿つほど")
        assertEquals("sanzan na omoide wa kanashimi o ugatsu hodo", mixed.text)
        assertEquals(0, mixed.unsupportedKanjiChars)

        val katakana = LyricsRomanizer.romanize("ビート ストーリー マッチ")
        assertEquals("biito sutoorii matchi", katakana.text)
    }

    @Test
    fun romanize_russianCyrillic_transliteratesAccuratelyOffline() {
        val cyrillic = LyricsRomanizer.romanize("Привет мир, я люблю тебя")
        assertTrue(cyrillic.hasRomanization)
        assertEquals(LyricScript.CYRILLIC, cyrillic.script)
        assertEquals("Privet mir, ya lyublyu tebya", cyrillic.text)
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
                        SyncedLyricLine(startTimeMs = 4_000L, text = "きみのそばで"),
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
        assertEquals("kimi no soba de", synced.lines[1].romanizedText)
        assertEquals("boku no namae wa arifureta", synced.lines[2].romanizedText)
        assertEquals("boku no ", synced.lines[2].words[0].romanizedText)
        assertEquals("namae wa ", synced.lines[2].words[1].romanizedText)
        assertEquals("arifureta", synced.lines[2].words[2].romanizedText)
    }

    @Test
    fun attachRomanization_preservesContextualReadingAcrossSplitJapaneseAndKoreanWordTokens() {
        val doc =
            LyricsDocument.Synced(
                provider = LyricsProviderId.SIMPMUSIC,
                lines =
                    listOf(
                        SyncedLyricLine(
                            startTimeMs = 1_000L,
                            text = "今日行って",
                            words =
                                listOf(
                                    SyncedLyricWord(1_000L, "今"),
                                    SyncedLyricWord(1_200L, "日"),
                                    SyncedLyricWord(1_500L, "行"),
                                    SyncedLyricWord(1_700L, "っ"),
                                    SyncedLyricWord(1_900L, "て"),
                                ),
                        ),
                        SyncedLyricLine(
                            startTimeMs = 4_000L,
                            text = "음악",
                            words =
                                listOf(
                                    SyncedLyricWord(4_000L, "음"),
                                    SyncedLyricWord(4_300L, "악"),
                                ),
                        ),
                    ),
            )

        val summary = LyricsRomanizer.attachRomanization(doc)
        val synced = summary.document as LyricsDocument.Synced

        val jaJoined = synced.lines[0].words.joinToString("") { it.romanizedText.orEmpty() }
        assertTrue(synced.lines[0].romanizedText!!.startsWith("kyou "))
        assertEquals(synced.lines[0].romanizedText, jaJoined)

        val krJoined = synced.lines[1].words.joinToString("") { it.romanizedText.orEmpty() }
        assertEquals("eumak", synced.lines[1].romanizedText)
        assertEquals("eumak", krJoined)
    }

    @Test
    fun attachRomanization_romanizesSpacedUpstreamJapaneseCompoundsWithoutPinyinLeakOrDoubleQuoteSpaces() {
        val raw =
            "[00:01.00]<00:01.00>壊 <00:01.20>れ <00:01.40>な <00:01.60>い <00:01.80>理 <00:02.00>由 <00:02.20>の <00:02.40>わ <00:02.60>か <00:02.80>ら <00:03.00>ぬ <00:03.20>舟 <00:03.40>の <00:03.60>上 <00:03.80>に <00:04.00>二 <00:04.20>人\n" +
                "[00:05.00]<00:05.00>教えて<00:05.40>欲 <00:05.70>しかった\n" +
                "[00:08.00]<00:08.00>「<00:08.10>さよなら<00:08.50>」 <00:08.80>と一緒に"
        val parsed = LrcParser.parse(LyricsProviderId.SIMPMUSIC, raw) as LyricsDocument.Synced
        val summary = LyricsRomanizer.attachRomanization(parsed)
        val synced = summary.document as LyricsDocument.Synced

        val line0Roman = synced.lines[0].romanizedText.orEmpty()
        assertTrue("line0=$line0Roman", line0Roman.contains("kowarenai"))
        assertTrue("line0=$line0Roman", line0Roman.contains("riyuu"))
        assertTrue("line0=$line0Roman", line0Roman.contains("wakaranu"))
        assertFalse("line0=$line0Roman", line0Roman.contains("huai"))
        assertFalse("line0=$line0Roman", line0Roman.contains("yoshi"))
        assertFalse("line0=$line0Roman", line0Roman.contains("ni hito"))
        assertEquals(line0Roman, synced.lines[0].words.joinToString("") { it.romanizedText.orEmpty() })

        val line1Roman = synced.lines[1].romanizedText.orEmpty()
        assertEquals("oshiete hoshikatta", line1Roman)
        assertEquals(line1Roman, synced.lines[1].words.joinToString("") { it.romanizedText.orEmpty() })

        val line2Roman = synced.lines[2].romanizedText.orEmpty()
        assertTrue("line2=$line2Roman", line2Roman.startsWith("「sayonara」 to"))
        assertFalse("line2=$line2Roman", line2Roman.contains("  "))
        assertEquals(line2Roman, synced.lines[2].words.joinToString("") { it.romanizedText.orEmpty() })
    }
}
