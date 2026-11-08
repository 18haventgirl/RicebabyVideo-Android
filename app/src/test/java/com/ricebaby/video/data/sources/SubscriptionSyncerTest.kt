package com.ricebaby.video.data.sources

import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.db.VideoSourceEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSourceDao : SourceDao {
    val stored = mutableMapOf<String, VideoSourceEntity>()

    override fun observeEnabled(): Flow<List<VideoSourceEntity>> = throw UnsupportedOperationException()
    override suspend fun enabledOnce(): List<VideoSourceEntity> = stored.values.toList()
    override suspend fun allOnce(): List<VideoSourceEntity> = stored.values.toList()
    override suspend fun deleteByIds(ids: List<String>) { ids.forEach { stored.remove(it) } }
    override fun observeCount(): Flow<Int> = throw UnsupportedOperationException()
    override suspend fun upsertAll(sources: List<VideoSourceEntity>) { sources.forEach { stored[it.id] = it } }
    override suspend fun deleteBySubscription(url: String) { stored.values.removeAll { it.subscriptionUrl == url } }
    override suspend fun clearAll() { stored.clear() }
}

class SubscriptionSyncerTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: FakeSourceDao
    private lateinit var syncer: SubscriptionSyncer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = FakeSourceDao()
        syncer = SubscriptionSyncer(
            OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build(),
            dao,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `解析数组格式并过滤非法条目`() {
        val raw = """[{"id":"a","name":"A资源","baseUrl":"https://a.com/api","group":"normal","enabled":true,"priority":1},
            |{"id":"","name":"坏","baseUrl":"https://b.com"},{"name":"缺id","baseUrl":"https://c.com"},"字符串"]""".trimMargin()
        val parsed = syncer.parseSources(raw)
        assertEquals(1, parsed.size)
        assertEquals("a", parsed[0].id)
    }

    @Test
    fun `解析 sources 与 list 包装格式`() {
        assertEquals(1, syncer.parseSources("""{"sources":[{"id":"x","name":"X","baseUrl":"https://x.com"}]}""").size)
        assertEquals(1, syncer.parseSources("""{"list":[{"id":"y","name":"Y","baseUrl":"https://y.com"}]}""").size)
    }

    @Test
    fun `解析时丢弃成人采集站`() {
        val raw = """[
            |{"id":"hsck","name":"黄色仓库","baseUrl":"https://hsckzy.vip/api.php/provide/vod"},
            |{"id":"xrbsp","name":"淫水机资源站","baseUrl":"https://www.xrbsp.com/api/json.php"},
            |{"id":"kxgav","name":"白嫖资源站","baseUrl":"https://www.kxgav.com/api/json.php"},
            |{"id":"pgxdy","name":"黄AV资源站","baseUrl":"https://www.pgxdy.com/api/json.php"},
            |{"id":"madou","name":"91麻豆","baseUrl":"https://91md.me/api.php/provide/vod"},
            |{"id":"ff","name":"非凡资源","baseUrl":"http://ffzy5.tv/api.php/provide/vod"}]""".trimMargin()
        val parsed = syncer.parseSources(raw)
        assertEquals(listOf("ff"), parsed.map { it.id })
    }

    @Test
    fun `sync 拉取两个订阅并跨订阅按 id 去重`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"dup","name":"一","baseUrl":"https://1.com"},{"id":"u1","name":"独一","baseUrl":"https://u.com"}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"dup","name":"二","baseUrl":"https://2.com"}]"""))
        val base = server.url("/").toString().removeSuffix("/")
        val outcome = syncer.sync("$base/a.json,$base/b.json,")
        assertEquals(2, outcome.accepted)
        assertEquals(1, outcome.skipped)
        assertEquals(2, dao.stored.size)
        assertEquals("一", dao.stored["dup"]?.name) // 先到先得
    }

    @Test
    fun `单个订阅失败不影响其他订阅`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"ok","name":"OK","baseUrl":"https://ok.com"}]"""))
        val base = server.url("/").toString().removeSuffix("/")
        val outcome = syncer.sync("http://127.0.0.1:1/x.json,$base/ok.json")
        assertEquals(1, outcome.accepted)
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors[0].contains("127.0.0.1"))
    }

    @Test
    fun `直连失败时改走站点代理取清单`() = runBlocking {
        val site = MockWebServer()
        site.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            site.enqueue(MockResponse().setBody("""[{"id":"p","name":"代理取回","baseUrl":"https://p.com"}]"""))
            val siteBase = site.url("/").toString().removeSuffix("/")
            val outcome = syncer.sync(server.url("/list.json").toString(), siteBase)
            assertEquals(1, outcome.accepted)
            assertEquals("代理取回", dao.stored["p"]?.name)
            assertTrue(site.takeRequest().path!!.startsWith("/api/proxy?url="))
        } finally {
            site.shutdown()
        }
    }

    @Test
    fun `直连与站点代理都失败时不写库`() = runBlocking {
        val site = MockWebServer()
        site.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            site.enqueue(MockResponse().setResponseCode(502))
            val outcome = syncer.sync(server.url("/list.json").toString(), site.url("/").toString().removeSuffix("/"))
            assertEquals(0, outcome.accepted)
            assertTrue(dao.stored.isEmpty())
        } finally {
            site.shutdown()
        }
    }
}
