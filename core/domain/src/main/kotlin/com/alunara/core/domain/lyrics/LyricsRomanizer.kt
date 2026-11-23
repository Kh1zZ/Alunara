package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.SyncedLyricLine
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import java.lang.ref.SoftReference
import java.util.Locale

enum class LyricScript {
    NONE,
    KOREAN,
    JAPANESE,
    CYRILLIC,
    CHINESE,
    DEVANAGARI,
    OTHER,
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
 * Multi-script offline lyrics romanizer aligned with ArchiveTune's `LyricsUtils`:
 * - Japanese (Kanji + Hiragana + Katakana): `kuromoji-ipadic` morphological tokenizer cached via
 *   `WeakReference` and reclaimed after batch romanization to protect 1 GB RAM TV budgets, with
 *   bound verb/adjective suffix & sokuon joining so phrases read naturally.
 * - Korean (Hangul): Revised Romanization using ArchiveTune's full 95-rule phonological
 *   assimilation table (`HANGUL_ROMAJA_MAP`).
 * - Russian / Slavic (Cyrillic): direct scientific/phonetic transliteration table.
 * - Chinese / Hindi / Other scripts: Android ICU `"Any-Latin; Latin-ASCII"` reflection bridge.
 */
object LyricsRomanizer {
    private val KANA_ROMAJI_MAP: Map<String, String> =
        mapOf(
            // Katakana digraphs (Yōon + foreign extensions)
            "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
            "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
            "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
            "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
            "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
            "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
            "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
            "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
            "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
            "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
            "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
            "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
            "ティ" to "ti", "ディ" to "di", "デュ" to "dyu",
            "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
            "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
            "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo",
            "シェ" to "she", "ジェ" to "je", "チェ" to "che",
            "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
            // Katakana single characters
            "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
            "ァ" to "a", "ィ" to "i", "ゥ" to "u", "ェ" to "e", "ォ" to "o",
            "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
            "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
            "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
            "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
            "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
            "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
            "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
            "ャ" to "ya", "ュ" to "yu", "ョ" to "yo",
            "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
            "ワ" to "wa", "ヲ" to "o", "ン" to "n",
            "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
            "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
            "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
            "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
            "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
            "ヴ" to "vu",
        )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> =
        mapOf(
            "cho" to
                mapOf(
                    "ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d", "ᄄ" to "tt",
                    "ᄅ" to "r", "ᄆ" to "m", "ᄇ" to "b", "ᄈ" to "pp", "ᄉ" to "s",
                    "ᄊ" to "ss", "ᄋ" to "", "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch",
                    "ᄏ" to "k", "ᄐ" to "t", "ᄑ" to "p", "ᄒ" to "h",
                ),
            "jung" to
                mapOf(
                    "ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae", "ᅥ" to "eo",
                    "ᅦ" to "e", "ᅧ" to "yeo", "ᅨ" to "ye", "ᅩ" to "o", "ᅪ" to "wa",
                    "ᅫ" to "wae", "ᅬ" to "oe", "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo",
                    "ᅰ" to "we", "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu", "ᅴ" to "eui",
                    "ᅵ" to "i",
                ),
            "jong" to
                mapOf(
                    "ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh",
                    "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh",
                    "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch",
                    "ᆫ" to "n", "ᆫᄅ" to "ll",
                    "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn", "ᆬᄆ" to "nm", "ᆬᄒ" to "nch",
                    "ᆭ" to "n", "ᆭᄋ" to "nh", "ᆭᄅ" to "nn",
                    "ᆮ" to "t", "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn", "ᆮᄆ" to "nm", "ᆮᄒ" to "th",
                    "ᆯ" to "l", "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆯᄅ" to "ll",
                    "ᆰ" to "k", "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn", "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh",
                    "ᆱ" to "m", "ᆱᄋ" to "lm", "ᆱᄂ" to "mn", "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh",
                    "ᆲ" to "p", "ᆲᄋ" to "lb", "ᆲᄂ" to "mn", "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph",
                    "ᆳ" to "t", "ᆳᄋ" to "ls", "ᆳᄂ" to "nn", "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh",
                    "ᆴ" to "t", "ᆴᄋ" to "lt", "ᆴᄂ" to "nn", "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth",
                    "ᆵ" to "p", "ᆵᄋ" to "lp", "ᆵᄂ" to "mn", "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph",
                    "ᆶ" to "l", "ᆶᄋ" to "lh", "ᆶᄂ" to "ll", "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh",
                    "ᆷ" to "m", "ᆷᄅ" to "mn",
                    "ᆸ" to "p", "ᆸᄋ" to "b", "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph",
                    "ᆹ" to "p", "ᆹᄋ" to "ps", "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh",
                    "ᆺ" to "t", "ᆺᄋ" to "s", "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh",
                    "ᆻ" to "t", "ᆻᄋ" to "ss", "ᆻᄂ" to "tn", "ᆻᄅ" to "tn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th",
                    "ᆼ" to "ng",
                    "ᆽ" to "t", "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch",
                    "ᆾ" to "t", "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch",
                    "ᆿ" to "k", "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh",
                    "ᇀ" to "t", "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th",
                    "ᇁ" to "p", "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph",
                    "ᇂ" to "t", "ᇂᄋ" to "h", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn", "ᇂᄆ" to "mm", "ᇂᄒ" to "t", "ᇂᄀ" to "k",
                ),
        )

