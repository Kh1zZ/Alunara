package com.alunara.core.lyrics

import com.alunara.core.domain.contract.LyricsEngine
import com.alunara.core.domain.lyrics.LrcParser
import com.alunara.core.domain.model.LyricsDocument
import com.alunara.core.domain.model.LyricsProviderId
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.policy.ResourceBudgetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates selectively approved ArchiveTune Lyrics providers (`simpmusic`, `lrclib`, `kugou`)
 * with:
 * - Adaptive provider ranking based on session reliability scores.
 * - True hedged request scheduling bounded by [ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS] (<= 2 concurrent).
 * - Duration mismatch filtering so wrong-version synced lyrics do not win.
 * - Syllable-to-word repair against clean reference lyrics.
 */
class ArchiveTuneLyricsAdapter(
    private val providers: List<LyricsProviderClient> =
        listOf(
            SimpMusicProviderClient,
            LrcLibProviderClient,
            KuGouProviderClient,
        ),
    maxConcurrentRequests: Int = ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS,
    private val hedgeDelayMs: Long = DEFAULT_HEDGE_DELAY_MS,
) : LyricsEngine {
    private val concurrencyLimit = maxConcurrentRequests.coerceIn(1, ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS)
    private val providerScores = ConcurrentHashMap<LyricsProviderId, Int>()

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
            val primaryDoc = fetchSingleProvider(target, track)
            if (primaryDoc is LyricsDocument.Synced &&
                (primaryDoc.isWordSynced || LrcParser.isSyllableFragmented(primaryDoc)) &&
                target.id != LyricsProviderId.LRCLIB
            ) {
                val refProvider = providers.firstOrNull { it.id == LyricsProviderId.LRCLIB }
                val refDocs = if (refProvider != null) listOf(fetchSingleProvider(refProvider, track)) else emptyList()
                val refTexts = extractCleanReferenceTexts(refDocs, track)
                return LrcParser.repairWordBoundaries(primaryDoc, refTexts)
            }
            return primaryDoc
        }

        val ordered = orderProvidersByScore(providers)
        val semaphore = Semaphore(concurrencyLimit)

        val rawDocs =
            coroutineScope {
                val firstDeferred =
                    async {
                        semaphore.withPermit { fetchSingleProvider(ordered[0], track) }
                    }
                val fastFirst =
                    if (hedgeDelayMs > 0L) {
                        withTimeoutOrNull(hedgeDelayMs) { firstDeferred.await() }
                    } else {
                        null
                    }

                if (fastFirst != null && isCleanWordSyncedMatch(fastFirst, track)) {
                    recordScore(ordered[0].id, +2)
                    return@coroutineScope listOf(fastFirst)
                }

                val collected = mutableListOf<LyricsDocument>()
                if (ordered.size >= 2) {
                    val secondDeferred =
                        async {
                            semaphore.withPermit { fetchSingleProvider(ordered[1], track) }
                        }
                    val doc0 = fastFirst ?: firstDeferred.await()
                    val doc1 = secondDeferred.await()
                    collected += doc0
                    collected += doc1
                } else {
                    collected += (fastFirst ?: firstDeferred.await())
                }

                // Only escalate to remaining providers when neither of the first two gave clean word-sync
                val repairedSoFar = repairDocs(collected, track)
                val bestSoFar = selectBestDocument(repairedSoFar, track)
                if (!(bestSoFar is LyricsDocument.Synced && isCleanWordSyncedMatch(bestSoFar, track))) {
                    for (extraProvider in ordered.drop(2)) {
                        val extraDoc = semaphore.withPermit { fetchSingleProvider(extraProvider, track) }
                        collected += extraDoc
                        val repairedNow = repairDocs(collected, track)
                        val bestNow = selectBestDocument(repairedNow, track)
                        if (bestNow is LyricsDocument.Synced && isCleanWordSyncedMatch(bestNow, track)) {
                            break
                        }
                    }
                }
                collected
            }

        val repairedDocs = repairDocs(rawDocs, track)
        repairedDocs.forEach { doc ->
            when {
                doc is LyricsDocument.Synced && !isDurationMismatched(doc, track) -> recordScore(doc.provider, +2)
                doc is LyricsDocument.Plain -> recordScore(doc.provider, +1)
            }
        }

        return selectBestDocument(repairedDocs, track)
            ?: LyricsDocument.Unavailable("No matching lyrics from configured providers")
    }

    private fun orderProvidersByScore(list: List<LyricsProviderClient>): List<LyricsProviderClient> =
        list.withIndex().sortedWith(
            compareByDescending<IndexedValue<LyricsProviderClient>> { providerScores[it.value.id] ?: 0 }
                .thenBy { it.index },
        ).map { it.value }

    private fun recordScore(
        providerId: LyricsProviderId,
        delta: Int,
    ) {
        providerScores.compute(providerId) { _, prev ->
            ((prev ?: 0) + delta).coerceIn(-4, 8)
        }
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
                    recordScore(provider.id, -1)
                    LyricsDocument.Unavailable(error.message ?: "Provider error")
                },
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            recordScore(provider.id, -1)
            LyricsDocument.Unavailable(t.message ?: "Provider error")
        }

    private fun repairDocs(
        docs: List<LyricsDocument>,
        track: Track,
    ): List<LyricsDocument> {
        val refTexts = extractCleanReferenceTexts(docs, track)
        return docs.map { doc ->
            if (doc is LyricsDocument.Synced &&
                !isDurationMismatched(doc, track) &&
                (doc.isWordSynced || LrcParser.isSyllableFragmented(doc))
            ) {
                LrcParser.repairWordBoundaries(doc, refTexts)
            } else {
                doc
            }
        }
    }

    private fun extractCleanReferenceTexts(
        docs: List<LyricsDocument>,
        track: Track,
    ): List<String> =
        docs.flatMap { doc ->
            when (doc) {
                is LyricsDocument.Synced ->
                    if (!isDurationMismatched(doc, track) && !LrcParser.isSyllableFragmented(doc)) {
                        doc.lines.map { it.text }
                    } else {
                        emptyList()
                    }
                is LyricsDocument.Plain -> doc.text.lines()
                is LyricsDocument.Unavailable -> emptyList()
            }
        }

    private fun selectBestDocument(
        candidates: List<LyricsDocument>,
        track: Track,
    ): LyricsDocument? {
        val validCandidates =
            candidates.filter { doc ->
                doc !is LyricsDocument.Unavailable &&
                    !(doc is LyricsDocument.Synced && isDurationMismatched(doc, track))
            }
        if (validCandidates.isEmpty()) return null

        return validCandidates.maxByOrNull { doc ->
            scoreDocument(doc, track)
        }
    }

    private fun scoreDocument(
        doc: LyricsDocument,
        track: Track,
    ): Int =
        when (doc) {
            is LyricsDocument.Synced -> {
                if (isDurationMismatched(doc, track)) {
                    -1_000
                } else {
                    val base =
                        when {
                            doc.isWordSynced && !LrcParser.isSyllableFragmented(doc) -> 400
                            !doc.isWordSynced && !LrcParser.isSyllableFragmented(doc) -> 300
                            else -> 200
                        }
                    val lineBonus = doc.lines.size.coerceAtMost(80)
                    val reliabilityBonus = (providerScores[doc.provider] ?: 0) * 2
                    base + lineBonus + reliabilityBonus
                }
            }
            is LyricsDocument.Plain -> 100 + doc.text.lines().count { it.isNotBlank() }.coerceAtMost(40)
            is LyricsDocument.Unavailable -> 0
        }

    private fun isCleanWordSyncedMatch(
        doc: LyricsDocument,
        track: Track,
    ): Boolean =
        doc is LyricsDocument.Synced &&
            doc.isWordSynced &&
            !LrcParser.isSyllableFragmented(doc) &&
            !isDurationMismatched(doc, track)

    private fun isDurationMismatched(
        doc: LyricsDocument.Synced,
        track: Track,
    ): Boolean {
        val expectedSec = track.durationSeconds ?: return false
        if (expectedSec <= 0 || doc.lines.isEmpty()) return false
        val maxAllowedMs = (expectedSec + DURATION_TOLERANCE_SECONDS) * 1_000L
        val lastTimestampMs = doc.lines.last().startTimeMs
        return lastTimestampMs > maxAllowedMs
    }

    companion object {
        const val DEFAULT_HEDGE_DELAY_MS: Long = 280L
        private const val DURATION_TOLERANCE_SECONDS = 60L
    }
}
