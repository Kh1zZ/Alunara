package com.alunara.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QueueItemEntity::class,
        PlaybackSnapshotEntity::class,
        PlaybackHistoryEntity::class,
        LocalFavoriteEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AlunaraDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    abstract fun historyDao(): HistoryDao

    abstract fun favoritesDao(): FavoritesDao

    abstract fun lyricsCacheDao(): LyricsCacheDao
}
