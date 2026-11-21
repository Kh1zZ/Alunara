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
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parse(
        provider: LyricsProviderId,
        rawPayload: String,
    ): LyricsDocument {
        if (rawPayload.isBlank()) {
            return LyricsDocument.Unavailable("Empty lyrics payload")
        }

        val syncedLines = mutableListOf<SyncedLyricLine>()
        val plainLines = mutableListOf<String>()

        rawPayload.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach
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
            val words = parseWordSegments(bodyRaw)
            val text =
                if (words.isNotEmpty()) {
                    words.joinToString(separator = "") { it.text }.trim()
                } else {
                    decodeHtmlEntities(bodyRaw.trim())
                }
            if (text.isEmpty()) return@forEach

            lineMatches.forEach { match ->
                val timeMs = parseTimestampMs(match) ?: return@forEach
                syncedLines += SyncedLyricLine(startTimeMs = timeMs, text = text, words = words)
            }
        }

        return when {
            syncedLines.isNotEmpty() -> {
                LyricsDocument.Synced(
                    provider = provider,
                    lines = syncedLines.sortedBy { it.startTimeMs },
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
     * Detects syllable-fragmented lyrics where words were broken into 1–3 char tokens
     * (e.g., "Ha ri- ha ri ber u lang" -> average token length < 3.4).
     */
    fun isSyllableFragmented(doc: LyricsDocument.Synced): Boolean {
        var totalChars = 0
        var totalTokens = 0
        doc.lines.forEach { line ->
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
     * Reconstructs whole words (preserving each merged word's earliest syllable timestamp)
     * by matching concatenated syllable runs against clean reference lyrics vocabulary.
     */
    fun repairWordBoundaries(
        doc: LyricsDocument.Synced,
        referenceTexts: List<String>,
    ): LyricsDocument.Synced {
        if (referenceTexts.isEmpty()) return doc
        val refVocab = buildReferenceVocabulary(referenceTexts)
        if (refVocab.isEmpty()) return doc

        val repairedLines =
            doc.lines.map { line ->
                if (line.hasWordSync) {
                    val trimmedWords =
                        line.words
                            .mapNotNull { w ->
                                val t = w.text.trim()
                                if (t.isEmpty()) null else SyncedLyricWord(w.startTimeMs, t)
                            }
                    val merged = mergeTokensWithVocab(trimmedWords, refVocab)
                    val spaced =
                        merged.mapIndexed { idx, word ->
                            if (idx < merged.lastIndex) word.copy(text = "${word.text} ") else word
                        }
                    val rebuiltText = spaced.joinToString(separator = "") { it.text }.trim()
                    line.copy(text = rebuiltText, words = spaced)
                } else if (isSyllableFragmented(doc)) {
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
            out += SyncedLyricWord(startTimeMs = tokens[i].startTimeMs, text = bestWord)
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

    private fun parseWordSegments(body: String): List<SyncedLyricWord> {
        val matches = WORD_TIMESTAMP_REGEX.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        val rawWords = mutableListOf<SyncedLyricWord>()
        for (i in matches.indices) {
            val current = matches[i]
            val startMs = parseTimestampMs(current) ?: continue
            val sliceStart = current.range.last + 1
            val sliceEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            if (sliceStart > sliceEnd) continue
            val wordText = decodeHtmlEntities(body.substring(sliceStart, sliceEnd))
            if (wordText.isNotEmpty()) {
                rawWords += SyncedLyricWord(startTimeMs = startMs, text = wordText)
            }
        }

        if (rawWords.size <= 1) return rawWords
        val merged = ArrayList<SyncedLyricWord>(rawWords.size)
        rawWords.forEach { current ->
            val prev = merged.lastOrNull()
            if (prev != null && prev.text.trimEnd().endsWith("-")) {
                merged[merged.lastIndex] = prev.copy(text = "${prev.text.trimEnd()}${current.text}")
            } else {
                merged += current
            }
        }
        return merged
    }

    private fun decodeHtmlEntities(input: String): String =
        input
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

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
