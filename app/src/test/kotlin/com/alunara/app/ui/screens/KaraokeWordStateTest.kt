package com.alunara.app.ui.screens

import com.alunara.core.domain.model.SyncedLyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class KaraokeWordStateTest {
    @Test
    fun tracksWordEdgesAndGapsWhenSeekingInEitherDirection() {
        val words = listOf(
            SyncedLyricWord(100L, "First ", endTimeMs = 200L),
            SyncedLyricWord(350L, "Second"),
        )
        assertEquals(0, karaokeWordState(words, 99L, 500L))
        assertEquals(1, karaokeWordState(words, 200L, 500L))
        assertEquals(2, karaokeWordState(words, 201L, 500L))
        assertEquals(3, karaokeWordState(words, 350L, 500L))
        assertEquals(4, karaokeWordState(words, 501L, 500L))
        assertEquals(1, karaokeWordState(words, 150L, 500L))
    }
}
