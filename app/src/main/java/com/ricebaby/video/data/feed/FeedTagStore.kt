package com.ricebaby.video.data.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.feedTagDataStore: DataStore<Preferences> by preferencesDataStore(name = "ricebaby_feed_tags")

/** 首页标签的用户自定义：隐藏站点标签、追加自己的标签，按电影 / 剧集分别保存 */
@Singleton
class FeedTagStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.feedTagDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val hiddenTags: Flow<Map<String, List<String>>> = store.data.map { prefs ->
        TYPES.associateWith { decode(prefs[key("hidden", it)]) }
    }

    val customTags: Flow<Map<String, List<String>>> = store.data.map { prefs ->
        TYPES.associateWith { decode(prefs[key("custom", it)]) }
    }

    suspend fun setHidden(type: String, tag: String, hidden: Boolean) =
        mutate("hidden", type) { current -> if (hidden) (current + tag).distinct() else current - tag }

    /** 空标签与重复标签直接拒绝，返回是否写入 */
    suspend fun addCustom(type: String, tag: String): Boolean {
        val name = tag.trim()
        if (name.isEmpty()) return false
        mutate("custom", type) { (it + name).distinct() }
        return true
    }

    suspend fun removeCustom(type: String, tag: String) = mutate("custom", type) { it - tag }

    suspend fun reset(type: String) {
        store.edit { prefs ->
            prefs.remove(key("hidden", type))
            prefs.remove(key("custom", type))
        }
    }

    /**
     * 站点标签缓存（键 = "站点地址|类型"）。标签行是首屏骨架之外唯一能立刻确定的内容，
     * 冷启动先用它把位置钉住，网络回来再静默刷新，避免"先空一块再往下跳"。
     */
    fun serverTags(scope: String): Flow<List<String>> =
        store.data.map { prefs -> decodeMap(prefs[KEY_SERVER_TAGS])[scope].orEmpty() }

    suspend fun saveServerTags(scope: String, tags: List<String>) {
        if (tags.isEmpty()) return
        store.edit { prefs ->
            val current = decodeMap(prefs[KEY_SERVER_TAGS])
            prefs[KEY_SERVER_TAGS] = encodeMap(current + (scope to tags))
        }
    }

    private suspend fun mutate(field: String, type: String, transform: (List<String>) -> List<String>) {
        val next = transform(decode(store.data.first()[key(field, type)]))
        store.edit { prefs -> prefs[key(field, type)] = encode(next) }
    }

    private fun key(field: String, type: String) = stringPreferencesKey("${field}_${type}")

    private fun decode(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private fun encode(tags: List<String>): String = json.encodeToString(tags)

    private val mapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    private fun decodeMap(raw: String?): Map<String, List<String>> =
        raw?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() }.orEmpty()

    private fun encodeMap(map: Map<String, List<String>>): String = json.encodeToString(mapSerializer, map)

    private companion object {
        val TYPES = listOf("movie", "tv")
        val KEY_SERVER_TAGS = stringPreferencesKey("server_tags")
    }
}
