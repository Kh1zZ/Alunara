package com.alunara.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_items",
    indices = [Index(value = ["positionIndex"])],
)
data class QueueItemEntity(
    @PrimaryKey val queueItemId: String,
    val positionIndex: Int,
    val trackId: String,
    val title: String,
    val artistNames: String,
    val albumId: String?,
    val albumTitle: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val isExplicit: Boolean,
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val currentIndex: Int,
    val positionMs: Long,
    val autoplayEnabled: Boolean,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["accountScope", "playedAtEpochMs"])],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val title: String,
    val artistNames: String,
    val thumbnailUrl: String?,
    val playedAtEpochMs: Long,
    val accountScope: String,
)

@Entity(
    tableName = "local_favorites",
    primaryKeys = ["trackId", "accountScope"],
    indices = [Index(value = ["accountScope", "addedAtEpochMs"])],
)
data class LocalFavoriteEntity(
    val trackId: String,
    val accountScope: String,
    val title: String,
    val artistNames: String,
    val thumbnailUrl: String?,
    val addedAtEpochMs: Long,
)

@Entity(
    tableName = "lyrics_cache",
    indices = [Index(value = ["expiresAtEpochMs"])],
)
data class LyricsCacheEntity(
    @PrimaryKey val trackId: String,
    val provider: String,
    val format: String,
    val payload: String,
    val fetchedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val isNegativeCache: Boolean,
)
