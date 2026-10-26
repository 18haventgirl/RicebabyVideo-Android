package com.ricebaby.video.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SiteInstance(val id: String, val name: String, val baseUrl: String)
