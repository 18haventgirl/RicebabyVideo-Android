package com.ricebaby.video.data.douban

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class DoubanItem(
    val id: String,
    val title: String,
    val cover: String,
    val rate: String,
    val playable: Boolean,
)

@Serializable
private data class TagsResponse(val tags: List<String> = emptyList())

@Serializable
private data class DoubanSubjectDto(
    val id: String = "",
    val title: String = "",
    val cover: String = "",
    val rate: String = "",
    val playable: Boolean = false,
    @SerialName("episodes_info") val episodesInfo: String? = null,
)

@Serializable
private data class RecommendResponse(val subjects: List<DoubanSubjectDto> = emptyList())

class DoubanRepository @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun tags(baseUrl: String, type: String): List<String> =
        get("$baseUrl/api/douban/tags?type=$type") { json.decodeFromString<TagsResponse>(it).tags } ?: emptyList()

    suspend fun recommend(baseUrl: String, tag: String, type: String, pageStart: Int): List<DoubanItem> =
        get(
            "$baseUrl/api/douban/recommend?tag=${java.net.URLEncoder.encode(tag, "UTF-8")}&type=$type&page_limit=20&page_start=$pageStart",
        ) { raw ->
            json.decodeFromString<RecommendResponse>(raw).subjects.map {
                DoubanItem(
                    id = it.id,
                    title = it.title,
                    cover = if (it.cover.startsWith("/")) baseUrl + it.cover else it.cover,
                    rate = it.rate,
                    playable = it.playable,
                )
            }
        } ?: emptyList()

    private suspend fun <T> get(url: String, parse: (String) -> T): T? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parse(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }
}
