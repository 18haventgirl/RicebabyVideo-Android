package com.ricebaby.video.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM video_sources WHERE enabled = 1 ORDER BY priority ASC, name ASC")
    fun observeEnabled(): Flow<List<VideoSourceEntity>>

    @Query("SELECT * FROM video_sources WHERE enabled = 1")
    suspend fun enabledOnce(): List<VideoSourceEntity>

    @Query("SELECT COUNT(*) FROM video_sources")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(sources: List<VideoSourceEntity>)

    @Query("DELETE FROM video_sources WHERE subscriptionUrl = :url")
    suspend fun deleteBySubscription(url: String)

    @Query("DELETE FROM video_sources")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE source = :source AND vodId = :vodId")
    suspend fun remove(source: String, vodId: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteEntity>)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE source = :source AND vodId = :vodId")
    suspend fun remove(source: String, vodId: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertAll(entries: List<HistoryEntity>)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC LIMIT 12")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun remove(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(entries: List<SearchHistoryEntity>)
}
