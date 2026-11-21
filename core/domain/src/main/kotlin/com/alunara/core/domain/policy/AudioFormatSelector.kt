package com.alunara.core.domain.policy

enum class AudioQualityPreset(
    val maxBitrateBps: Int,
) {
    AUTO(192_000),
    LOW(80_000),
    MEDIUM(160_000),
    HIGH(Int.MAX_VALUE),
}

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
 * and supports explicit bitrate caps or [AudioQualityPreset] selection.
 */
object AudioFormatSelector {
    fun selectBest(
        candidates: List<AudioFormatCandidate>,
        maxPreferredBitrateBps: Int = 256_000,
    ): AudioFormatCandidate? {
        val pool = eligibleAudioPool(candidates) ?: return null

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

    fun selectBest(
        candidates: List<AudioFormatCandidate>,
        quality: AudioQualityPreset,
    ): AudioFormatCandidate? {
        if (quality != AudioQualityPreset.HIGH) {
            return selectBest(candidates, maxPreferredBitrateBps = quality.maxBitrateBps)
        }
        val pool = eligibleAudioPool(candidates) ?: return null
        return pool.maxWithOrNull(
            compareBy<AudioFormatCandidate> { it.bitrateBps.coerceAtLeast(0) }
                .thenBy { codecPriority(it.mimeType) },
        )
    }

    private fun eligibleAudioPool(candidates: List<AudioFormatCandidate>): List<AudioFormatCandidate>? {
        val audioOnly = candidates.filter { it.isAudioOnly && it.mimeType.startsWith("audio/", ignoreCase = true) }
        if (audioOnly.isEmpty()) return null
        return audioOnly.filter { it.hasDirectUrl }.ifEmpty { audioOnly }
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
