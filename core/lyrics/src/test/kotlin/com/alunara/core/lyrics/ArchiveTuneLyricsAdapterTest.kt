package com.alunara.core.lyrics

import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ArchiveTuneLyricsAdapterTest {
    private val sampleTrack =
        Track(
            id = "video123",
            title = "Sample Song",
            artists = listOf(ArtistRef(null, "Sample Artist")),
            durationSeconds = 210,
        )

    private class FakeProvider(
        override val id: LyricsProviderId,
        private val delayMs: Long = 0L,
        private val activeCounter: AtomicInteger,
        private val maxObserved: AtomicInteger,
        private val resultProvider: () -> Result<String>,
    ) : LyricsProviderClient {
        override suspend fun fetchRawLyrics(track: Track): Result<String> {
            val current = activeCounter.incrementAndGet()
            maxObserved.updateAndGet { prev -> maxOf(prev, current) }
            try {
                if (delayMs > 0) delay(delayMs)
                return resultProvider()
            } finally {
                activeCounter.decrementAndGet()
            }
        }
    }

    @Test
    fun fetchLyrics_prefersSyncedOverPlainAndRespectsConcurrencyLimit() =
        runTest {
            val active = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val adapter =
                ArchiveTuneLyricsAdapter(
                    providers =
                        listOf(
                            FakeProvider(LyricsProviderId.LRCLIB, 20L, active, maxObserved) {
                                Result.success("Plain only line")
                            },
                            FakeProvider(LyricsProviderId.SIMPMUSIC, 20L, active, maxObserved) {
                                Result.success("[00:05.00]Synced line")
                            },
                            FakeProvider(LyricsProviderId.KUGOU, 20L, active, maxObserved) {
                                error("Should not need third provider once synced is found")
                            },
                        ),
                    maxConcurrentRequests = 2,
                )

            val doc = adapter.fetchLyrics(sampleTrack)
            assertTrue(doc is LyricsDocument.Synced)
            assertEquals(LyricsProviderId.SIMPMUSIC, (doc as LyricsDocument.Synced).provider)
            assertTrue("Expected max <= 2 concurrent requests, got ${maxObserved.get()}", maxObserved.get() <= 2)
        }

    @Test
    fun fetchLyrics_fallsBackToPlainWhenNoProviderHasSynced() =
        runTest {
            val active = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val adapter =
                ArchiveTuneLyricsAdapter(
                    providers =
                        listOf(
                            FakeProvider(LyricsProviderId.LRCLIB, 10L, active, maxObserved) {
                                Result.failure(IllegalStateException("404"))
                            },
                            FakeProvider(LyricsProviderId.SIMPMUSIC, 10L, active, maxObserved) {
                                Result.success("Plain text lyric")
                            },
                        ),
                )

            val doc = adapter.fetchLyrics(sampleTrack)
            assertTrue(doc is LyricsDocument.Plain)
            assertEquals("Plain text lyric", (doc as LyricsDocument.Plain).text)
        }

    @Test
    fun fetchLyrics_returnsUnavailableWhenAllProvidersFail() =
        runTest {
            val active = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val adapter =
                ArchiveTuneLyricsAdapter(
                    providers =
                        listOf(
                            FakeProvider(LyricsProviderId.LRCLIB, 0L, active, maxObserved) {
                                Result.failure(IllegalStateException("Network error"))
                            },
                        ),
                )

            val doc = adapter.fetchLyrics(sampleTrack)
            assertTrue(doc is LyricsDocument.Unavailable)
        }
}
