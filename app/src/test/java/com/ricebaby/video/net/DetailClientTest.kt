package com.ricebaby.video.net

import com.ricebaby.video.data.db.VideoSourceEntity
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

class DetailClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DetailClient

    private val source = VideoSourceEntity(id = "feifan", name = "非凡资源", baseUrl = "https://ffzy.tv/api.php/provide/vod")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DetailClient(
            OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun baseUrl() = server.url("/").toString().removeSuffix("/")

    @Test
    fun `成功解析详情与剧集列表`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":{"vod_id":88,"vod_name":"测试片","vod_pic":"https://p/1.jpg",
                |"type_name":"动作","vod_year":"2024","vod_area":"中国","vod_actor":"甲,乙","vod_director":"丙",
                |"vod_content":"<p>剧情简介</p>","vod_remarks":"HD",
                |"episodes":[{"name":"第1集","url":"https://v/1.m3u8"},{"name":"第2集","url":"https://v/2.m3u8"}],
                |"source":"feifan","source_code":"feiyan"}}""".trimMargin(),
            ),
        )
        val outcome = client.fetch(baseUrl(), "88", source)
        assertTrue(outcome is DetailOutcome.Success)
        val detail = (outcome as DetailOutcome.Success).detail
        assertEquals("测试片", detail.name)
        assertEquals(2, detail.episodes.size)
        assertEquals("第2集", detail.episodes[1].name)
        assertEquals("https://v/2.m3u8", detail.episodes[1].url)
        val recorded = server.takeRequest()
        assertEquals("/api/detail", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"id\":\"88\"") || body.contains("\"id\":88"))
        assertTrue(body.contains("ffzy.tv"))
    }

    @Test
    fun `episodes 缺失时为空列表`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"vod_id":1,"vod_name":"无剧集"}}"""))
        val outcome = client.fetch(baseUrl(), "1", source)
        assertTrue(outcome is DetailOutcome.Success)
        assertEquals(emptyList<com.ricebaby.video.net.Episode>(), (outcome as DetailOutcome.Success).detail.episodes)
    }

    @Test
    fun `success false 判为失败`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":false,"error":"Video not found"}"""))
        val outcome = client.fetch(baseUrl(), "9", source)
        assertTrue(outcome is DetailOutcome.Failed)
    }

    @Test
    fun `HTTP 错误与网络异常均收敛为 Failed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.fetch(baseUrl(), "9", source) is DetailOutcome.Failed)
        server.shutdown()
        assertTrue(client.fetch(baseUrl(), "9", source) is DetailOutcome.Failed)
    }
}
