package com.ricebaby.video.data.backup

import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.data.db.SearchHistoryEntity
import com.ricebaby.video.data.model.SiteInstance
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val instances: List<SiteInstance> = emptyList(),
    val activeInstanceId: String? = null,
    val passwords: Map<String, String> = emptyMap(),
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val favorites: List<FavoriteEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
)

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun serialize(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    /** 解析失败返回 null，调用方不得改动现有数据 */
    fun deserialize(text: String): BackupPayload? = runCatching {
        json.decodeFromString(BackupPayload.serializer(), text)
    }.getOrNull()
}
