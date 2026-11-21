package com.alunara.core.lyrics

import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.lyrics.LrcParser
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.ResourceBudgetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Orchestrates selectively approved ArchiveTune Lyrics providers (`simpmusic`, `lrclib`, `kugou`)
 * with bounded concurrency (max 2 concurrent requests per PRD §7.1) and strict priority:
 * word-sync -> line-sync -> plain.
 */
class ArchiveTuneLyricsAdapter(
    private val providers: List<LyricsProviderClient> =
        listOf(
            SimpMusicProviderClient,
            LrcLibProviderClient,
            KuGouProviderClient,
        ),
    maxConcurrentRequests: Int = ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS,
) : LyricsEngine {
    private val concurrencyLimit = maxConcurrentRequests.coerceIn(1, ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS)

    override suspend fun fetchLyrics(
        track: Track,
        preferredProvider: LyricsProviderId?,
    ): LyricsDocument {
        if (track.title.isBlank() || providers.isEmpty()) {
            return LyricsDocument.Unavailable("Missing track metadata or lyrics providers")
        }

        if (preferredProvider != null) {
            val target =
                providers.firstOrNull { it.id == preferredProvider }
                    ?: return LyricsDocument.Unavailable("Provider ${preferredProvider.name} not configured")
            return fetchSingleProvider(target, track)
        }

        val semaphore = Semaphore(concurrencyLimit)
        val primaryBatch = providers.take(concurrencyLimit)
        val primaryDocs = fetchBatch(primaryBatch, track, semaphore)
        val primaryBest = selectBestDocument(primaryDocs)
        if (primaryBest is LyricsDocument.Synced && primaryBest.isWordSynced) {
            return primaryBest
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
                            fetchSingleProvider(provider, track)
                        }
                    }
                }.awaitAll()
        }

    private suspend fun fetchSingleProvider(
        provider: LyricsProviderClient,
        track: Track,
    ): LyricsDocument =
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

    private fun selectBestDocument(candidates: List<LyricsDocument>): LyricsDocument? {
        val wordSynced = candidates.firstOrNull { it is LyricsDocument.Synced && it.isWordSynced }
        if (wordSynced != null) return wordSynced
        val lineSynced = candidates.firstOrNull { it is LyricsDocument.Synced }
        if (lineSynced != null) return lineSynced
        return candidates.firstOrNull { it is LyricsDocument.Plain }
    }
}
