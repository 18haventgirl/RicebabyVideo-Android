package com.ricebaby.video.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VideoSourceEntity::class, FavoriteEntity::class, HistoryEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RicebabyDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
