package com.ricebaby.video.player

import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.data.db.VideoSourceEntity
import com.ricebaby.video.search.VideoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * App ↔ 站点播放器页的 localStorage/sessionStorage 编解码。
 * 键名与格式对齐 Ricebaby_Video master（见 spec §5.1 与 M3 计划契约表）。
 */
object BridgeCodec {

    const val KEY_SETTINGS = "ricebabyvideo-settings"
    const val KEY_HISTORY = "ricebabyvideo-history-store"
    const val KEY_FAVORITES = "ricebabyvideo-favorites-store"

    private val json = Json { ignoreUnknownKeys = true }

    data class WebHistoryItem(
        val videoId: String,
        val title: String,
        val source: String,
        val episodeIndex: Int,
        val playbackPosition: Long,
        val duration: Long,
        val timestamp: Long,
        val poster: String?,
    )

    data class WebFavoriteItem(
        val videoId: String,
        val title: String,
        val source: String,
        val sourceName: String?,
        val poster: String?,
        val addedAt: Long,
        val typeName: String?,
        val year: String?,
        val remarks: String?,
    )

    fun settingsJson(sources: List<VideoSourceEntity>): String {
        val array = JsonArray(
            sources.map {
                JsonObject(
                    buildMap {
                        put("id", JsonPrimitive(it.id))
                        put("name", JsonPrimitive(it.name))
                        put("baseUrl", JsonPrimitive(it.baseUrl))
                        put("searchPath", JsonPrimitive(""))
                        put("detailPath", JsonPrimitive(""))
                        put("enabled", JsonPrimitive(it.enabled))
                        put("priority", JsonPrimitive(it.priority))
                        put("group", JsonPrimitive(it.group))
                    },
                )
            },
        )
        val root = buildMap<String, JsonElement> {
            put("sources", array)
            put("subscriptions", JsonArray(emptyList()))
            put("sortBy", JsonPrimitive("default"))
            put("searchHistory", JsonPrimitive(true))
            put("watchHistory", JsonPrimitive(true))
            put("autoNextEpisode", JsonPrimitive(true))
            put("autoSkipIntro", JsonPrimitive(false))
            put("skipIntroSeconds", JsonPrimitive(0))
            put("autoSkipOutro", JsonPrimitive(false))
            put("skipOutroSeconds", JsonPrimitive(0))
            put("enableAdFilter", JsonPrimitive(false))
            put("adKeywords", JsonArray(emptyList()))
            put("blockedCategories", JsonArray(emptyList()))
        }
        return json.encodeToString(JsonObject(root))
    }

    fun historyJson(items: List<HistoryEntity>): String {
        val array = JsonArray(
            items.map { h ->
                buildMap<String, JsonElement> {
                    put("videoId", JsonPrimitive(h.vodId))
                    put("title", JsonPrimitive(h.name))
                    put("url", JsonPrimitive(""))
                    put("episodeIndex", JsonPrimitive(h.episodeIndex))
                    put("source", JsonPrimitive(h.source))
                    put("timestamp", JsonPrimitive(h.updatedAt))
                    put("playbackPosition", JsonPrimitive(h.positionMs))
                    put("duration", JsonPrimitive(h.durationMs))
                    put("poster", if (h.pic != null) JsonPrimitive(h.pic) else JsonNull)
                    put("episodes", JsonArray(emptyList()))
                    put("showIdentifier", JsonPrimitive("title:${h.name.lowercase().trim()}"))
                }.let(::JsonObject)
            },
        )
        val root = buildMap<String, JsonElement> {
            put("state", JsonObject(mapOf("history" to array)))
            put("version", JsonPrimitive(2))
        }
        return json.encodeToString(JsonObject(root))
    }

    fun favoritesJson(items: List<FavoriteEntity>): String {
        val array = JsonArray(
            items.map { f ->
                buildMap<String, JsonElement> {
                    put("videoId", JsonPrimitive(f.vodId))
                    put("title", JsonPrimitive(f.name))
                    put("source", JsonPrimitive(f.source))
                    put("sourceName", JsonPrimitive(f.sourceName ?: f.name))
                    put("poster", if (f.pic != null) JsonPrimitive(f.pic) else JsonNull)
                    put("addedAt", JsonPrimitive(f.addedAt))
                    f.typeName?.let { put("type", JsonPrimitive(it)) }
                    f.year?.let { put("year", JsonPrimitive(it)) }
                    f.remarks?.let { put("remarks", JsonPrimitive(it)) }
                }.let(::JsonObject)
            },
        )
        return json.encodeToString(JsonObject(mapOf("state" to JsonObject(mapOf("favorites" to array)))))
    }

