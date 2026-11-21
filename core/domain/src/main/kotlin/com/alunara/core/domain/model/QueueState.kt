package com.alunara.core.domain.model

data class QueueItem(
    val queueItemId: String,
    val track: Track,
)

data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val autoplayEnabled: Boolean = true,
) {
    val currentItem: QueueItem?
        get() = items.getOrNull(currentIndex)
}
