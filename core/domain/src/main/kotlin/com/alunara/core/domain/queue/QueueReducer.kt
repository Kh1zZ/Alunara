package com.alunara.core.domain.queue

import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState

sealed interface QueueAction {
    data class SetQueue(
        val items: List<QueueItem>,
        val startIndex: Int = 0,
    ) : QueueAction

    data class Enqueue(
        val item: QueueItem,
    ) : QueueAction

    data class EnqueueAll(
        val items: List<QueueItem>,
    ) : QueueAction

    data class RemoveAt(
        val index: Int,
    ) : QueueAction

    data class Move(
        val fromIndex: Int,
        val toIndex: Int,
    ) : QueueAction

    data object SkipNext : QueueAction

    data object SkipPrevious : QueueAction

    data class UpdatePosition(
        val positionMs: Long,
    ) : QueueAction

    data class SetAutoplay(
        val enabled: Boolean,
    ) : QueueAction

    data class Restore(
        val snapshot: QueueState,
    ) : QueueAction
}

object QueueReducer {
    fun reduce(
        state: QueueState,
        action: QueueAction,
    ): QueueState =
        when (action) {
            is QueueAction.SetQueue -> {
                if (action.items.isEmpty()) {
                    state.copy(items = emptyList(), currentIndex = -1, positionMs = 0L)
                } else {
                    val safeIndex = action.startIndex.coerceIn(0, action.items.lastIndex)
                    state.copy(items = action.items, currentIndex = safeIndex, positionMs = 0L)
                }
            }

            is QueueAction.Enqueue -> {
                val updated = state.items + action.item
                val nextIndex = if (state.currentIndex < 0) 0 else state.currentIndex
                state.copy(items = updated, currentIndex = nextIndex)
            }

            is QueueAction.EnqueueAll -> {
                if (action.items.isEmpty()) {
                    state
                } else {
                    val updated = state.items + action.items
                    val nextIndex = if (state.currentIndex < 0) 0 else state.currentIndex
                    state.copy(items = updated, currentIndex = nextIndex)
                }
            }

            is QueueAction.RemoveAt -> {
                if (action.index !in state.items.indices) {
                    state
                } else {
                    val updated = state.items.toMutableList().apply { removeAt(action.index) }
                    when {
                        updated.isEmpty() -> state.copy(items = emptyList(), currentIndex = -1, positionMs = 0L)
                        action.index < state.currentIndex -> state.copy(items = updated, currentIndex = state.currentIndex - 1)
                        action.index == state.currentIndex ->
                            state.copy(
                                items = updated,
                                currentIndex = action.index.coerceAtMost(updated.lastIndex),
                                positionMs = 0L,
                            )
                        else -> state.copy(items = updated)
                    }
                }
            }

            is QueueAction.Move -> {
                if (action.fromIndex !in state.items.indices ||
                    action.toIndex !in state.items.indices ||
                    action.fromIndex == action.toIndex
                ) {
                    state
                } else {
                    val updated = state.items.toMutableList()
                    val moved = updated.removeAt(action.fromIndex)
                    updated.add(action.toIndex, moved)
                    val newCurrent =
                        when {
                            state.currentIndex == action.fromIndex -> action.toIndex
                            action.fromIndex < state.currentIndex && action.toIndex >= state.currentIndex ->
                                state.currentIndex - 1
                            action.fromIndex > state.currentIndex && action.toIndex <= state.currentIndex ->
                                state.currentIndex + 1
                            else -> state.currentIndex
                        }
                    state.copy(items = updated, currentIndex = newCurrent)
                }
            }

            QueueAction.SkipNext -> {
                if (state.items.isEmpty() || state.currentIndex >= state.items.lastIndex) {
                    state
                } else {
                    state.copy(currentIndex = (state.currentIndex + 1).coerceAtLeast(0), positionMs = 0L)
                }
            }

            QueueAction.SkipPrevious -> {
                if (state.items.isEmpty() || state.currentIndex <= 0) {
                    state.copy(positionMs = 0L)
                } else {
                    state.copy(currentIndex = state.currentIndex - 1, positionMs = 0L)
                }
            }

            is QueueAction.UpdatePosition -> {
                state.copy(positionMs = action.positionMs.coerceAtLeast(0L))
            }

            is QueueAction.SetAutoplay -> {
                state.copy(autoplayEnabled = action.enabled)
            }

            is QueueAction.Restore -> {
                val restoredItems = action.snapshot.items
                val restoredIndex =
                    if (restoredItems.isEmpty()) {
                        -1
                    } else {
                        action.snapshot.currentIndex.coerceIn(0, restoredItems.lastIndex)
                    }
                action.snapshot.copy(
                    items = restoredItems,
                    currentIndex = restoredIndex,
                    positionMs = action.snapshot.positionMs.coerceAtLeast(0L),
                )
            }
        }
}
