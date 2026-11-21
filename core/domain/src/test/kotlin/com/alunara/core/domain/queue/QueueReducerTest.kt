package com.alunara.core.domain.queue

import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReducerTest {
    private fun item(id: String) =
        QueueItem(
            queueItemId = "q-$id",
            track = Track(id = id, title = "Track $id", artists = listOf(ArtistRef(null, "Artist"))),
        )

    @Test
    fun setQueue_emptyResetsIndexAndPosition() {
        val initial = QueueState(items = listOf(item("1")), currentIndex = 0, positionMs = 45_000L)
        val reduced = QueueReducer.reduce(initial, QueueAction.SetQueue(emptyList()))
        assertEquals(emptyList<QueueItem>(), reduced.items)
        assertEquals(-1, reduced.currentIndex)
        assertEquals(0L, reduced.positionMs)
        assertNull(reduced.currentItem)
    }

    @Test
    fun setQueue_clampsStartIndexAndResetsPosition() {
        val reduced =
            QueueReducer.reduce(
                QueueState(positionMs = 12_000L),
                QueueAction.SetQueue(listOf(item("a"), item("b")), startIndex = 99),
            )
        assertEquals(1, reduced.currentIndex)
        assertEquals(0L, reduced.positionMs)
    }

    @Test
    fun removeAt_beforeCurrentShiftsCurrentIndexDown() {
        val state = QueueState(items = listOf(item("a"), item("b"), item("c")), currentIndex = 2)
        val reduced = QueueReducer.reduce(state, QueueAction.RemoveAt(0))
        assertEquals(1, reduced.currentIndex)
        assertEquals("c", reduced.currentItem?.track?.id)
    }

    @Test
    fun move_tracksActiveItemWhenMoved() {
        val state = QueueState(items = listOf(item("a"), item("b"), item("c")), currentIndex = 0)
        val reduced = QueueReducer.reduce(state, QueueAction.Move(fromIndex = 0, toIndex = 2))
        assertEquals(2, reduced.currentIndex)
        assertEquals("a", reduced.currentItem?.track?.id)
    }

    @Test
    fun skipNextAndPrevious_respectBoundsAndResetPosition() {
        val state = QueueState(items = listOf(item("a"), item("b")), currentIndex = 0, positionMs = 10_000L)
        val next = QueueReducer.reduce(state, QueueAction.SkipNext)
        assertEquals(1, next.currentIndex)
        assertEquals(0L, next.positionMs)

        val atEnd = QueueReducer.reduce(next, QueueAction.SkipNext)
        assertEquals(1, atEnd.currentIndex)

        val prev = QueueReducer.reduce(next, QueueAction.SkipPrevious)
        assertEquals(0, prev.currentIndex)
        assertEquals(0L, prev.positionMs)
    }

    @Test
    fun restore_clampsOutOfBoundsSnapshot() {
        val restored =
            QueueReducer.reduce(
                QueueState(),
                QueueAction.Restore(
                    QueueState(
                        items = listOf(item("x")),
                        currentIndex = 4,
                        positionMs = -200L,
                        autoplayEnabled = false,
                    ),
                ),
            )
        assertEquals(0, restored.currentIndex)
        assertEquals(0L, restored.positionMs)
        assertEquals(false, restored.autoplayEnabled)
    }
}
