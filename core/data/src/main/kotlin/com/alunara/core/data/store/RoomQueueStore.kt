package com.alunara.core.data.store

import com.alunara.core.data.db.PlaybackSnapshotEntity
import com.alunara.core.data.db.QueueDao
import com.alunara.core.data.db.QueueItemEntity
import com.alunara.core.domain.contract.QueueStore
import com.alunara.core.domain.model.AlbumRef
import com.alunara.core.domain.model.ArtistRef
import com.alunara.core.domain.model.QueueItem
import com.alunara.core.domain.model.QueueState
import com.alunara.core.domain.model.Track
import com.alunara.core.domain.queue.QueueAction
import com.alunara.core.domain.queue.QueueReducer

class RoomQueueStore(
    private val queueDao: QueueDao,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : QueueStore {
    override suspend fun loadQueue(): QueueState {
        val entities = queueDao.getOrderedQueue()
        val snapshot = queueDao.getSnapshot()
        val items = entities.map { it.toDomain() }
        val rawState =
            QueueState(
                items = items,
                currentIndex = snapshot?.currentIndex ?: if (items.isNotEmpty()) 0 else -1,
                positionMs = snapshot?.positionMs ?: 0L,
                autoplayEnabled = snapshot?.autoplayEnabled ?: true,
            )
        return QueueReducer.reduce(QueueState(), QueueAction.Restore(rawState))
    }

    override suspend fun saveQueue(state: QueueState) {
        val entities =
            state.items.mapIndexed { index, item ->
                QueueItemEntity(
                    queueItemId = item.queueItemId,
                    positionIndex = index,
                    trackId = item.track.id,
                    title = item.track.title,
                    artistNames = item.track.primaryArtistName,
                    albumId = item.track.album?.id,
                    albumTitle = item.track.album?.title,
                    durationSeconds = item.track.durationSeconds,
                    thumbnailUrl = item.track.thumbnailUrl,
                    isExplicit = item.track.isExplicit,
                )
            }
        val snapshot =
            PlaybackSnapshotEntity(
                id = 1,
                currentIndex = state.currentIndex,
                positionMs = state.positionMs,
                autoplayEnabled = state.autoplayEnabled,
                updatedAtEpochMs = nowEpochMs(),
            )
        queueDao.replaceQueueAndSnapshot(entities, snapshot)
    }

    private fun QueueItemEntity.toDomain(): QueueItem {
        val artists =
            artistNames
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { ArtistRef(id = null, name = it) }
        val album =
            if (!albumId.isNullOrBlank() && !albumTitle.isNullOrBlank()) {
                AlbumRef(id = albumId, title = albumTitle)
            } else {
                null
            }
        return QueueItem(
            queueItemId = queueItemId,
            track =
                Track(
                    id = trackId,
                    title = title,
                    artists = artists,
                    album = album,
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumbnailUrl,
                    isExplicit = isExplicit,
                ),
        )
    }
}
