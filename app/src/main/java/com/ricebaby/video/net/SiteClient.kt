package com.ricebaby.video.net

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ProbeOutcome {
    data class Reachable(
        val hasAuth: Boolean,
        val hasPremiumAuth: Boolean,
        val subscriptionSources: String,
    ) : ProbeOutcome
    data class Unreachable(val reason: String) : ProbeOutcome
}

sealed interface LoginOutcome {
    data class Success(val name: String, val role: String, val profileId: String) : LoginOutcome
    data object Rejected : LoginOutcome
    data class Unreachable(val reason: String) : LoginOutcome
}

@Serializable
private data class AuthGetResponse(
    val hasAuth: Boolean = false,
    val hasPremiumAuth: Boolean = false,
    val subscriptionSources: String = "",
)

@Serializable
private data class AuthPostRequest(val password: String)

@Serializable
private data class AuthPostResponse(
    val valid: Boolean = false,
    val name: String? = null,
    val role: String? = null,
    val profileId: String? = null,
)

class SiteClient @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun probe(baseUrl: String): ProbeOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/auth").get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ProbeOutcome.Unreachable("服务返回 HTTP ${response.code}")
                } else {
                    val body = json.decodeFromString<AuthGetResponse>(response.body?.string().orEmpty().ifBlank { "{}" })
                    ProbeOutcome.Reachable(
                        hasAuth = body.hasAuth,
                        hasPremiumAuth = body.hasPremiumAuth,
                        subscriptionSources = body.subscriptionSources,
                    )
                }
            }
        } catch (_: IOException) {
            ProbeOutcome.Unreachable("无法连接站点")
        } catch (_: SerializationException) {
            ProbeOutcome.Unreachable("站点返回格式不符合预期")
        }
    }

    suspend fun login(baseUrl: String, password: String): LoginOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth")
            .post(json.encodeToString(AuthPostRequest(password)).toRequestBody(jsonMedia))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LoginOutcome.Unreachable("服务返回 HTTP ${response.code}")
                } else {
                    val body = json.decodeFromString<AuthPostResponse>(response.body?.string().orEmpty().ifBlank { "{}" })
                    when {
                        body.valid -> LoginOutcome.Success(
                            name = body.name.orEmpty(),
                            role = body.role.orEmpty(),
                            profileId = body.profileId.orEmpty(),
                        )
                        else -> LoginOutcome.Rejected
                    }
                }
            }
        } catch (_: IOException) {
            LoginOutcome.Unreachable("无法连接站点")
        } catch (_: SerializationException) {
            LoginOutcome.Unreachable("站点返回格式不符合预期")
        }
    }
}
