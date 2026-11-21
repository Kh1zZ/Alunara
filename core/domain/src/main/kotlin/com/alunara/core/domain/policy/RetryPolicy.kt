package com.alunara.core.domain.policy

import kotlin.math.min
import kotlin.math.pow

enum class FailureCategory {
    TRANSIENT_TIMEOUT,
    OFFLINE,
    EXPIRED_SOURCE,
    AUTH_INVALID,
    RATE_LIMITED,
    CONTENT_UNAVAILABLE,
}

data class RetryDecision(
    val shouldRetry: Boolean,
    val delayMs: Long,
    val shouldSkipToNext: Boolean,
    val reason: String,
)

/**
 * Bounded retry/backoff policy with infinite-skip-loop protection (PRD §6.2).
 */
class RetryPolicy(
    private val maxRetriesPerTrack: Int = 2,
    private val baseDelayMs: Long = 500L,
    private val maxDelayMs: Long = 5_000L,
    private val jitterFraction: Double = 0.2,
    private val jitterProvider: () -> Double = { Math.random() },
) {
    fun evaluate(
        category: FailureCategory,
        attemptIndex: Int,
        consecutiveTrackFailures: Int,
        queueSize: Int,
    ): RetryDecision {
        val retryable =
            when (category) {
                FailureCategory.TRANSIENT_TIMEOUT,
                FailureCategory.OFFLINE,
                FailureCategory.EXPIRED_SOURCE,
                FailureCategory.RATE_LIMITED,
                -> true

                FailureCategory.AUTH_INVALID,
                FailureCategory.CONTENT_UNAVAILABLE,
                -> false
            }

        if (retryable && attemptIndex < maxRetriesPerTrack) {
            val exponential = baseDelayMs * 2.0.pow(attemptIndex.coerceAtLeast(0).toDouble())
            val capped = min(exponential.toLong(), maxDelayMs)
            val normalizedRandom = (jitterProvider().coerceIn(0.0, 1.0) * 2.0) - 1.0
            val jitter = (capped * jitterFraction * normalizedRandom).toLong()
            val delay = (capped + jitter).coerceIn(0L, maxDelayMs)
            return RetryDecision(
                shouldRetry = true,
                delayMs = delay,
                shouldSkipToNext = false,
                reason = "Retry attempt ${attemptIndex + 1} for $category",
            )
        }

        val canSkipSafely = queueSize > 1 && (consecutiveTrackFailures + 1) < queueSize
        return RetryDecision(
            shouldRetry = false,
            delayMs = 0L,
            shouldSkipToNext = canSkipSafely,
            reason =
                if (canSkipSafely) {
                    "Retries exhausted ($category); advancing to next track"
                } else {
                    "Stopped to prevent infinite skip loop ($category)"
                },
        )
    }
}
