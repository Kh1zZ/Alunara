package com.alunara.core.domain.policy

/**
 * Locked resource and compliance budgets from PRD §7.1, §9.1, and FG-03.
 */
object ResourceBudgetPolicy {
    const val MAX_DYNAMIC_CACHE_BYTES: Long = 128L * 1024L * 1024L

    // FG-03: 32 MB audio disk cache MUST remain disabled unless content-provider terms allow it.
    const val AUDIO_DISK_CACHE_ENABLED: Boolean = false
    const val MAX_AUDIO_DISK_CACHE_BYTES: Long = 0L

    // PRD §7.1 / FR-20: bounded lyrics parallelism and single next-track prefetch.
    const val MAX_CONCURRENT_LYRICS_REQUESTS: Int = 2
    const val MAX_LYRICS_PREFETCH_COUNT: Int = 1

    const val DEFAULT_HISTORY_LIMIT: Int = 200
}
