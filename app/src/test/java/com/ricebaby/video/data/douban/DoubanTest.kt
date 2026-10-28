package com.ricebaby.video.data.douban

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DoubanTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repo() = DoubanRepository(
        OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build(),
    )

    @Test
    fun `tags 解析标签数组`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tags":["热门","最新","华语"]}"""))
        val tags = repo().tags(server.url("/").toString().removeSuffix("/"), "movie")
        assertEquals(listOf("热门", "最新", "华语"), tags)
        assertEquals("/api/douban/tags?type=movie", server.takeRequest().path)
    }

    @Test
    fun `recommend 解析并补全相对封面`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"subjects":[{"id":"37516455","title":"杯雪","cover":"/api/douban/image?url=abc","rate":"7.5","playable":true},
                |{"id":"1","title":"直链","cover":"https://cdn/x.jpg","rate":"9.0","playable":false}]}""".trimMargin(),
            ),
        )
        val base = server.url("/").toString().removeSuffix("/")
        val items = repo().recommend(base, "热门", "movie", 0)
        assertEquals(2, items.size)
        assertEquals("$base/api/douban/image?url=abc", items[0].cover)
        assertEquals("https://cdn/x.jpg", items[1].cover)
        assertEquals("杯雪", items[0].title)
        assertEquals("7.5", items[0].rate)
        val path = server.takeRequest().path!!
        assert(path.startsWith("/api/douban/recommend"))
        assert(path.contains("page_start=0"))
    }

    @Test
    fun `网络或解析异常返回空集合不抛出`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(emptyList<DoubanItem>(), repo().recommend(server.url("/").toString().removeSuffix("/"), "热门", "movie", 0))
    }

    @Test
    fun `推荐标签映射`() {
        assertNull(RecommendTagMapper.bestTag(emptyMap()))
        assertNull(RecommendTagMapper.bestTag(mapOf("动漫" to 1))) // 不足 2 部
        assertEquals("日本", RecommendTagMapper.bestTag(mapOf("动漫" to 2)))
        assertEquals("华语", RecommendTagMapper.bestTag(mapOf("国产剧" to 3, "动漫" to 1)))
        assertEquals("欧美", RecommendTagMapper.bestTag(mapOf("美剧" to 2)))
        assertEquals("热门", RecommendTagMapper.bestTag(mapOf("剧情片" to 2)))
    }
}
