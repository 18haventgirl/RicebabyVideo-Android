package com.ricebaby.video.data.sources

import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.db.VideoSourceEntity
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
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

    suspend fun sync(subscriptionUrls: String, siteBaseUrl: String? = null): SyncOutcome = withContext(Dispatchers.IO) {
        val urls = subscriptionUrls.split(",").map { it.trim() }.filter { it.startsWith("http") }
        var accepted = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (url in urls) {
            val raw = fetchSubscription(url, siteBaseUrl, errors) ?: continue
            try {
                val all = parseSources(raw).map { it.copy(subscriptionUrl = url) }
                val fresh = all.filter { seen.add(it.id) }
                skipped += all.size - fresh.size
                dao.deleteBySubscription(url)
                dao.upsertAll(fresh)
                accepted += fresh.size
            } catch (_: Exception) {
                errors += "$url → 内容无法解析"
            }
        }
        SyncOutcome(accepted, skipped, errors)
    }

    /**
     * 清单托管在 GitHub 之类的境外地址时国内直连会被墙：先 3s 短超时直连（境外网络走快路径），
     * 失败改走站点 /api/proxy 由服务端代取，手机从此只跟站点一个域名说话。
     */
    private fun fetchSubscription(url: String, siteBaseUrl: String?, errors: MutableList<String>): String? {
        val attempts = mutableListOf(url to http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build())
        if (siteBaseUrl != null) {
            val viaSite = "$siteBaseUrl/api/proxy?url=" + URLEncoder.encode(url, "UTF-8")
            attempts += viaSite to http.newBuilder().callTimeout(40, TimeUnit.SECONDS).build()
        }
        for ((target, client) in attempts) {
            try {
                val request = Request.Builder().url(target).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body?.string().orEmpty()
                    errors += "$target → HTTP ${response.code}"
                }
            } catch (e: IOException) {
                errors += "$target → ${e.message ?: "网络错误"}"
            }
        }
        return null
    }
}
