package com.ricebaby.video.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VideoItem(
    @SerialName("vod_id") val vodIdElement: JsonElement? = null,
    @SerialName("vod_name") val name: String = "",
    @SerialName("vod_pic") val pic: String? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("vod_remarks") val remarks: String? = null,
    @SerialName("vod_year") val year: String? = null,
    @SerialName("vod_area") val area: String? = null,
    @SerialName("vod_actor") val actor: String? = null,
    @SerialName("vod_director") val director: String? = null,
    @SerialName("vod_content") val content: String? = null,
    val source: String = "",
    val sourceDisplayName: String? = null,
    val latency: Long? = null,
) {
    val vodId: String
        get() = vodIdElement?.toString()?.trim('"') ?: ""
}
