package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine

/**
 * Pure line-synced LRC parser with safe fallback to plain lyrics or unavailable status.
 */
object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
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

            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                plainLines += trimmed
                return@forEach
            }

            val text = TIMESTAMP_REGEX.replace(trimmed, "").trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { match ->
                val timeMs = parseTimestampMs(match) ?: return@forEach
                syncedLines += SyncedLyricLine(startTimeMs = timeMs, text = text)
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
