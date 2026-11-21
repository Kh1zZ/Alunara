package com.alunara.core.domain.lyrics

import com.alunara.core.domain.model.SyncedLyricLine
import com.alunara.core.domain.model.SyncedLyricWord
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

    @Test
    fun findActiveWordIndex_handlesEmptyBeforeFirstExactMidAndEnd() {
        val words =
            listOf(
                SyncedLyricWord(4_540L, "Hari "),
                SyncedLyricWord(5_000L, "ini "),
                SyncedLyricWord(5_600L, "cerah"),
            )
        assertEquals(-1, LyricsSyncResolver.findActiveWordIndex(emptyList(), 5_000L))
        assertEquals(-1, LyricsSyncResolver.findActiveWordIndex(words, 4_539L))
        assertEquals(0, LyricsSyncResolver.findActiveWordIndex(words, 4_540L))
        assertEquals(1, LyricsSyncResolver.findActiveWordIndex(words, 5_599L))
        assertEquals(2, LyricsSyncResolver.findActiveWordIndex(words, 8_000L))
    }
}
