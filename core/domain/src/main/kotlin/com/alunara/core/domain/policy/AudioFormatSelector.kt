package com.alunara.core.domain.policy

data class AudioFormatCandidate(
    val itag: Int,
    val mimeType: String,
    val bitrateBps: Int,
    val isAudioOnly: Boolean,
    val hasDirectUrl: Boolean,
)

/**
 * Selects the best audio-only stream format for low-RAM Android TV playback (PRD §6.1).
 * Never selects video formats, prefers direct URLs to avoid JS deobfuscation spikes,
 * and prefers Opus/AAC up to [maxPreferredBitrateBps].
 */
object AudioFormatSelector {
    fun selectBest(
        candidates: List<AudioFormatCandidate>,
        maxPreferredBitrateBps: Int = 256_000,
    ): AudioFormatCandidate? {
        val audioOnly = candidates.filter { it.isAudioOnly && it.mimeType.startsWith("audio/", ignoreCase = true) }
        if (audioOnly.isEmpty()) return null

        val pool = audioOnly.filter { it.hasDirectUrl }.ifEmpty { audioOnly }
        val withinCap = pool.filter { it.bitrateBps in 1..maxPreferredBitrateBps }
        return if (withinCap.isNotEmpty()) {
            withinCap.maxWithOrNull(
                compareBy<AudioFormatCandidate> { codecPriority(it.mimeType) }
                    .thenBy { it.bitrateBps },
            )
        } else {
            pool.minWithOrNull(
                compareBy<AudioFormatCandidate> { it.bitrateBps.takeIf { b -> b > 0 } ?: Int.MAX_VALUE }
                    .thenByDescending { codecPriority(it.mimeType) },
            )
        }
    }

    private fun codecPriority(mimeType: String): Int {
        val lower = mimeType.lowercase()
        return when {
            "opus" in lower || "audio/webm" in lower -> 2
            "mp4a" in lower || "audio/mp4" in lower -> 1
            else -> 0
        }
    }
}
