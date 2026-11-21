package com.alunara.core.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun transientTimeout_retriesWithinLimitWithBoundedDelay() {
        val policy = RetryPolicy(maxRetriesPerTrack = 2, baseDelayMs = 500L, maxDelayMs = 5_000L, jitterFraction = 0.2) { 0.5 }
        val d0 = policy.evaluate(FailureCategory.TRANSIENT_TIMEOUT, attemptIndex = 0, consecutiveTrackFailures = 0, queueSize = 3)
        val d1 = policy.evaluate(FailureCategory.TRANSIENT_TIMEOUT, attemptIndex = 1, consecutiveTrackFailures = 0, queueSize = 3)
        assertTrue(d0.shouldRetry)
        assertEquals(500L, d0.delayMs)
        assertFalse(d0.shouldSkipToNext)
        assertTrue(d1.shouldRetry)
        assertEquals(1_000L, d1.delayMs)
    }

    @Test
    fun exhaustedRetries_skipsWhenSafeInMultiTrackQueue() {
        val policy = RetryPolicy(maxRetriesPerTrack = 2) { 0.5 }
        val decision = policy.evaluate(FailureCategory.OFFLINE, attemptIndex = 2, consecutiveTrackFailures = 0, queueSize = 3)
        assertFalse(decision.shouldRetry)
        assertTrue(decision.shouldSkipToNext)
    }

    @Test
    fun infiniteSkipLoopGuard_stopsWhenAllTracksFailed() {
        val policy = RetryPolicy(maxRetriesPerTrack = 2) { 0.5 }
        val decision = policy.evaluate(FailureCategory.EXPIRED_SOURCE, attemptIndex = 2, consecutiveTrackFailures = 2, queueSize = 3)
        assertFalse(decision.shouldRetry)
        assertFalse(decision.shouldSkipToNext)
    }

    @Test
    fun authInvalid_doesNotRetry() {
        val policy = RetryPolicy(maxRetriesPerTrack = 2) { 0.5 }
        val decision = policy.evaluate(FailureCategory.AUTH_INVALID, attemptIndex = 0, consecutiveTrackFailures = 0, queueSize = 3)
        assertFalse(decision.shouldRetry)
        assertTrue(decision.shouldSkipToNext)
    }
}
