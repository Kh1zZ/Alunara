package com.alunara.core.data.cache

import com.alunara.core.domain.policy.ResourceBudgetPolicy
import kotlin.math.min

data class CacheBudgetDecision(
    val dynamicCacheLimitBytes: Long,
    val audioDiskCacheLimitBytes: Long,
    val audioDiskCacheEnabled: Boolean,
)

/**
 * Enforces the 128 MB aggregate metadata/artwork/lyrics cap, adaptive low-storage reduction,
 * and FG-03 prohibition against audio disk caching by default.
 */
object CacheBudgetManager {
    private const val DEFAULT_LOW_STORAGE_THRESHOLD_BYTES: Long = 256L * 1024L * 1024L
    private const val LOW_STORAGE_DYNAMIC_CAP_BYTES: Long = 32L * 1024L * 1024L
    private const val LOW_MEMORY_DYNAMIC_CAP_BYTES: Long = 64L * 1024L * 1024L

    fun computeBudget(
        availableStorageBytes: Long,
        lowStorageThresholdBytes: Long = DEFAULT_LOW_STORAGE_THRESHOLD_BYTES,
        lowMemoryMode: Boolean = false,
    ): CacheBudgetDecision {
        val baseCap =
            if (lowMemoryMode) {
                min(ResourceBudgetPolicy.MAX_DYNAMIC_CACHE_BYTES, LOW_MEMORY_DYNAMIC_CAP_BYTES)
            } else {
                ResourceBudgetPolicy.MAX_DYNAMIC_CACHE_BYTES
            }

        val dynamicCap =
            when {
                availableStorageBytes <= 0L -> 0L
                availableStorageBytes < lowStorageThresholdBytes -> {
                    val quarterAvailable = availableStorageBytes / 4L
                    min(baseCap, min(LOW_STORAGE_DYNAMIC_CAP_BYTES, quarterAvailable))
                }
                else -> baseCap
            }

        return CacheBudgetDecision(
            dynamicCacheLimitBytes = dynamicCap,
            audioDiskCacheLimitBytes = ResourceBudgetPolicy.MAX_AUDIO_DISK_CACHE_BYTES,
            audioDiskCacheEnabled = ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED,
        )
    }
}
