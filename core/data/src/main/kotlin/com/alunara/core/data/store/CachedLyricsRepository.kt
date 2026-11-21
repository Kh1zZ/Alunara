package com.alunara.core.data.store

import com.alunara.core.data.db.LyricsCacheDao
import com.alunara.core.data.db.LyricsCacheEntity
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.lyrics.LrcParser
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.Track
import java.util.Locale

/**
 * Room-backed lyrics cache wrapper enforcing positive TTL, negative-miss caching, and bounded entries.
 */
class CachedLyricsRepository(
    private val delegate: LyricsEngine,
    private val cacheDao: LyricsCacheDao,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : LyricsEngine {
    override suspend fun fetchLyrics(track: Track): LyricsDocument {
        val now = nowEpochMs()
        val existing = cacheDao.getEntry(track.id)
        if (existing != null && existing.expiresAtEpochMs > now) {
            return existing.toDocument()
        }

        val fetched = delegate.fetchLyrics(track)
        cacheDao.deleteExpired(now)
        when (fetched) {
            is LyricsDocument.Synced -> {
                cacheDao.upsertEntry(
                    LyricsCacheEntity(
                        trackId = track.id,
                        provider = fetched.provider.name,
                        format = FORMAT_LRC,
                        payload = serializeSyncedLines(fetched.lines),
                        fetchedAtEpochMs = now,
                        expiresAtEpochMs = now + POSITIVE_TTL_MS,
                        isNegativeCache = false,
                    ),
                )
                cacheDao.deleteOldest(MAX_CACHED_LYRICS_ENTRIES)
            }

            is LyricsDocument.Plain -> {
                cacheDao.upsertEntry(
                    LyricsCacheEntity(
                        trackId = track.id,
                        provider = fetched.provider.name,
                        format = FORMAT_PLAIN,
                        payload = fetched.text,
                        fetchedAtEpochMs = now,
                        expiresAtEpochMs = now + POSITIVE_TTL_MS,
                        isNegativeCache = false,
                    ),
                )
                cacheDao.deleteOldest(MAX_CACHED_LYRICS_ENTRIES)
            }

            is LyricsDocument.Unavailable -> {
                cacheDao.upsertEntry(
                    LyricsCacheEntity(
                        trackId = track.id,
                        provider = "",
                        format = FORMAT_NONE,
                        payload = fetched.reason,
                        fetchedAtEpochMs = now,
                        expiresAtEpochMs = now + NEGATIVE_TTL_MS,
                        isNegativeCache = true,
                    ),
                )
            }
        }
        return fetched
    }

    private fun LyricsCacheEntity.toDocument(): LyricsDocument {
        if (isNegativeCache || format == FORMAT_NONE) {
            return LyricsDocument.Unavailable(payload.ifBlank { "Lyrics unavailable" })
        }
        val providerId =
            runCatching { LyricsProviderId.valueOf(provider) }
                .getOrDefault(LyricsProviderId.LRCLIB)
        return when (format) {
            FORMAT_LRC -> LrcParser.parse(providerId, payload)
            else -> LyricsDocument.Plain(providerId, payload)
        }
    }

    private fun serializeSyncedLines(lines: List<SyncedLyricLine>): String =
        lines.joinToString("\n") { line ->
            val safeMs = line.startTimeMs.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val millis = safeMs % 1000L
            String.format(Locale.ROOT, "[%02d:%02d.%03d]%s", minutes, seconds, millis, line.text)
        }

    companion object {
        private const val FORMAT_LRC = "LRC"
        private const val FORMAT_PLAIN = "PLAIN"
        private const val FORMAT_NONE = "NONE"
        private const val POSITIVE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val NEGATIVE_TTL_MS = 10L * 60L * 1000L
        private const val MAX_CACHED_LYRICS_ENTRIES = 300
    }
}