    private val CYRILLIC_MAP: Map<Char, String> =
        mapOf(
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E", 'Ё' to "Yo",
            'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
            'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U",
            'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",
            'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya",
            'І' to "I", 'Ї' to "Yi", 'Є' to "Ye", 'Ґ' to "G",
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
            'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
            'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
            'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
            'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g",
        )

    private val BOUND_CONJUNCTIVE_PARTICLES = setOf("て", "で", "ば", "ちゃ", "じゃ", "ちゃう", "じゃう")
    private val BOUND_SUBSIDIARY_SURFACES =
        setOf("てる", "でる", "てた", "でた", "ちゃう", "じゃう", "とく", "てく", "さ", "み", "げ", "そう")

    private val EXCLUDED_OTHER_SCRIPTS =
        setOf(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED,
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.DEVANAGARI,
        )

    @Volatile
    private var kuromojiSoftRef: SoftReference<Tokenizer>? = null

    private val icuTransliteratorBridge: Any? by lazy {
        runCatching {
            val clazz = Class.forName("android.icu.text.Transliterator")
            val getInstance = clazz.getMethod("getInstance", String::class.java)
            getInstance.invoke(null, "Any-Latin; Latin-ASCII")
        }.getOrNull()
    }

    private val icuTransliterateMethod by lazy {
        runCatching {
            Class
                .forName("android.icu.text.Transliterator")
                .getMethod("transliterate", String::class.java)
        }.getOrNull()
    }

    @Synchronized
    private fun obtainKuromojiTokenizer(): Tokenizer {
        kuromojiSoftRef?.get()?.let { return it }
        return Tokenizer().also {
            kuromojiSoftRef = SoftReference(it)
        }
    }

    private fun isPureBasicLatin(text: String): Boolean = text.all { it.code < 0x0250 }

