package com.ricebaby.video.search

import com.ricebaby.video.data.db.VideoSourceEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchStreamClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SearchStreamClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SearchStreamClient(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `解析 start videos progress complete 事件序列`() {
        val sse = """
            |data: {"type":"start","totalSources":2}
            |
            |data: {"type":"videos","source":"feifan","videos":[{"vod_id":12,"vod_name":"凡人修仙传","source":"feifan","sourceDisplayName":"非凡资源","latency":88}],"completedSources":1,"totalSources":2,"latency":88}
            |
            |data: {"type":"progress","completedSources":1,"totalSources":2,"totalVideosFound":1}
            |
            |data: {"type":"complete","totalVideosFound":1,"totalSources":2,"maxPageCount":3}
            |
            |""".trimMargin()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))
        val events = mutableListOf<SearchEvent>()
        val latch = CountDownLatch(1)
        val handle = client.start(
            server.url("/").toString().removeSuffix("/"),
            "凡人",
            listOf(VideoSourceEntity(id = "feifan", name = "非凡资源", baseUrl = "https://ffzy5.tv/api.php/provide/vod")),
        ) { event ->
            synchronized(events) { events += event }
            if (event is SearchEvent.Complete) latch.countDown()
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        handle.cancel()
        synchronized(events) {
            assertEquals(listOf("Start", "Videos", "Progress", "Complete"), events.map { it::class.simpleName })
            val videos = events.filterIsInstance<SearchEvent.Videos>().first()
            assertEquals("凡人修仙传", videos.videos[0].name)
            assertEquals("12", videos.videos[0].vodId)
        }
        val recorded = server.takeRequest()
        assertEquals("/api/search-parallel", recorded.path)
        val sentBody = recorded.body.readUtf8()
        assertEquals("凡人", kotlinx.serialization.json.Json.parseToJsonElement(sentBody)
            .let { it as kotlinx.serialization.json.JsonObject }["query"]?.toString()?.trim('"'))
        assertTrue(sentBody.contains("ffzy5.tv"))
    }

    @Test
    fun `HTTP 失败产出 Error 事件`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val events = mutableListOf<SearchEvent>()
        val latch = CountDownLatch(1)
        val handle = client.start(server.url("/").toString().removeSuffix("/"), "x", emptyList()) {
            synchronized(events) { events += it }
            if (it is SearchEvent.Error) latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        handle.cancel()
        assertTrue(events.single() is SearchEvent.Error)
    }

    @Test
    fun `坏行跳过不中断流`() {
        val sse = "data: {not json}\n\ndata: {\"type\":\"start\",\"totalSources\":1}\n\ndata: {\"type\":\"complete\",\"totalVideosFound\":0,\"totalSources\":1}\n\n"
        server.enqueue(MockResponse().setBody(sse))
        val events = mutableListOf<SearchEvent>()
        val latch = CountDownLatch(1)
        val handle = client.start(server.url("/").toString().removeSuffix("/"), "x", emptyList()) {
            synchronized(events) { events += it }
            if (it is SearchEvent.Complete) latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        handle.cancel()
        synchronized(events) {
            assertEquals(listOf("Start", "Complete"), events.map { it::class.simpleName })
        }
    }
}
