package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.SyncedLyricLine
import com.atilika.kuromoji.ipadic.Tokenizer
import java.lang.ref.WeakReference

enum class LyricScript {
    NONE,
    KOREAN,
    JAPANESE,
    MIXED_CJK,
}

data class RomanizationResult(
    val text: String,
    val script: LyricScript,
    val convertedChars: Int,
    val unsupportedKanjiChars: Int,
    val totalCjkChars: Int,
) {
    val hasRomanization: Boolean
        get() = convertedChars > 0

    val coverageRatio: Float
        get() = if (totalCjkChars == 0) 0f else convertedChars.toFloat() / totalCjkChars.toFloat()
}

data class DocumentRomanizationSummary(
    val document: LyricsDocument,
    val hasRomanizableLines: Boolean,
    val hasUnsupportedKanji: Boolean,
    val coverageRatio: Float,
)

/**
 * Lightweight, zero-startup-cost offline Japanese (Hiragana/Katakana -> Hepburn Romaji)
 * and Korean (Hangul -> Revised Romanization) transliterator compliant with PRD §7.3.
 * Never fabricates readings for ambiguous Kanji characters; preserves them inline and
 * reports honest coverage metrics.
 */
object LyricsRomanizer {
    private val initials =
        arrayOf(
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s",
            "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
        )
    private val medials =
        arrayOf(
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa",
            "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
        )
    private val finalsCoda =
        arrayOf(
            "", "k", "k", "k", "n", "n", "n", "t", "l", "l",
            "l", "l", "l", "l", "l", "l", "m", "p", "p", "s",
            "ss", "ng", "t", "t", "k", "t", "p", "h",
        )
    private val finalsLiaison =
        arrayOf(
            "", "g", "kk", "gs", "n", "nj", "nh", "d", "r", "lg",
            "lm", "lb", "ls", "lt", "lp", "rh", "m", "b", "bs", "s",
            "ss", "ng", "j", "ch", "k", "t", "p", "",
        )

