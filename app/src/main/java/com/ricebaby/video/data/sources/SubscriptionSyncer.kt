package com.ricebaby.video.data.sources

import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.db.VideoSourceEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class ImportSourceFormat(
    val id: String,
    val name: String,
    val baseUrl: String,
    val group: String? = "normal",
    val enabled: Boolean? = true,
    val priority: Int? = 1,
)

data class SyncOutcome(val accepted: Int, val skipped: Int, val errors: List<String>)

@Singleton
class SubscriptionSyncer @Inject constructor(
    private val http: OkHttpClient,
    private val dao: SourceDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 与 Web 端 parseSourcesFromJson 对齐：数组 / {sources} / {list} 三种结构 */
    fun parseSources(raw: String): List<VideoSourceEntity> {
        val now = System.currentTimeMillis()
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["sources"] ?: element["list"])?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { item ->
            runCatching { json.decodeFromJsonElement(ImportSourceFormat.serializer(), item) }.getOrNull()
        }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.baseUrl.isNotBlank() }
            .map {
                VideoSourceEntity(
                    id = it.id,
                    name = it.name,
                    baseUrl = it.baseUrl.trimEnd('/'),
                    group = it.group ?: "normal",
                    enabled = it.enabled != false,
                    priority = it.priority ?: 1,
                    updatedAt = now,
                )
            }
    }

    suspend fun sync(subscriptionUrls: String): SyncOutcome = withContext(Dispatchers.IO) {
        val urls = subscriptionUrls.split(",").map { it.trim() }.filter { it.startsWith("http") }
        var accepted = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        errors += "$url → HTTP ${response.code}"
                        return@use
                    }
                    val all = parseSources(response.body?.string().orEmpty()).map { it.copy(subscriptionUrl = url) }
                    val fresh = all.filter { seen.add(it.id) }
                    skipped += all.size - fresh.size
                    dao.deleteBySubscription(url)
                    dao.upsertAll(fresh)
                    accepted += fresh.size
                }
            } catch (e: IOException) {
                errors += "$url → ${e.message ?: "网络错误"}"
            } catch (_: Exception) {
                errors += "$url → 内容无法解析"
            }
        }
        SyncOutcome(accepted, skipped, errors)
    }
}
