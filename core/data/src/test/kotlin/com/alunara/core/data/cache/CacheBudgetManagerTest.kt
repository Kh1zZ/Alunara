package com.alunara.core.data.cache

import com.alunara.core.domain.policy.ResourceBudgetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheBudgetManagerTest {
    @Test
    fun computeBudget_normalStorageUsesFull128MbAndDisablesAudioCache() {
        val budget = CacheBudgetManager.computeBudget(availableStorageBytes = 2L * 1024L * 1024L * 1024L)
        assertEquals(ResourceBudgetPolicy.MAX_DYNAMIC_CACHE_BYTES, budget.dynamicCacheLimitBytes)
        assertFalse(budget.audioDiskCacheEnabled)
        assertEquals(0L, budget.audioDiskCacheLimitBytes)
    }

    @Test
    fun computeBudget_lowStorageReducesDynamicCacheAdaptively() {
        val budget = CacheBudgetManager.computeBudget(availableStorageBytes = 80L * 1024L * 1024L)
        assertEquals(20L * 1024L * 1024L, budget.dynamicCacheLimitBytes)
        assertFalse(budget.audioDiskCacheEnabled)
        assertEquals(0L, budget.audioDiskCacheLimitBytes)
    }

    @Test
    fun computeBudget_lowMemoryCapsDynamicCache() {
        val budget =
            CacheBudgetManager.computeBudget(
                availableStorageBytes = 2L * 1024L * 1024L * 1024L,
                lowMemoryMode = true,
            )
        assertTrue(budget.dynamicCacheLimitBytes <= 64L * 1024L * 1024L)
        assertFalse(budget.audioDiskCacheEnabled)
    }
}
