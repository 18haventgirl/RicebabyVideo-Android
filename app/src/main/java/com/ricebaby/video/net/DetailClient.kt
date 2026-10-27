package com.ricebaby.video.net

import com.ricebaby.video.data.db.VideoSourceEntity
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class Episode(val name: String, val url: String)

data class VideoDetail(
    val id: String,
    val name: String,
    val pic: String?,
    val typeName: String?,
    val year: String?,
    val area: String?,
    val actor: String?,
    val director: String?,
    val content: String?,
    val remarks: String?,
    val source: String,
    val episodes: List<Episode>,
)

sealed interface DetailOutcome {
    data class Success(val detail: VideoDetail) : DetailOutcome
    data class Failed(val reason: String) : DetailOutcome
}

@Serializable
private data class DetailSourcePayload(
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
private data class DetailRequestPayload(val id: String, val source: DetailSourcePayload)

@Serializable
private data class DetailEpisodeDto(val name: String = "", val url: String = "")

@Serializable
private data class DetailDataDto(
    @SerialName("vod_id") val vodIdElement: JsonElement? = null,
    @SerialName("vod_name") val name: String = "",
    @SerialName("vod_pic") val pic: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_year") val year: String? = null,
    @SerialName("vod_area") val area: String? = null,
    @SerialName("vod_actor") val actor: String? = null,
    @SerialName("vod_director") val director: String? = null,
    @SerialName("vod_content") val content: String? = null,
    @SerialName("vod_remarks") val remarks: String? = null,
    val source: String = "",
    val episodes: List<DetailEpisodeDto> = emptyList(),
)

@Serializable
private data class DetailResponseDto(val success: Boolean = false, val data: DetailDataDto? = null)

class DetailClient @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun fetch(baseUrl: String, id: String, source: VideoSourceEntity): DetailOutcome =
        withContext(Dispatchers.IO) {
            val payload = DetailRequestPayload(
                id = id,
                source = DetailSourcePayload(
                    id = source.id,
                    name = source.name,
                    baseUrl = source.baseUrl,
                    group = source.group,
                    enabled = source.enabled,
                    priority = source.priority,
                ),
            )
            val request = Request.Builder()
                .url("$baseUrl/api/detail")
                .post(json.encodeToString(DetailRequestPayload.serializer(), payload).toRequestBody(jsonMedia))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use DetailOutcome.Failed("服务返回 HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty().ifBlank { "{}" }
                    val dto = json.decodeFromString<DetailResponseDto>(body)
                    val data = dto.data
                    if (!dto.success || data == null) {
                        DetailOutcome.Failed("未找到该影片的详情")
                    } else {
                        DetailOutcome.Success(
                            VideoDetail(
                                id = data.vodIdElement?.toString()?.trim('"').orEmpty(),
                                name = data.name,
                                pic = data.pic,
                                typeName = data.typeName,
                                year = data.year,
                                area = data.area,
                                actor = data.actor,
                                director = data.director,
                                content = data.content,
                                remarks = data.remarks,
                                source = data.source,
                                episodes = data.episodes
                                    .filter { it.url.isNotBlank() }
                                    .mapIndexed { index, e -> Episode(e.name.ifBlank { "第${index + 1}集" }, e.url) },
                            ),
                        )
                    }
                }
            } catch (_: IOException) {
                DetailOutcome.Failed("无法连接站点")
            } catch (_: SerializationException) {
                DetailOutcome.Failed("详情返回格式不符合预期")
            }
        }
}
