package com.ricebaby.video.data.settings

import com.ricebaby.video.data.model.SiteInstance

data class AppSettings(
    val instances: List<SiteInstance> = emptyList(),
    val activeInstanceId: String? = null,
    val passwords: Map<String, String> = emptyMap(),
    val proxyHost: String = "",
    val proxyPort: Int = 0,
) {
    val activeInstance: SiteInstance?
        get() = instances.firstOrNull { it.id == activeInstanceId }

    val proxyEnabled: Boolean
        get() = proxyHost.isNotBlank() && proxyPort > 0
}
