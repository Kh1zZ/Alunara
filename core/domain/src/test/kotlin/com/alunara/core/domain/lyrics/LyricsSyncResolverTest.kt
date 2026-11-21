package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.SyncedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSyncResolverTest {
    private val sampleLines =
        listOf(
            SyncedLyricLine(5_000L, "Line 1"),
            SyncedLyricLine(12_500L, "Line 2"),
            SyncedLyricLine(25_000L, "Line 3"),
        )

    @Test
    fun findActiveLineIndex_handlesEmptyBeforeFirstExactMidAndEnd() {
        assertEquals(-1, LyricsSyncResolver.findActiveLineIndex(emptyList(), 10_000L))
        assertEquals(-1, LyricsSyncResolver.findActiveLineIndex(sampleLines, 4_999L))
        assertEquals(0, LyricsSyncResolver.findActiveLineIndex(sampleLines, 5_000L))
        assertEquals(1, LyricsSyncResolver.findActiveLineIndex(sampleLines, 18_000L))
        assertEquals(2, LyricsSyncResolver.findActiveLineIndex(sampleLines, 99_000L))
    }
}
