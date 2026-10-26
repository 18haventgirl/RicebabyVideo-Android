package com.ricebaby.video.data.settings

import com.ricebaby.video.data.model.SiteInstance
import java.util.UUID
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SiteInstanceCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> trimmed
            trimmed.contains("://") -> return null // 显式声明了其它协议，拒绝
            else -> "https://$trimmed"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return url.toString().removeSuffix("/")
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun encodeInstances(instances: List<SiteInstance>): String = json.encodeToString(instances)

    fun decodeInstances(raw: String): List<SiteInstance> = try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyList()
    }

    fun encodePasswords(passwords: Map<String, String>): String = json.encodeToString(passwords)

    fun decodePasswords(raw: String): Map<String, String> = try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyMap()
    }
}
