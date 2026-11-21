package com.alunara.core.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResourceBudgetPolicyTest {
    @Test
    fun resourceBudgets_enforceLockedFg03AndPrdCaps() {
        assertFalse(ResourceBudgetPolicy.AUDIO_DISK_CACHE_ENABLED)
        assertEquals(0L, ResourceBudgetPolicy.MAX_AUDIO_DISK_CACHE_BYTES)
        assertEquals(128L * 1024L * 1024L, ResourceBudgetPolicy.MAX_DYNAMIC_CACHE_BYTES)
        assertEquals(2, ResourceBudgetPolicy.MAX_CONCURRENT_LYRICS_REQUESTS)
        assertEquals(1, ResourceBudgetPolicy.MAX_LYRICS_PREFETCH_COUNT)
    }
}
