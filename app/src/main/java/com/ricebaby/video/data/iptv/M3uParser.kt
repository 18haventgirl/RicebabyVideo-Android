package com.ricebaby.video.data.iptv

object M3uParser {

    data class IptvChannel(val name: String, val group: String, val url: String, val logo: String?)

    fun parse(raw: String): List<IptvChannel> {
        val result = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF") -> {
                    // 属性段不含逗号、显示名可含逗号：取第一个逗号之后为显示名
                    val comma = trimmed.indexOf(',')
                    val attrs = if (comma >= 0) trimmed.substring(0, comma) else trimmed
                    pendingName = if (comma >= 0 && comma < trimmed.length - 1) trimmed.substring(comma + 1).trim() else ""
                    pendingGroup = attrOf(attrs, "group-title") ?: "未分组"
                    pendingLogo = attrOf(attrs, "tvg-logo")
                }
                trimmed.isEmpty() || trimmed.startsWith("#") -> continue
                pendingName != null -> {
                    if (pendingName.isNotBlank()) {
                        result += IptvChannel(pendingName, pendingGroup ?: "未分组", trimmed, pendingLogo?.ifBlank { null })
                    }
                    pendingName = null
                    pendingGroup = null
                    pendingLogo = null
                }
                // 无 EXTINF 前导的裸 url：跳过
            }
        }
        return result
    }

    private fun attrOf(attrs: String, key: String): String? {
        val start = attrs.indexOf("$key=\"")
        if (start < 0) return null
        val from = start + key.length + 2
        val end = attrs.indexOf('"', from)
        return if (end < 0) null else attrs.substring(from, end)
    }
}