    private val kanaDigraphs: Map<String, String> =
        mapOf(
            // Hiragana yoon + foreign extensions
            "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
            "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
            "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
            "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
            "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
            "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
            "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
            "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
            "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
            "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
            "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
            "てぃ" to "ti", "でぃ" to "di",
            "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
            // Katakana yoon + foreign extensions
            "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
            "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
            "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
            "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
            "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
            "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
            "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
            "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
            "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
            "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
            "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
            "ティ" to "ti", "ディ" to "di", "デュ" to "dyu",
            "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
            "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
            "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo",
            "シェ" to "she", "ジェ" to "je", "チェ" to "che",
            "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        )

    private val kanaSingle: Map<Char, String> =
        mapOf(
            // Hiragana
            'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
            'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
            'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
            'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
            'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
            'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
            'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
            'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
            'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
            'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
            'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
            'わ' to "wa", 'を' to "wo", 'ん' to "n",
            'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
            'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
            'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
            'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
            'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
            'ゔ' to "vu",
            // Katakana
            'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
            'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
            'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
            'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
            'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
            'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
            'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
            'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
            'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
            'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo",
            'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
            'ワ' to "wa", 'ヲ' to "wo", 'ン' to "n",
            'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
            'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
            'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
            'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
            'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
            'ヴ' to "vu",
        )

    @Volatile
    private var kuromojiWeakRef: WeakReference<Tokenizer>? = null

    @Synchronized
    private fun obtainKuromojiTokenizer(): Tokenizer {
        kuromojiWeakRef?.get()?.let { return it }
        return Tokenizer().also {
            kuromojiWeakRef = WeakReference(it)
        }
    }

    fun attachRomanization(doc: LyricsDocument): DocumentRomanizationSummary {
        var batchTokenizer: Tokenizer? = null
        val batchProvider: () -> Tokenizer = {
            batchTokenizer ?: obtainKuromojiTokenizer().also { batchTokenizer = it }
        }
        try {
            return when (doc) {
                is LyricsDocument.Synced -> {
                    var totalConverted = 0
                    var totalKanji = 0
                    var totalCjk = 0
                    val updatedLines =
                        doc.lines.map { line ->
                            val res = romanize(line.text, batchProvider)
                            totalConverted += res.convertedChars
                            totalKanji += res.unsupportedKanjiChars
                            totalCjk += res.totalCjkChars
                            val effectiveRoman = line.romanizedText ?: res.text.takeIf { res.hasRomanization }
                            val updatedWords =
                                if (effectiveRoman != null && line.words.isNotEmpty()) {
                                    line.words.mapIndexed { idx, w ->
                                        val wRes = romanize(w.text, batchProvider)
                                        val wRoman =
                                            if (wRes.hasRomanization) {
                                                val suffix = if (idx < line.words.lastIndex && !wRes.text.endsWith(" ")) " " else ""
                                                wRes.text.trim() + suffix
                                            } else {
                                                w.text
                                            }
                                        w.copy(romanizedText = wRoman)
                                    }
                                } else {
                                    line.words
                                }
                            line.copy(romanizedText = effectiveRoman, words = updatedWords)
                        }
                    val updatedDoc = doc.copy(lines = updatedLines)
                    DocumentRomanizationSummary(
                        document = updatedDoc,
                        hasRomanizableLines = updatedLines.any { !it.romanizedText.isNullOrBlank() },
                        hasUnsupportedKanji = totalKanji > 0,
                        coverageRatio = if (totalCjk == 0) 0f else totalConverted.toFloat() / totalCjk.toFloat(),
                    )
                }

                is LyricsDocument.Plain -> {
                    val romanizedLines =
                        doc.text.lines().map { line ->
                            val res = romanize(line, batchProvider)
                            res to res.text
                        }
                    val totalConverted = romanizedLines.sumOf { it.first.convertedChars }
                    val totalKanji = romanizedLines.sumOf { it.first.unsupportedKanjiChars }
                    val totalCjk = romanizedLines.sumOf { it.first.totalCjkChars }
                    val hasRoman = romanizedLines.any { it.first.hasRomanization }
                    val updatedDoc =
                        if (hasRoman) {
                            doc.copy(romanizedText = romanizedLines.joinToString("\n") { it.second })
                        } else {
                            doc
                        }
                    DocumentRomanizationSummary(
                        document = updatedDoc,
                        hasRomanizableLines = hasRoman,
                        hasUnsupportedKanji = totalKanji > 0,
                        coverageRatio = if (totalCjk == 0) 0f else totalConverted.toFloat() / totalCjk.toFloat(),
                    )
                }

                is LyricsDocument.Unavailable ->
                    DocumentRomanizationSummary(
                        document = doc,
                        hasRomanizableLines = false,
                        hasUnsupportedKanji = false,
                        coverageRatio = 0f,
                    )
            }
        } finally {
            if (batchTokenizer != null) {
                batchTokenizer = null
                System.gc()
            }
        }
    }

    fun romanize(input: String): RomanizationResult = romanize(input, ::obtainKuromojiTokenizer)

    private fun romanize(
        input: String,
        tokenizerProvider: () -> Tokenizer,
    ): RomanizationResult {
        if (input.isEmpty()) {
            return RomanizationResult(
                text = input,
                script = LyricScript.NONE,
                convertedChars = 0,
                unsupportedKanjiChars = 0,
                totalCjkChars = 0,
            )
        }

        if (input.any(::isHanIdeograph)) {
            return romanizeJapaneseWithKuromoji(input, tokenizerProvider)
        }

        return romanizeKanaAndHangul(input)
    }

    private fun romanizeJapaneseWithKuromoji(
        input: String,
        tokenizerProvider: () -> Tokenizer,
    ): RomanizationResult {
        val tokens = tokenizerProvider().tokenize(input)
        val sb = StringBuilder(input.length * 2)
        var converted = 0
        var unsupportedKanji = 0
        var sawKorean = false

        tokens.forEachIndexed { index, token ->
            val surface = token.surface
            if (surface.isBlank()) {
                if (sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                }
                return@forEachIndexed
            }

            val reading = token.reading?.takeIf { it.isNotEmpty() && it != "*" }
            val nextToken = tokens.getOrNull(index + 1)
            val nextReading =
                nextToken?.reading?.takeIf { it.isNotEmpty() && it != "*" }
                    ?: nextToken?.surface

            val segmentText: String
            if (reading != null) {
                val effectiveKana =
                    when {
                        token.partOfSpeechLevel1 == "助詞" && !token.pronunciation.isNullOrEmpty() && token.pronunciation != "*" -> {
                            if (token.pronunciation == "ヲ") "オ" else token.pronunciation
                        }
                        else -> reading
                    }
                segmentText = romanizeKanaSegment(effectiveKana, nextReading)
                converted += surface.count { !it.isWhitespace() }
            } else {
                val fallback = romanizeKanaAndHangul(surface)
                if (fallback.script == LyricScript.KOREAN || fallback.script == LyricScript.MIXED_CJK) {
                    sawKorean = true
                }
                converted += fallback.convertedChars
                unsupportedKanji += surface.count(::isHanIdeograph)
                segmentText = fallback.text
            }

            val trimmed = segmentText.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val needsLeadingSpace =
                sb.isNotEmpty() &&
                    sb.last() != ' ' &&
                    !isOpeningBracketOrQuote(sb.last()) &&
                    !isClosingPunctuation(trimmed.first())
            if (needsLeadingSpace) {
                sb.append(' ')
            }
            sb.append(trimmed)
        }

        return RomanizationResult(
            text = sb.toString().trim(),
            script = if (sawKorean) LyricScript.MIXED_CJK else LyricScript.JAPANESE,
            convertedChars = converted,
            unsupportedKanjiChars = unsupportedKanji,
            totalCjkChars = converted + unsupportedKanji,
        )
    }

    private fun romanizeKanaSegment(
        kana: String,
        nextKana: String?,
    ): String {
        val sb = StringBuilder(kana.length * 2)
        var i = 0
        while (i < kana.length) {
            val ch = kana[i]
            when {
                ch == 'っ' || ch == 'ッ' -> {
                    val nextRomaji = peekKanaRomaji(kana, i + 1) ?: nextKana?.let { peekKanaRomaji(it, 0) }
                    if (!nextRomaji.isNullOrEmpty()) {
                        val doubler = if (nextRomaji.startsWith("ch")) 't' else nextRomaji[0]
                        if (doubler in 'a'..'z' && doubler !in setOf('a', 'e', 'i', 'o', 'u')) {
                            sb.append(doubler)
                        }
                    }
                    i++
                }

                ch == 'ー' -> {
                    val prev = sb.lastOrNull()
                    if (prev != null && prev in setOf('a', 'e', 'i', 'o', 'u')) {
                        sb.append(prev)
                    }
                    i++
                }

                ch == 'ん' || ch == 'ン' -> {
                    val nextRomaji = peekKanaRomaji(kana, i + 1)
                    if (!nextRomaji.isNullOrEmpty() && nextRomaji[0] in setOf('a', 'e', 'i', 'o', 'u', 'y')) {
                        sb.append("n'")
                    } else {
                        sb.append('n')
                    }
                    i++
                }

                isKana(ch) -> {
                    val digraph = if (i + 1 < kana.length) kana.substring(i, i + 2) else null
                    val diRomaji = if (digraph != null) kanaDigraphs[digraph] else null
                    if (diRomaji != null) {
                        sb.append(diRomaji)
                        i += 2
                    } else {
                        sb.append(kanaSingle[ch] ?: ch.toString())
                        i++
                    }
                }

                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun isClosingPunctuation(ch: Char): Boolean =
        ch in setOf('.', ',', '!', '?', ':', ';', ')', ']', '}', '」', '』', '、', '。', '！', '？', '’', '"', '\'')

    private fun isOpeningBracketOrQuote(ch: Char): Boolean =
        ch in setOf('(', '[', '{', '「', '『', '‘', '"')

    private fun romanizeKanaAndHangul(input: String): RomanizationResult {

        val sb = StringBuilder(input.length * 2)
        var converted = 0
        var kanjiCount = 0
        var sawKorean = false
        var sawJapanese = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]
            when {
                isHangulSyllable(ch) -> {
                    sawKorean = true
                    converted++
                    val sIndex = ch.code - HANGUL_BASE
                    val lIdx = sIndex / (V_COUNT * T_COUNT)
                    val vIdx = (sIndex % (V_COUNT * T_COUNT)) / T_COUNT
                    val tIdx = sIndex % T_COUNT

                    sb.append(initials[lIdx])
                    sb.append(medials[vIdx])

                    if (tIdx != 0) {
                        val nextChar = input.getOrNull(i + 1)
                        val nextStartsWithVowel =
                            nextChar != null &&
                                isHangulSyllable(nextChar) &&
                                ((nextChar.code - HANGUL_BASE) / (V_COUNT * T_COUNT)) == SILENT_IEUNG_INITIAL
                        if (nextStartsWithVowel && finalsLiaison[tIdx].isNotEmpty()) {
                            sb.append(finalsLiaison[tIdx])
                        } else {
                            sb.append(finalsCoda[tIdx])
                        }
                    }
                    i++
                }

                ch == 'っ' || ch == 'ッ' -> {
                    sawJapanese = true
                    converted++
                    val nextRomaji = peekKanaRomaji(input, i + 1)
                    if (!nextRomaji.isNullOrEmpty()) {
                        val doubler =
                            if (nextRomaji.startsWith("ch")) {
                                't'
                            } else {
                                nextRomaji[0]
                            }
                        if (doubler in 'a'..'z' && doubler !in setOf('a', 'e', 'i', 'o', 'u')) {
                            sb.append(doubler)
                        }
                    }
                    i++
                }

                ch == 'ー' -> {
                    sawJapanese = true
                    converted++
                    val prev = sb.lastOrNull()
                    if (prev != null && prev in setOf('a', 'e', 'i', 'o', 'u')) {
                        sb.append(prev)
                    }
                    i++
                }

                ch == 'ん' || ch == 'ン' -> {
                    sawJapanese = true
                    converted++
                    val nextRomaji = peekKanaRomaji(input, i + 1)
                    if (!nextRomaji.isNullOrEmpty() && nextRomaji[0] in setOf('a', 'e', 'i', 'o', 'u', 'y')) {
                        sb.append("n'")
                    } else {
                        sb.append('n')
                    }
                    i++
                }

                isKana(ch) -> {
                    sawJapanese = true
                    converted++
                    val digraph = if (i + 1 < input.length) input.substring(i, i + 2) else null
                    val diRomaji = if (digraph != null) kanaDigraphs[digraph] else null
                    if (diRomaji != null) {
                        converted++
                        sb.append(diRomaji)
                        i += 2
                    } else {
                        val single = kanaSingle[ch]
                        if (single != null) {
                            sb.append(single)
                        } else {
                            sb.append(ch)
                        }
                        i++
                    }
                }

                isHanIdeograph(ch) -> {
                    kanjiCount++
                    sb.append(ch)
                    i++
                }

                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }

        val script =
            when {
                sawKorean && sawJapanese -> LyricScript.MIXED_CJK
                sawKorean -> LyricScript.KOREAN
                sawJapanese || kanjiCount > 0 -> LyricScript.JAPANESE
                else -> LyricScript.NONE
            }

        return RomanizationResult(
            text = sb.toString(),
            script = script,
            convertedChars = converted,
            unsupportedKanjiChars = kanjiCount,
            totalCjkChars = converted + kanjiCount,
        )
    }

    private fun peekKanaRomaji(
        input: String,
        index: Int,
    ): String? {
        if (index >= input.length) return null
        val digraph = if (index + 1 < input.length) input.substring(index, index + 2) else null
        if (digraph != null) {
            kanaDigraphs[digraph]?.let { return it }
        }
        return kanaSingle[input[index]]
    }

    private fun isHangulSyllable(ch: Char): Boolean = ch.code in HANGUL_BASE..0xD7A3

    private fun isKana(ch: Char): Boolean = ch.code in 0x3040..0x30FF

    private fun isHanIdeograph(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF

    private const val HANGUL_BASE = 0xAC00
    private const val V_COUNT = 21
    private const val T_COUNT = 28
    private const val SILENT_IEUNG_INITIAL = 11
}
