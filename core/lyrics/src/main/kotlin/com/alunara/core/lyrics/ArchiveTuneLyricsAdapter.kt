package com.alunara.core.lyrics

import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.lyrics.LrcParser
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.ResourceBudgetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Orchestrates selectively approved ArchiveTune Lyrics providers (`lrclib`, `simpmusic`, `kugou`)
 * with bounded concurrency (max 2 concurrent requests per PRD §7.1) and safe plain-lyrics fallback.
 */
class ArchiveTuneLyricsAdapter(
    private val providers: List<LyricsProviderClient> =
        listOf(
            LrcLibProviderClient,
            SimpMusicProviderClient,
            KuGouProviderClient,
        ),
    maxConcurrentRequests: Int = ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS,
) : LyricsEngine {
    private val concurrencyLimit = maxConcurrentRequests.coerceIn(1, ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS)

    override suspend fun fetchLyrics(track: Track): LyricsDocument {
        if (track.title.isBlank() || providers.isEmpty()) {
            return LyricsDocument.Unavailable("Missing track metadata or lyrics providers")
        }

        val semaphore = Semaphore(concurrencyLimit)
        val primaryBatch = providers.take(concurrencyLimit)
        val primaryDocs = fetchBatch(primaryBatch, track, semaphore)
        selectBestDocument(primaryDocs)?.let { best ->
            if (best is LyricsDocument.Synced) return best
        }

        val fallbackBatch = providers.drop(concurrencyLimit)
        val fallbackDocs =
            if (fallbackBatch.isNotEmpty()) {
                fetchBatch(fallbackBatch, track, semaphore)
            } else {
                emptyList()
            }

        return selectBestDocument(primaryDocs + fallbackDocs)
            ?: LyricsDocument.Unavailable("No matching lyrics from configured providers")
    }

    private suspend fun fetchBatch(
        batch: List<LyricsProviderClient>,
        track: Track,
        semaphore: Semaphore,
    ): List<LyricsDocument> =
        coroutineScope {
            batch
                .map { provider ->
                    async {
                        semaphore.withPermit {
                            try {
                                provider.fetchRawLyrics(track).fold(
                                    onSuccess = { payload -> LrcParser.parse(provider.id, payload) },
                                    onFailure = { error ->
                                        if (error is CancellationException) throw error
                                        LyricsDocument.Unavailable(error.message ?: "Provider error")
                                    },
                                )
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (t: Throwable) {
                                LyricsDocument.Unavailable(t.message ?: "Provider error")
                            }
                        }
                    }
                }.awaitAll()
        }

    private fun selectBestDocument(candidates: List<LyricsDocument>): LyricsDocument? {
        val synced = candidates.firstOrNull { it is LyricsDocument.Synced }
        if (synced != null) return synced
        return candidates.firstOrNull { it is LyricsDocument.Plain }
    }
}
