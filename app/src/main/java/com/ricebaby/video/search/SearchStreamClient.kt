package com.ricebaby.video.search

import com.ricebaby.video.data.db.VideoSourceEntity
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface SearchEvent {
    data class Start(val totalSources: Int) : SearchEvent
    data class Videos(val source: String, val videos: List<VideoItem>) : SearchEvent
    data class Progress(val completed: Int, val total: Int, val found: Int) : SearchEvent
    data class Complete(val found: Int, val total: Int) : SearchEvent
    data class Error(val message: String) : SearchEvent
}

@Serializable
private data class SearchSourcePayload(
    val id: String,
    val name: String,
    val baseUrl: String,
    val searchPath: String = "",
    val detailPath: String = "",
    val enabled: Boolean = true,
    val priority: Int = 1,
    val group: String = "normal",
)

@Serializable
private data class SearchRequestPayload(val query: String, val sources: List<SearchSourcePayload>)

@Serializable
private data class SearchEventDto(
    val type: String = "",
    val totalSources: Int = 0,
    val source: String = "",
    val videos: List<VideoItem> = emptyList(),
    val completedSources: Int = 0,
    val totalVideosFound: Int = 0,
    val message: String = "",
)

class SearchHandle internal constructor(private val call: okhttp3.Call) {
    fun cancel() {
        if (!call.isCanceled()) call.cancel()
    }
}

@Singleton
class SearchStreamClient @Inject constructor(
    private val http: OkHttpClient,
) {
    // encodeDefaults：searchPath/detailPath 等空默认值字段也必须发给服务端
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val executor = Executors.newCachedThreadPool()

    fun start(
        baseUrl: String,
        query: String,
        sources: List<VideoSourceEntity>,
        onEvent: (SearchEvent) -> Unit,
    ): SearchHandle {
        val payload = SearchRequestPayload(
            query = query,
            sources = sources.map {
                SearchSourcePayload(
                    id = it.id,
                    name = it.name,
                    baseUrl = it.baseUrl,
                    group = it.group,
                    enabled = it.enabled,
                    priority = it.priority,
                )
            },
        )
        val request = Request.Builder()
            .url("$baseUrl/api/search-parallel")
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(SearchRequestPayload.serializer(), payload).toRequestBody(jsonMedia))
            .build()
        val call = http.newCall(request)
        executor.execute {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        onEvent(SearchEvent.Error("服务返回 HTTP ${response.code}"))
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    var line = source.readUtf8Line()
                    while (line != null) {
                        if (line.startsWith("data: ")) {
                            try {
                                val dto = json.decodeFromString<SearchEventDto>(line.substring(6))
                                mapEvent(dto)?.let(onEvent)
                            } catch (_: Exception) {
                                // 坏行跳过：SSE 流不因单条解析失败中断
                            }
                        }
                        line = source.readUtf8Line()
                    }
                }
            } catch (_: IOException) {
                if (!call.isCanceled()) onEvent(SearchEvent.Error("搜索连接中断"))
            }
        }
        return SearchHandle(call)
    }

    private fun mapEvent(dto: SearchEventDto): SearchEvent? = when (dto.type) {
        "start" -> SearchEvent.Start(dto.totalSources)
        "videos" -> SearchEvent.Videos(
            source = dto.source,
            videos = dto.videos.map { if (it.source.isBlank()) it.copy(source = dto.source) else it },
        )
        "progress" -> SearchEvent.Progress(dto.completedSources, dto.totalSources, dto.totalVideosFound)
        "complete" -> SearchEvent.Complete(dto.totalVideosFound, dto.totalSources)
        "error" -> SearchEvent.Error(dto.message.ifBlank { "搜索出错" })
        else -> null
    }
}
