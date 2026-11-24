package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord

/**
 * Parses standard line-synced LRC (`[mm:ss.xx]`) and Enhanced LRC word-synced (`<mm:ss.xx>`)
 * payloads with safe fallback to plain lyrics or unavailable status.
 * Includes syllable-boundary reconstruction when ELRC/KRC feeds split words into spaced syllables.
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val METADATA_TAG_REGEX = Regex("""^\[(ti|ar|al|au|length|by|offset|re|ve):.*]$""", RegexOption.IGNORE_CASE)
    private val OFFSET_TAG_REGEX = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NUMERIC_HEX_ENTITY_REGEX = Regex("""&#[xX]([0-9a-fA-F]+);""")
    private val NUMERIC_DEC_ENTITY_REGEX = Regex("""&#(\d+);""")

    fun parse(
        provider: LyricsProviderId,
        rawPayload: String,
    ): LyricsDocument {
        if (rawPayload.isBlank()) {
            return LyricsDocument.Unavailable("Empty lyrics payload")
        }

        val syncedLines = mutableListOf<SyncedLyricLine>()
        val plainLines = mutableListOf<String>()
        var globalOffsetMs = 0L

        rawPayload.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach
            val offsetMatch = OFFSET_TAG_REGEX.matchEntire(trimmed)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            if (METADATA_TAG_REGEX.matches(trimmed)) return@forEach

            val lineMatches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (lineMatches.isEmpty()) {
                val words = parseWordSegments(trimmed)
                if (words.isNotEmpty()) {
                    val text = words.joinToString(separator = "") { it.text }.trim()
                    if (text.isNotEmpty()) {
                        syncedLines +=
                            SyncedLyricLine(
                                startTimeMs = words.first().startTimeMs,
                                text = text,
                                words = words,
                            )
                    }
                } else {
                    plainLines += decodeHtmlEntities(trimmed)
                }
                return@forEach
            }

            val bodyRaw = TIMESTAMP_REGEX.replace(trimmed, "")
            val lineStartTimes = lineMatches.mapNotNull(::parseTimestampMs)
            if (lineStartTimes.isEmpty()) return@forEach
            val firstLineTimeMs = lineStartTimes.first()
            val words = parseWordSegments(bodyRaw, fallbackStartTimeMs = firstLineTimeMs)
            val text =
                if (words.isNotEmpty()) {
                    words.joinToString(separator = "") { it.text }.trim()
                } else {
                    decodeHtmlEntities(bodyRaw.trim())
                }
            if (text.isEmpty()) return@forEach

            val hasLeadingPrefixWord = words.isNotEmpty() && words.first().startTimeMs == firstLineTimeMs &&
                WORD_TIMESTAMP_REGEX.find(bodyRaw)?.range?.first?.let { it > 0 } == true &&
                bodyRaw.substring(0, WORD_TIMESTAMP_REGEX.find(bodyRaw)!!.range.first).isNotBlank()

            lineStartTimes.forEach { timeMs ->
                val wordsForLine =
                    if (hasLeadingPrefixWord && timeMs != firstLineTimeMs) {
                        val delta = timeMs - firstLineTimeMs
                        words.map { w ->
                            val shiftedStart = (w.startTimeMs + delta).coerceAtLeast(0L)
                            val shiftedEnd = w.endTimeMs?.let { (it + delta).coerceAtLeast(shiftedStart + 1L) }
                            w.copy(startTimeMs = shiftedStart, endTimeMs = shiftedEnd)
                        }
                    } else {
                        words
                    }
                syncedLines += SyncedLyricLine(startTimeMs = timeMs, text = text, words = wordsForLine)
            }
        }

        return when {
            syncedLines.isNotEmpty() -> {
                val adjustedLines =
                    if (globalOffsetMs == 0L) {
                        syncedLines
                    } else {
                        syncedLines.map { line ->
                            val shiftedLineMs = (line.startTimeMs - globalOffsetMs).coerceAtLeast(0L)
                            val shiftedWords =
                                if (line.words.isEmpty()) {
                                    emptyList()
                                } else {
                                    line.words.map { w ->
                                        val shiftedStart = (w.startTimeMs - globalOffsetMs).coerceAtLeast(0L)
                                        val shiftedEnd = w.endTimeMs?.let { (it - globalOffsetMs).coerceAtLeast(shiftedStart + 1L) }
                                        w.copy(startTimeMs = shiftedStart, endTimeMs = shiftedEnd)
                                    }
                                }
                            line.copy(startTimeMs = shiftedLineMs, words = shiftedWords)
                        }
                    }
                LyricsDocument.Synced(
                    provider = provider,
                    lines = adjustedLines.sortedBy { it.startTimeMs },
                )
            }
            plainLines.isNotEmpty() -> {
                LyricsDocument.Plain(
                    provider = provider,
                    text = plainLines.joinToString("\n"),
                )
            }
            else -> LyricsDocument.Unavailable("No parseable lyric lines")
        }
    }

    /**
     * Detects syllable-fragmented lyrics where non-CJK words were broken into 1-3 char space-separated tokens
     * (e.g., "Ha ri- ha ri ber u lang" -> average token length < 3.4).
     */
    fun isSyllableFragmented(doc: LyricsDocument.Synced): Boolean {
        var totalChars = 0
        var totalTokens = 0
        doc.lines.forEach { line ->
            if (line.text.containsCjkKanaHangul()) return@forEach
            val tokens = line.text.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            if (tokens.size >= 4) {
                totalTokens += tokens.size
                totalChars += tokens.sumOf { it.length }
            }
        }
        if (totalTokens < 8) return false
        return (totalChars.toDouble() / totalTokens.toDouble()) < 3.4
    }

    /**
     * Returns true when a synced document needs word-boundary repair — either because lines are
     * space-fragmented syllables or because non-CJK ELRC lines split single words across timed tokens.
     */
    fun needsWordBoundaryRepair(doc: LyricsDocument.Synced): Boolean {
        if (isSyllableFragmented(doc)) return true
        return doc.lines.any { line ->
            if (!line.hasWordSync || line.text.containsCjkKanaHangul()) {
                false
            } else {
                val words = line.words
                (0 until words.lastIndex).any { idx ->
                    val current = words[idx].text
                    val next = words[idx + 1].text
                    current.isNotEmpty() &&
                        next.isNotEmpty() &&
                        !current.last().isWhitespace() &&
                        !next.first().isWhitespace()
                }
            }
        }
    }

    /**
     * Reconstructs whole words (preserving each merged word's earliest syllable timestamp)
     * by matching concatenated syllable runs against clean reference lyrics vocabulary.
     * Leaves CJK/Kana/Hangul lines and already-clean word-synced documents untouched.
     */
    fun repairWordBoundaries(
        doc: LyricsDocument.Synced,
        referenceTexts: List<String>,
    ): LyricsDocument.Synced {
        if (referenceTexts.isEmpty() || !needsWordBoundaryRepair(doc)) return doc
        val refVocab = buildReferenceVocabulary(referenceTexts)
        if (refVocab.isEmpty()) return doc

        val docIsFragmented = isSyllableFragmented(doc)
        val repairedLines =
            doc.lines.map { line ->
                if (line.text.containsCjkKanaHangul()) {
                    line
                } else if (line.hasWordSync) {
                    val trimmedWords =
                        line.words
                            .mapNotNull { w ->
                                val t = w.text.trim()
                                if (t.isEmpty()) null else SyncedLyricWord(w.startTimeMs, t, endTimeMs = w.endTimeMs)
                            }
                    val merged = mergeTokensWithVocab(trimmedWords, refVocab)
                    val spaced =
                        merged.mapIndexed { idx, word ->
                            if (idx < merged.lastIndex) word.copy(text = "${word.text} ") else word
                        }
                    val rebuiltText = spaced.joinToString(separator = "") { it.text }.trim()
                    line.copy(text = rebuiltText, words = spaced)
                } else if (docIsFragmented) {
                    val rawTokens =
                        line.text
                            .trim()
                            .split(WHITESPACE_REGEX)
                            .filter { it.isNotEmpty() }
                            .map { SyncedLyricWord(line.startTimeMs, it) }
                    val merged = mergeTokensWithVocab(rawTokens, refVocab)
                    val rebuiltText = merged.joinToString(separator = " ") { it.text }
                    line.copy(text = rebuiltText)
                } else {
                    line
                }
            }

        return doc.copy(lines = repairedLines)
    }

    private fun String.containsCjkKanaHangul(): Boolean =
        any { ch ->
            ch.code in 0x3040..0x30FF ||
                ch.code in 0x31F0..0x31FF ||
                ch.code in 0x3400..0x4DBF ||
                ch.code in 0x4E00..0x9FFF ||
                ch.code in 0xAC00..0xD7A3
        }

    private fun buildReferenceVocabulary(referenceTexts: List<String>): Set<String> {
        val vocab = HashSet<String>()
        referenceTexts.forEach { raw ->
            val stripped = WORD_TIMESTAMP_REGEX.replace(TIMESTAMP_REGEX.replace(raw, " "), " ")
            stripped.split(WHITESPACE_REGEX).forEach { token ->
                val norm = normalizeToken(token)
                if (norm.length >= 2) {
                    vocab += norm
                }
            }
        }
        return vocab
    }

    private fun mergeTokensWithVocab(
        tokens: List<SyncedLyricWord>,
        refVocab: Set<String>,
    ): List<SyncedLyricWord> {
        if (tokens.size <= 1) return tokens
        val out = ArrayList<SyncedLyricWord>(tokens.size)
        var i = 0
        val n = tokens.size
        while (i < n) {
            var bestK = 1
            var bestWord = tokens[i].text
            var accRaw = ""
            var accNorm = ""
            val maxLookahead = minOf(6, n - i)
            for (k in 1..maxLookahead) {
                val piece = tokens[i + k - 1].text
                accRaw += piece
                accNorm += normalizeToken(piece)
                if (accNorm.isNotEmpty() && accNorm in refVocab) {
                    bestK = k
                    bestWord = accRaw
                }
            }
            out +=
                SyncedLyricWord(
                    startTimeMs = tokens[i].startTimeMs,
                    text = bestWord,
                    endTimeMs = tokens[i + bestK - 1].endTimeMs,
                )
            i += bestK
        }
        return out
    }

    private fun normalizeToken(token: String): String =
        buildString(token.length) {
            for (ch in token) {
                if (ch.isLetterOrDigit()) {
                    append(ch.lowercaseChar())
                }
            }
        }

    private fun parseWordSegments(
        body: String,
        fallbackStartTimeMs: Long? = null,
    ): List<SyncedLyricWord> {
        val matches = WORD_TIMESTAMP_REGEX.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        val rawWords = mutableListOf<SyncedLyricWord>()
        val firstMatchStart = matches.first().range.first
        if (firstMatchStart > 0) {
            val prefixRaw = decodeHtmlEntities(body.substring(0, firstMatchStart))
            if (prefixRaw.isNotBlank()) {
                val startMs = fallbackStartTimeMs ?: parseTimestampMs(matches.first())
                if (startMs != null) {
                    rawWords += SyncedLyricWord(startTimeMs = startMs, text = prefixRaw.trimStart())
                }
            }
        }
        for (i in matches.indices) {
            val current = matches[i]
            val startMs = parseTimestampMs(current) ?: continue
            val sliceStart = current.range.last + 1
            val sliceEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            if (sliceStart > sliceEnd) continue
            val wordText = decodeHtmlEntities(body.substring(sliceStart, sliceEnd))
            if (wordText.isNotEmpty()) {
                rawWords += SyncedLyricWord(startTimeMs = startMs, text = wordText)
            } else if (rawWords.isNotEmpty() && rawWords.last().endTimeMs == null) {
                val prev = rawWords.last()
                rawWords[rawWords.lastIndex] = prev.copy(endTimeMs = startMs.coerceAtLeast(prev.startTimeMs + 1L))
            }
        }

        if (rawWords.size <= 1) return rawWords
        val merged = ArrayList<SyncedLyricWord>(rawWords.size)
        rawWords.forEach { current ->
            val prev = merged.lastOrNull()
            if (prev != null && prev.text.trimEnd().endsWith("-")) {
                merged[merged.lastIndex] =
                    prev.copy(
                        text = "${prev.text.trimEnd()}${current.text}",
                        endTimeMs = current.endTimeMs ?: prev.endTimeMs,
                    )
            } else {
                merged += current
            }
        }
        return merged
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        var current = input
        repeat(2) {
            current = current.replace("&amp;", "&")
        }
        current =
            current
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        current =
            NUMERIC_HEX_ENTITY_REGEX.replace(current) { match ->
                val codePoint = match.groupValues[1].toIntOrNull(16)
                codePointToString(codePoint, match.value)
            }
        return NUMERIC_DEC_ENTITY_REGEX.replace(current) { match ->
            val codePoint = match.groupValues[1].toIntOrNull()
            codePointToString(codePoint, match.value)
        }
    }

    private fun codePointToString(
        codePoint: Int?,
        fallback: String,
    ): String {
        if (codePoint == null || !Character.isValidCodePoint(codePoint) || codePoint == 0) {
            return fallback
        }
        return String(Character.toChars(codePoint))
    }

    private fun parseTimestampMs(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60L) return null
        val fracRaw = match.groupValues[3]
        val millis =
            when (fracRaw.length) {
                0 -> 0L
                1 -> (fracRaw.toLongOrNull() ?: return null) * 100L
                2 -> (fracRaw.toLongOrNull() ?: return null) * 10L
                else -> fracRaw.take(3).toLongOrNull() ?: return null
            }
        return (minutes * 60_000L) + (seconds * 1_000L) + millis
    }
}
