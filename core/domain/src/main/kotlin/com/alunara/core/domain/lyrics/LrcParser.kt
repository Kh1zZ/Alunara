package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord

/**
 * Parses standard line-synced LRC (`[mm:ss.xx]`) and Enhanced LRC word-synced (`<mm:ss.xx>`)
 * payloads with safe fallback to plain lyrics or unavailable status.
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val METADATA_TAG_REGEX = Regex("""^\[(ti|ar|al|au|length|by|offset|re|ve):.*]$""", RegexOption.IGNORE_CASE)

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

    private fun parseWordSegments(body: String): List<SyncedLyricWord> {
        val matches = WORD_TIMESTAMP_REGEX.findAll(body).toList()
        if (matches.isEmpty()) return emptyList()

        val words = mutableListOf<SyncedLyricWord>()
        for (i in matches.indices) {
            val current = matches[i]
            val startMs = parseTimestampMs(current) ?: continue
            val sliceStart = current.range.last + 1
            val sliceEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            if (sliceStart > sliceEnd) continue
            val wordText = decodeHtmlEntities(body.substring(sliceStart, sliceEnd))
            if (wordText.isNotEmpty()) {
                words += SyncedLyricWord(startTimeMs = startMs, text = wordText)
            }
        }
        return words
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