    fun gsScript(candidates: List<VideoItem>, key: String): String {
        val data = JsonArray(
            candidates.map { c ->
                buildMap<String, JsonElement> {
                    put("id", JsonPrimitive(c.vodId))
                    put("source", JsonPrimitive(c.source))
                    put("sourceName", JsonPrimitive(c.sourceDisplayName ?: c.source))
                    c.pic?.let { put("pic", JsonPrimitive(it)) }
                    c.typeName?.let { put("typeName", JsonPrimitive(it)) }
                    c.remarks?.let { put("remarks", JsonPrimitive(it)) }
                }.let(::JsonObject)
            },
        )
        val payload = json.encodeToString(JsonObject(mapOf("data" to data, "ts" to JsonPrimitive(System.currentTimeMillis()))))
        return "sessionStorage.setItem(${jsLiteral("gs:$key")}, ${jsLiteral(payload)});"
    }

    fun documentStartScript(settingsJson: String, historyJson: String, favoritesJson: String, gsScript: String): String =
        "try{localStorage.setItem(${jsLiteral(KEY_SETTINGS)}, ${jsLiteral(settingsJson)});}catch(e){}" +
            "try{localStorage.setItem(${jsLiteral(KEY_HISTORY)}, ${jsLiteral(historyJson)});}catch(e){}" +
            "try{localStorage.setItem(${jsLiteral(KEY_FAVORITES)}, ${jsLiteral(favoritesJson)});}catch(e){}" +
            if (gsScript.isBlank()) "" else "try{$gsScript}catch(e){}"

    /** 把任意字符串编码成 JS 字符串字面量（复用 JSON 转义规则，两者在 JS 源码中兼容） */
    private fun jsLiteral(value: String): String = json.encodeToString(JsonPrimitive(value))

    fun parseWebHistory(rawJson: String?): List<WebHistoryItem> = runCatching {
        json.parseToJsonElement(rawJson ?: return emptyList())
            .jsonObject["state"]?.jsonObject?.get("history")?.jsonArray
            ?.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    WebHistoryItem(
                        videoId = o["videoId"].str(),
                        title = o["title"].str(),
                        source = o["source"].str(),
                        episodeIndex = o["episodeIndex"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        playbackPosition = o["playbackPosition"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        duration = o["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        timestamp = o["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        poster = o["poster"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                    )
                }.getOrNull()
            } ?: emptyList()
    }.getOrDefault(emptyList())

    fun parseWebFavorites(rawJson: String?): List<WebFavoriteItem> = runCatching {
        json.parseToJsonElement(rawJson ?: return emptyList())
            .jsonObject["state"]?.jsonObject?.get("favorites")?.jsonArray
            ?.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    WebFavoriteItem(
                        videoId = o["videoId"].str(),
                        title = o["title"].str(),
                        source = o["source"].str(),
                        sourceName = o["sourceName"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                        poster = o["poster"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                        addedAt = o["addedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        typeName = o["type"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                        year = o["year"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                        remarks = o["remarks"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                    )
                }.getOrNull()
            } ?: emptyList()
    }.getOrDefault(emptyList())

    /** 历史：同 (source,vodId) 取时间戳更新的一方；两侧独有全部保留 */
    fun mergeHistory(web: List<WebHistoryItem>, local: List<HistoryEntity>): List<HistoryEntity> {
        val byKey = local.associateBy { "${it.source}:${it.vodId}" }.toMutableMap()
        for (w in web) {
            val key = "${w.source}:${w.videoId}"
            val existing = byKey[key]
            if (existing == null || w.timestamp > existing.updatedAt) {
                byKey[key] = HistoryEntity(
                    source = w.source,
                    vodId = w.videoId,
                    name = w.title,
                    pic = w.poster ?: existing?.pic,
                    typeName = existing?.typeName,
                    episodeIndex = w.episodeIndex,
                    positionMs = w.playbackPosition,
                    durationMs = w.duration,
                    updatedAt = w.timestamp,
                )
            }
        }
        return byKey.values.toList()
    }

    /** 收藏：并集；同条目保留更早的加入时间（首次收藏时刻） */
    fun mergeFavorites(web: List<WebFavoriteItem>, local: List<FavoriteEntity>): List<FavoriteEntity> {
        val byKey = local.associateBy { "${it.source}:${it.vodId}" }.toMutableMap()
        for (w in web) {
            val key = "${w.source}:${w.videoId}"
            val existing = byKey[key]
            if (existing == null || w.addedAt < existing.addedAt) {
                byKey[key] = FavoriteEntity(
                    source = w.source,
                    vodId = w.videoId,
                    name = w.title,
                    pic = w.poster ?: existing?.pic,
                    typeName = w.typeName ?: existing?.typeName,
                    remarks = w.remarks ?: existing?.remarks,
                    year = w.year ?: existing?.year,
                    sourceName = w.sourceName ?: existing?.sourceName,
                    addedAt = if (existing == null) w.addedAt else minOf(w.addedAt, existing.addedAt),
                )
            }
        }
        return byKey.values.toList()
    }

    private fun JsonElement?.str(): String =
        this?.takeIf { it !is JsonNull }?.jsonPrimitive?.content.orEmpty()
}
