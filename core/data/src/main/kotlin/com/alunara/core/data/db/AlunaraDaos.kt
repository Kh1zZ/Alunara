package com.alunara.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY positionIndex ASC")
    suspend fun getOrderedQueue(): List<QueueItemEntity>

    @Query("SELECT * FROM playback_snapshot WHERE id = 1")
    suspend fun getSnapshot(): PlaybackSnapshotEntity?

    @Query("DELETE FROM queue_items")
    suspend fun clearQueueItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: PlaybackSnapshotEntity)

    @Transaction
    suspend fun replaceQueueAndSnapshot(
        items: List<QueueItemEntity>,
        snapshot: PlaybackSnapshotEntity,
    ) {
        clearQueueItems()
        if (items.isNotEmpty()) {
            insertQueueItems(items)
        }
        upsertSnapshot(snapshot)
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertHistory(entry: PlaybackHistoryEntity)

    @Query(
        "SELECT * FROM playback_history WHERE accountScope = :accountScope ORDER BY playedAtEpochMs DESC LIMIT :limit",
    )
    suspend fun getRecentHistory(
        accountScope: String,
        limit: Int,
    ): List<PlaybackHistoryEntity>

    @Query(
        """
        DELETE FROM playback_history
        WHERE accountScope = :accountScope AND id NOT IN (
            SELECT id FROM playback_history
            WHERE accountScope = :accountScope
            ORDER BY playedAtEpochMs DESC
            LIMIT :keepCount
        )
        """,
    )
    suspend fun pruneExcess(
        accountScope: String,
        keepCount: Int,
    )

    @Query("DELETE FROM playback_history WHERE accountScope = :accountScope")
    suspend fun clearAccountScope(accountScope: String)
}

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entry: LocalFavoriteEntity)

    @Query("DELETE FROM local_favorites WHERE trackId = :trackId AND accountScope = :accountScope")
    suspend fun removeFavorite(
        trackId: String,
        accountScope: String,
    )

    @Query("SELECT * FROM local_favorites WHERE accountScope = :accountScope ORDER BY addedAtEpochMs DESC")
    suspend fun getFavorites(accountScope: String): List<LocalFavoriteEntity>

    @Query("DELETE FROM local_favorites WHERE accountScope = :accountScope")
    suspend fun clearAccountScope(accountScope: String)
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun getEntry(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE expiresAtEpochMs <= :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long)

    @Query(
        """
        DELETE FROM lyrics_cache WHERE trackId NOT IN (
            SELECT trackId FROM lyrics_cache
            ORDER BY fetchedAtEpochMs DESC
            LIMIT :keepCount
        )
        """,
    )
    suspend fun deleteOldest(keepCount: Int)
}
