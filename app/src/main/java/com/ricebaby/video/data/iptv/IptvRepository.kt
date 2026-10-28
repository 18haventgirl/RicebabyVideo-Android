package com.ricebaby.video.data.iptv

import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.ProbeOutcome
import com.ricebaby.video.net.SiteClient
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class IptvRepository @Inject constructor(
    private val http: OkHttpClient,
    private val siteClient: SiteClient,
    private val settingsRepository: SettingsRepository,
) {
    /** 加载结果：频道列表 + 失败的源地址 */
    data class IptvLoad(val channels: List<M3uParser.IptvChannel>, val failedSources: List<String>)

    suspend fun loadChannels(): Result<IptvLoad> = withContext(Dispatchers.IO) {
        runCatching {
            val instance = settingsRepository.state.first().activeInstance
                ?: return@runCatching IptvLoad(emptyList(), emptyList())
            val probe = siteClient.probe(instance.baseUrl)
            val sources = (probe as? ProbeOutcome.Reachable)?.iptvSources
                ?.split(",")?.map { it.trim() }?.filter { it.startsWith("http") }
                .orEmpty()
            val channels = mutableListOf<M3uParser.IptvChannel>()
            val failed = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            for (src in sources) {
                try {
                    val proxyUrl = "${instance.baseUrl}/api/proxy?url=" + URLEncoder.encode(src, "UTF-8")
                    val request = Request.Builder().url(proxyUrl).build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            failed += src
                            return@use
                        }
                        val parsed = M3uParser.parse(response.body?.string().orEmpty())
                        parsed.forEach { if (seen.add("${it.name}|${it.url}")) channels += it }
                    }
                } catch (_: IOException) {
                    failed += src
                }
            }
            IptvLoad(channels, failed)
        }
    }
}
