package com.ricebaby.video.ui.detail

import com.ricebaby.video.search.VideoItem
import javax.inject.Inject
import javax.inject.Singleton

data class DetailPayload(val target: VideoItem, val candidates: List<VideoItem>)

/** 详情页跳转的数据交接：完整候选集放内存，导航参数只带 key，避免超长 URL */
@Singleton
class DetailRegistry @Inject constructor() {
    private val payloads = java.util.concurrent.ConcurrentHashMap<String, DetailPayload>()

    fun put(key: String, payload: DetailPayload) {
        payloads[key] = payload
        while (payloads.size > 40) {
            payloads.keys.firstOrNull { it != key }?.let(payloads::remove)
        }
    }

    fun take(key: String): DetailPayload? = payloads[key]
}
