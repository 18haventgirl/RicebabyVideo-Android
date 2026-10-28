package com.ricebaby.video.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "video_sources")
data class VideoSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val group: String = "normal",
    val enabled: Boolean = true,
    val priority: Int = 1,
    val subscriptionUrl: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "favorites", primaryKeys = ["source", "vodId"])
data class FavoriteEntity(
    val source: String,
    val vodId: String,
    val name: String,
    val pic: String? = null,
    val typeName: String? = null,
    val remarks: String? = null,
    val year: String? = null,
    val sourceName: String? = null,
    val addedAt: Long,
)

@Entity(tableName = "history", primaryKeys = ["source", "vodId"])
@Serializable
data class HistoryEntity(
    val source: String,
    val vodId: String,
    val name: String,
    val pic: String? = null,
    val typeName: String? = null,
    val episodeIndex: Int = 0,
    val episodeName: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long,
)

@Entity(tableName = "search_history")
@Serializable
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val updatedAt: Long,
)
