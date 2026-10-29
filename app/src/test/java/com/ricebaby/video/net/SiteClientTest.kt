package com.ricebaby.video.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SiteClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SiteClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SiteClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString().removeSuffix("/")

    @Test
    fun `probe 读取 hasAuth premium 与订阅源字段并忽略多余字段`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"hasAuth":true,"hasPremiumAuth":false,"persistSession":true,"subscriptionSources":"https://sub.example/a.json","iptvSources":"https://live.example.m3u"}"""),
        )
        val outcome = client.probe(baseUrl())
        assertEquals(
            ProbeOutcome.Reachable(hasAuth = true, hasPremiumAuth = false, subscriptionSources = "https://sub.example/a.json"),
            outcome,
        )
        assertEquals("/api/auth", server.takeRequest().path)
    }

    @Test
    fun `probe HTTP 错误判为不可达`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val outcome = client.probe(baseUrl())
        assertTrue(outcome is ProbeOutcome.Unreachable)
    }

    @Test
    fun `login 成功返回身份与 profileId`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"valid":true,"name":"管理员","role":"super_admin","profileId":"abcd0123456789ef"}"""),
        )
        val outcome = client.login(baseUrl(), "hunter2")
        assertEquals(LoginOutcome.Success("管理员", "super_admin", "abcd0123456789ef"), outcome)
        val recorded = server.takeRequest()
        assertEquals("/api/auth", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("hunter2"))
    }

    @Test
    fun `login 密码错误判为 Rejected 而非异常`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"valid":false}"""))
        val outcome = client.login(baseUrl(), "wrong")
        assertEquals(LoginOutcome.Rejected, outcome)
    }

    @Test
    fun `服务器不可达时 probe 返回 Unreachable 不抛异常`() = runBlocking {
        val deadPort = server.port
        server.shutdown()
        val outcome = client.probe("http://127.0.0.1:$deadPort")
        assertTrue(outcome is ProbeOutcome.Unreachable)
    }
}
