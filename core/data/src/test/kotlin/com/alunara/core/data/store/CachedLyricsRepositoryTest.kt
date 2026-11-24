package com.alunara.core.data.store

import com.alunara.core.data.db.LyricsCacheDao
import com.alunara.core.data.db.LyricsCacheEntity
import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedLyricsRepositoryTest {
    private class FakeLyricsCacheDao : LyricsCacheDao {
        private val entries = LinkedHashMap<String, LyricsCacheEntity>()

        override suspend fun getEntry(trackId: String): LyricsCacheEntity? = entries[trackId]

        override suspend fun upsertEntry(entry: LyricsCacheEntity) {
            entries[entry.trackId] = entry
        }

        override suspend fun deleteExpired(nowEpochMs: Long) {
            entries.entries.removeIf { it.value.expiresAtEpochMs <= nowEpochMs }
        }

        override suspend fun deleteOldest(keepCount: Int) {
            if (entries.size <= keepCount) return
            val keepIds =
                entries.values
                    .sortedByDescending { it.fetchedAtEpochMs }
                    .take(keepCount)
                    .map { it.trackId }
                    .toSet()
            entries.keys.retainAll(keepIds)
        }
    }

    @Test
    fun fetchLyrics_cachesSyncedLyricsAndRespectsTtlExpiry() =
        runBlocking {
            var now = 1_000L
            var upstreamCalls = 0
            val dao = FakeLyricsCacheDao()
            val repo =
                CachedLyricsRepository(
                    delegate =
                        object : LyricsEngine {
                            override suspend fun fetchLyrics(
                                track: Track,
                                preferredProvider: LyricsProviderId?,
                            ): LyricsDocument {
                                upstreamCalls++
                                return LyricsDocument.Synced(
                                    provider = LyricsProviderId.SIMPMUSIC,
                                    lines =
                                        listOf(
                                            SyncedLyricLine(
                                                startTimeMs = 12_345L,
                                                text = "Cached lyric",
                                                words =
                                                    listOf(
                                                        SyncedLyricWord(12_345L, "Cached "),
                                                        SyncedLyricWord(12_700L, "lyric"),
                                                    ),
                                            ),
                                        ),
                                )
                            }
                        },
                    cacheDao = dao,
                    nowEpochMs = { now },
                )

            val track = Track(id = "t1", title = "Song")
            val first = repo.fetchLyrics(track)
            assertTrue(first is LyricsDocument.Synced)
            assertEquals(1, upstreamCalls)

            // Cache hit within TTL preserves word timestamps
            now += 60_000L
            val second = repo.fetchLyrics(track)
            assertTrue(second is LyricsDocument.Synced)
            assertEquals(1, upstreamCalls)
            val cachedSynced = second as LyricsDocument.Synced
            assertTrue(cachedSynced.isWordSynced)
            assertEquals(12_345L, cachedSynced.lines.first().startTimeMs)
            assertEquals("Cached lyric", cachedSynced.lines.first().text)
            assertEquals(12_700L, cachedSynced.lines.first().words[1].startTimeMs)

            // Explicit provider switch bypasses different provider cache & updates cache on success
            val switched = repo.fetchLyrics(track, LyricsProviderId.SIMPMUSIC)
            assertEquals(1, upstreamCalls)
            assertTrue(switched is LyricsDocument.Synced)

            // Expired after 8 days -> re-fetches upstream
            now += 8L * 24L * 60L * 60L * 1000L
            repo.fetchLyrics(track)
            assertEquals(2, upstreamCalls)
        }

    @Test
    fun fetchLyrics_usesShortNegativeCacheForUnavailable() =
        runBlocking {
            var now = 10_000L
            var upstreamCalls = 0
            val dao = FakeLyricsCacheDao()
            val repo =
                CachedLyricsRepository(
                    delegate =
                        object : LyricsEngine {
                            override suspend fun fetchLyrics(
                                track: Track,
                                preferredProvider: LyricsProviderId?,
                            ): LyricsDocument {
                                upstreamCalls++
                                return LyricsDocument.Unavailable("Not found")
                            }
                        },
                    cacheDao = dao,
                    nowEpochMs = { now },
                )

            val track = Track(id = "missing", title = "Missing")
            assertTrue(repo.fetchLyrics(track) is LyricsDocument.Unavailable)
            assertEquals(1, upstreamCalls)

            now += 60_000L
            assertTrue(repo.fetchLyrics(track) is LyricsDocument.Unavailable)
            assertEquals(1, upstreamCalls)

            now += 11L * 60L * 1000L
            assertTrue(repo.fetchLyrics(track) is LyricsDocument.Unavailable)
            assertEquals(2, upstreamCalls)
        }

    @Test
    fun fetchLyrics_refreshesLegacyLrcFormatToV2WithOfflineFallback() =
        runBlocking {
            val now = 50_000L
            var online = true
            var upstreamCalls = 0
            val dao = FakeLyricsCacheDao()
            dao.upsertEntry(
                LyricsCacheEntity(
                    trackId = "legacy-online",
                    provider = LyricsProviderId.SIMPMUSIC.name,
                    format = "LRC",
                    payload = "[00:01.00]<00:01.00>壊 <00:01.20>れ",
                    fetchedAtEpochMs = 1_000L,
                    expiresAtEpochMs = now + 100_000L,
                    isNegativeCache = false,
                ),
            )
            dao.upsertEntry(
                LyricsCacheEntity(
                    trackId = "legacy-offline",
                    provider = LyricsProviderId.SIMPMUSIC.name,
                    format = "LRC",
                    payload = "[00:01.00]<00:01.00>壊 <00:01.20>れ <00:01.40>な <00:01.60>い",
                    fetchedAtEpochMs = 1_000L,
                    expiresAtEpochMs = now + 100_000L,
                    isNegativeCache = false,
                ),
            )

            val repo =
                CachedLyricsRepository(
                    delegate =
                        object : LyricsEngine {
                            override suspend fun fetchLyrics(
                                track: Track,
                                preferredProvider: LyricsProviderId?,
                            ): LyricsDocument {
                                upstreamCalls++
                                if (!online) {
                                    return LyricsDocument.Unavailable("Offline")
                                }
                                return LyricsDocument.Synced(
                                    provider = LyricsProviderId.SIMPMUSIC,
                                    lines =
                                        listOf(
                                            SyncedLyricLine(
                                                startTimeMs = 1_000L,
                                                text = "壊れ",
                                                words =
                                                    listOf(
                                                        SyncedLyricWord(1_000L, "壊"),
                                                        SyncedLyricWord(1_200L, "れ"),
                                                    ),
                                            ),
                                        ),
                                )
                            }
                        },
                    cacheDao = dao,
                    nowEpochMs = { now },
                )

            val refreshed = repo.fetchLyrics(Track(id = "legacy-online", title = "Online"))
            assertTrue(refreshed is LyricsDocument.Synced)
            assertEquals(1, upstreamCalls)
            assertEquals("LRC_V2", dao.getEntry("legacy-online")?.format)

            // Subsequent read hits upgraded LRC_V2 cache without calling upstream again
            val cached = repo.fetchLyrics(Track(id = "legacy-online", title = "Online"))
            assertTrue(cached is LyricsDocument.Synced)
            assertEquals(1, upstreamCalls)

            // Offline fallback serves legacy row parsed through normalized LrcParser
            online = false
            val offlineDoc = repo.fetchLyrics(Track(id = "legacy-offline", title = "Offline"))
            assertTrue(offlineDoc is LyricsDocument.Synced)
            assertEquals(2, upstreamCalls)
            assertEquals("壊れない", (offlineDoc as LyricsDocument.Synced).lines.single().text)
        }
}