    fun attachRomanization(doc: LyricsDocument): DocumentRomanizationSummary {
        when (doc) {
            is LyricsDocument.Synced ->
                if (doc.lines.all { isPureBasicLatin(it.text) }) {
                    return DocumentRomanizationSummary(
                        document = doc,
                        hasRomanizableLines = false,
                        hasUnsupportedKanji = false,
                        coverageRatio = 0f,
                    )
                }
            is LyricsDocument.Plain ->
                if (isPureBasicLatin(doc.text)) {
                    return DocumentRomanizationSummary(
                        document = doc,
                        hasRomanizableLines = false,
                        hasUnsupportedKanji = false,
                        coverageRatio = 0f,
                    )
                }
            is LyricsDocument.Unavailable ->
                return DocumentRomanizationSummary(
                    document = doc,
                    hasRomanizableLines = false,
                    hasUnsupportedKanji = false,
                    coverageRatio = 0f,
                )
        }
        var batchTokenizer: Tokenizer? = null
        val batchProvider: () -> Tokenizer = {
            batchTokenizer ?: obtainKuromojiTokenizer().also { batchTokenizer = it }
        }
        return when (doc) {
            is LyricsDocument.Synced -> {
                val docHasJapaneseKana = doc.lines.any { looksJapanese(it.text) }
                var totalConverted = 0
                var totalKanji = 0
                var totalCjk = 0
                val updatedLines =
                    doc.lines.map { line ->
                        val res = romanize(line.text, batchProvider, assumeJapanese = docHasJapaneseKana)
                        totalConverted += res.convertedChars
                        totalKanji += res.unsupportedKanjiChars
                        totalCjk += res.totalCjkChars
                        val effectiveRoman = line.romanizedText ?: res.text.takeIf { res.hasRomanization }
                        val updatedWords =
                            if (effectiveRoman != null && line.words.isNotEmpty()) {
                                val lineIsJapanese = docHasJapaneseKana || looksJapanese(line.text)
                                line.words.mapIndexed { idx, w ->
                                    val wRes =
                                        romanize(
                                            w.text,
                                            batchProvider,
                                            assumeJapanese = lineIsJapanese,
                                        )
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
                val lines = doc.text.lines()
                val docHasJapaneseKana = lines.any { looksJapanese(it) }
                val romanizedLines =
                    lines.map { line ->
                        val res = romanize(line, batchProvider, assumeJapanese = docHasJapaneseKana)
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
    }

    fun romanize(input: String): RomanizationResult =
        romanize(input, ::obtainKuromojiTokenizer, assumeJapanese = false)

    private fun romanize(
        input: String,
        tokenizerProvider: () -> Tokenizer,
        assumeJapanese: Boolean,
    ): RomanizationResult {
        if (input.isEmpty() || isPureBasicLatin(input)) {
            return RomanizationResult(
                text = input,
                script = LyricScript.NONE,
                convertedChars = 0,
                unsupportedKanjiChars = 0,
                totalCjkChars = 0,
            )
        }

        val hasKorean = isKorean(input)
        val hasJapanese = looksJapanese(input) || (assumeJapanese && input.any(::isHanIdeograph))
        val hasCyrillic = input.any { it in CYRILLIC_MAP || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC }
        val hasHindi = input.any { it.isLetter() && Character.UnicodeScript.of(it.code) == Character.UnicodeScript.DEVANAGARI }
        val hasHanOnly = !hasJapanese && !hasKorean && input.any(::isHanIdeograph)
        val hasOtherNonLatin = input.any { it.isLetter() && Character.UnicodeScript.of(it.code) !in EXCLUDED_OTHER_SCRIPTS }

        return when {
            hasJapanese -> romanizeJapaneseWithKuromoji(input, tokenizerProvider)
            hasKorean -> romanizeKoreanArchiveTune(input)
            hasCyrillic -> romanizeCyrillic(input)
            hasHindi -> romanizeWithIcu(input, LyricScript.DEVANAGARI)
            hasHanOnly -> romanizeChineseFallbackOrKuromoji(input, tokenizerProvider)
            hasOtherNonLatin -> romanizeWithIcu(input, LyricScript.OTHER)
            else ->
                RomanizationResult(
                    text = input,
                    script = LyricScript.NONE,
                    convertedChars = 0,
                    unsupportedKanjiChars = 0,
                    totalCjkChars = 0,
                )
        }
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

            val prevToken = tokens.getOrNull(index - 1)
            val nextToken = tokens.getOrNull(index + 1)
            val reading = token.reading?.takeIf { it.isNotEmpty() && it != "*" }
            val nextReading =
                nextToken?.reading?.takeIf { it.isNotEmpty() && it != "*" }
                    ?: nextToken?.surface?.let(::hiraganaToKatakana)

            val segmentText: String
            if (reading != null) {
                val effectiveKana =
                    when {
                        (token.partOfSpeechLevel1 == "助詞" || token.partOfSpeechLevel1 == "感動詞") &&
                            !token.pronunciation.isNullOrEmpty() &&
                            token.pronunciation != "*" -> {
                            if (token.pronunciation == "ヲ") "オ" else token.pronunciation
                        }
                        token.partOfSpeechLevel1 == "助詞" && reading == "ヲ" -> "オ"
                        else -> reading
                    }
                segmentText = romanizeKanaSegment(effectiveKana, nextReading)
                converted += surface.count { !it.isWhitespace() }
            } else if (surface.any(::isKana)) {
                val normalizedKana = hiraganaToKatakana(surface)
                segmentText = romanizeKanaSegment(normalizedKana, nextReading)
                converted += surface.count { !it.isWhitespace() }
            } else if (isKorean(surface)) {
                val kr = romanizeKoreanArchiveTune(surface)
                sawKorean = true
                converted += kr.convertedChars
                segmentText = kr.text
            } else {
                val unsupportedCount = surface.count(::isHanIdeograph)
                unsupportedKanji += unsupportedCount
                segmentText =
                    if (unsupportedCount > 0) {
                        val icu = transliterateWithIcu(surface)
                        if (icu != null && icu != surface) {
                            converted += unsupportedCount
                            icu
                        } else {
                            surface
                        }
                    } else {
                        surface
                    }
            }

            val trimmed = segmentText.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val attachToPrev = shouldAttachToPreviousToken(prevToken, token)
            val needsLeadingSpace =
                sb.isNotEmpty() &&
                    sb.last() != ' ' &&
                    !attachToPrev &&
                    !isOpeningBracketOrQuote(sb.last()) &&
                    !isClosingPunctuation(trimmed.first()) &&
                    !(prevToken?.surface?.all { !it.isLetterOrDigit() && !isKana(it) && !isHanIdeograph(it) } == true && !trimmed.first().isLetterOrDigit())
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

    private fun shouldAttachToPreviousToken(
        prevToken: Token?,
        currentToken: Token,
    ): Boolean {
        if (prevToken == null) return true
        val prevSurface = prevToken.surface
        val curSurface = currentToken.surface
        if (prevSurface.isBlank()) return false
        if (prevSurface.endsWith("っ") || prevSurface.endsWith("ッ")) return true

        val prevIsConjugableStem =
            prevToken.partOfSpeechLevel1 in setOf("動詞", "形容詞", "助動詞") ||
                (prevToken.partOfSpeechLevel1 == "助詞" && prevToken.partOfSpeechLevel2 == "接続助詞")
        val curIsAux = currentToken.partOfSpeechLevel1 == "助動詞" && prevIsConjugableStem
        val curIsBoundConjParticle =
            currentToken.partOfSpeechLevel1 == "助詞" &&
                currentToken.partOfSpeechLevel2 == "接続助詞" &&
                curSurface in BOUND_CONJUNCTIVE_PARTICLES &&
                prevIsConjugableStem
        val curIsBoundSubsidiary =
            (currentToken.partOfSpeechLevel2 == "非自立" || currentToken.partOfSpeechLevel2 == "接尾") &&
                curSurface in BOUND_SUBSIDIARY_SURFACES &&
                prevIsConjugableStem
        return curIsAux || curIsBoundConjParticle || curIsBoundSubsidiary
    }

    private fun romanizeKoreanArchiveTune(input: String): RomanizationResult {
        val sb = StringBuilder(input.length * 2)
        var prevFinal: String? = null
        var converted = 0

        for (i in input.indices) {
            val ch = input[i]
            if (isHangulSyllable(ch)) {
                converted++
                val sIndex = ch.code - HANGUL_BASE
                val choIndex = sIndex / (V_COUNT * T_COUNT)
                val jungIndex = (sIndex % (V_COUNT * T_COUNT)) / T_COUNT
                val jongIndex = sIndex % T_COUNT

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                var choConsumedByContext = false
                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val contextMapped = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                    if (contextMapped != null) {
                        sb.append(contextMapped)
                        choConsumedByContext = true
                    } else {
                        sb.append(HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal)
                    }
                }

                val cho = if (choConsumedByContext) "" else (HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar)
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                sb.append(cho).append(jung)

                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    sb.append(jong)
                    prevFinal = null
                }
                sb.append(ch)
            }
        }

        if (prevFinal != null) {
            val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
            sb.append(jong)
        }

        return RomanizationResult(
            text = sb.toString(),
            script = LyricScript.KOREAN,
            convertedChars = converted,
            unsupportedKanjiChars = 0,
            totalCjkChars = converted,
        )
    }

    private fun romanizeCyrillic(input: String): RomanizationResult {
        val sb = StringBuilder(input.length * 2)
        var converted = 0
        for (ch in input) {
            val mapped = CYRILLIC_MAP[ch]
            if (mapped != null) {
                converted++
                sb.append(mapped)
            } else {
                sb.append(ch)
            }
        }
        return RomanizationResult(
            text = sb.toString(),
            script = LyricScript.CYRILLIC,
            convertedChars = converted,
            unsupportedKanjiChars = 0,
            totalCjkChars = converted,
        )
    }

    private fun romanizeChineseFallbackOrKuromoji(
        input: String,
        tokenizerProvider: () -> Tokenizer,
    ): RomanizationResult {
        val icuResult = romanizeWithIcu(input, LyricScript.CHINESE)
        if (icuResult.hasRomanization) return icuResult
        return romanizeJapaneseWithKuromoji(input, tokenizerProvider)
    }

    private fun romanizeWithIcu(
        input: String,
        script: LyricScript,
    ): RomanizationResult {
        val transliterated = transliterateWithIcu(input)
        val nonSpaceCount = input.count { it.isLetter() }
        return if (transliterated != null && !transliterated.equals(input, ignoreCase = true)) {
            RomanizationResult(
                text = transliterated,
                script = script,
                convertedChars = nonSpaceCount,
                unsupportedKanjiChars = 0,
                totalCjkChars = nonSpaceCount,
            )
        } else {
            RomanizationResult(
                text = input,
                script = script,
                convertedChars = 0,
                unsupportedKanjiChars = input.count(::isHanIdeograph),
                totalCjkChars = nonSpaceCount,
            )
        }
    }

    private fun transliterateWithIcu(input: String): String? {
        val instance = icuTransliteratorBridge ?: return null
        val method = icuTransliterateMethod ?: return null
        return runCatching { method.invoke(instance, input) as? String }.getOrNull()
    }

    private fun romanizeKanaSegment(
        rawKana: String,
        nextKatakana: String?,
    ): String {
        val katakana = hiraganaToKatakana(rawKana)
        val sb = StringBuilder(katakana.length * 2)
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed && katakana[i] == 'ッ') {
                val nextChar =
                    katakana.getOrNull(i + 1)
                        ?: nextKatakana?.firstOrNull()
                if (nextChar != null) {
                    val nextRomaji = KANA_ROMAJI_MAP[nextChar.toString()]
                    val doubler =
                        when {
                            nextRomaji == null -> null
                            nextRomaji.startsWith("ch") -> 't'
                            nextRomaji.isNotEmpty() && nextRomaji[0] !in setOf('a', 'e', 'i', 'o', 'u') -> nextRomaji[0]
                            else -> null
                        }
                    if (doubler != null) {
                        sb.append(doubler)
                    }
                }
                i += 1
                consumed = true
            }

            if (!consumed && katakana[i] == 'ー') {
                val prev = sb.lastOrNull()
                if (prev != null && prev in setOf('a', 'e', 'i', 'o', 'u')) {
                    sb.append(prev)
                }
                i += 1
                consumed = true
            }

            if (!consumed && katakana[i] == 'ン') {
                val nextChar = katakana.getOrNull(i + 1)
                val nextRomaji = nextChar?.let { KANA_ROMAJI_MAP[it.toString()] }
                if (!nextRomaji.isNullOrEmpty() && nextRomaji[0] in setOf('a', 'e', 'i', 'o', 'u', 'y')) {
                    sb.append("n'")
                } else {
                    sb.append('n')
                }
                i += 1
                consumed = true
            }

            if (!consumed) {
                val oneCharCandidate = katakana[i].toString()
                sb.append(KANA_ROMAJI_MAP[oneCharCandidate] ?: oneCharCandidate)
                i += 1
            }
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    private fun hiraganaToKatakana(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch.code in 0x3041..0x3096) {
                sb.append((ch.code + 0x60).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun looksJapanese(text: String): Boolean =
        text.any { isKana(it) || it == '々' || it == '〆' || it == 'ヶ' }

    private fun isKorean(text: String): Boolean =
        text.any(::isHangulSyllable)

    private fun isClosingPunctuation(ch: Char): Boolean =
        ch in setOf('.', ',', '!', '?', ':', ';', ')', ']', '}', '」', '』', '、', '。', '！', '？', '’', '"', '\'')

    private fun isOpeningBracketOrQuote(ch: Char): Boolean =
        ch in setOf('(', '[', '{', '「', '『', '‘', '"')

    private fun isHangulSyllable(ch: Char): Boolean = ch.code in HANGUL_BASE..0xD7A3

    private fun isKana(ch: Char): Boolean = ch.code in 0x3040..0x30FF

    private fun isHanIdeograph(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF

    private const val HANGUL_BASE = 0xAC00
    private const val V_COUNT = 21
    private const val T_COUNT = 28
}
