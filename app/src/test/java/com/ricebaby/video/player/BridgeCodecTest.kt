package com.ricebaby.video.player

import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.data.db.VideoSourceEntity
import com.ricebaby.video.search.VideoItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val source = VideoSourceEntity(id = "feifan", name = "非凡资源", baseUrl = "https://ffzy.tv/api")

    @Test
    fun `settingsJson 输出 Web 端可解析的源配置`() {
        val settings = json.parseToJsonElement(BridgeCodec.settingsJson(listOf(source))).jsonObject
        val sources = settings["sources"]!!.jsonArray
        assertEquals(1, sources.size)
        val first = sources[0].jsonObject
        assertEquals("feifan", first["id"]!!.jsonPrimitive.content)
        assertEquals("https://ffzy.tv/api", first["baseUrl"]!!.jsonPrimitive.content)
        assertNotNull(first["searchPath"])
        assertNotNull(first["detailPath"])
        assertTrue(first["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("normal", first["group"]!!.jsonPrimitive.content)
    }

    @Test
    fun `historyJson 带 version 2 包裹与 showIdentifier`() {
        val history = HistoryEntity(
            source = "feifan", vodId = "88", name = "测试片", pic = "https://p/1.jpg",
            typeName = "动作", episodeIndex = 2, positionMs = 5000, durationMs = 60000, updatedAt = 123,
        )
        val root = json.parseToJsonElement(BridgeCodec.historyJson(listOf(history))).jsonObject
        assertEquals(2, root["version"]!!.jsonPrimitive.int)
        val item = root["state"]!!.jsonObject["viewingHistory"]!!.jsonArray[0].jsonObject
        assertEquals("title:测试片", item["showIdentifier"]!!.jsonPrimitive.content)
        assertEquals(2, item["episodeIndex"]!!.jsonPrimitive.int)
        assertEquals(5, item["playbackPosition"]!!.jsonPrimitive.long) // 注入转秒
        assertEquals(123, item["timestamp"]!!.jsonPrimitive.long)
    }

    @Test
    fun `favoritesJson 无 version 包裹`() {
        val fav = FavoriteEntity(source = "feifan", vodId = "88", name = "测试片", addedAt = 9)
        val root = json.parseToJsonElement(BridgeCodec.favoritesJson(listOf(fav))).jsonObject
        val item = root["state"]!!.jsonObject["favorites"]!!.jsonArray[0].jsonObject
        assertEquals("测试片", item["title"]!!.jsonPrimitive.content)
        assertEquals(9, item["addedAt"]!!.jsonPrimitive.long)
    }

    @Test
    fun `documentStartScript 是自包含 try-catch 且包含全部键`() {
        val script = BridgeCodec.documentStartScript(
            settingsJson = "{}", historyJson = "{}", favoritesJson = "{}", gsScript = "",
        )
        assertTrue(script.contains("try{"))
        assertTrue(script.contains("ricebabyvideo-settings"))
        assertTrue(script.contains("ricebabyvideo-history-store"))
        assertTrue(script.contains("ricebabyvideo-favorites-store"))
    }

    @Test
    fun `gsScript 写入 sessionStorage 键前缀 gs 冒号`() {
        val candidates = listOf(VideoItem(name = "片A", source = "s1", sourceDisplayName = "资源1"))
        val script = BridgeCodec.gsScript(candidates, "appkey")
        assertTrue(script.contains("sessionStorage.setItem"))
        assertTrue(script.contains("gs:appkey"))
    }

    @Test
    fun `回读解析 正常与坏数据`() {
        val raw = """{"state":{"viewingHistory":[{"videoId":88,"title":"片A","source":"s1","episodeIndex":1,"playbackPosition":100.5,"duration":900,"timestamp":555,"poster":null}]},"version":2}"""
        val parsed = BridgeCodec.parseWebHistory(raw)
        assertEquals(1, parsed.size)
        assertEquals("88", parsed[0].videoId)
        assertEquals(555, parsed[0].timestamp)
        assertEquals(100500, parsed[0].playbackPosition) // 秒→毫秒
        assertTrue(BridgeCodec.parseWebHistory(null).isEmpty())
        assertTrue(BridgeCodec.parseWebHistory("not json").isEmpty())
        assertTrue(BridgeCodec.parseWebHistory("""{"state":{}}""").isEmpty())
    }

    private fun webItem(id: String = "88", ts: Long = 100, pos: Long = 10) =
        com.ricebaby.video.player.BridgeCodec.WebHistoryItem(
            videoId = id, title = "片A", source = "s1", episodeIndex = 1,
            playbackPosition = pos, duration = 1000, timestamp = ts, poster = null,
        )

    private fun localItem(ts: Long = 50, pos: Long = 5) = HistoryEntity(
        source = "s1", vodId = "88", name = "片A", episodeIndex = 0,
        positionMs = pos, durationMs = 1000, updatedAt = ts,
    )

    @Test
    fun `历史合并 Web 更新则取 Web 进度`() {
        val merged = BridgeCodec.mergeHistory(listOf(webItem(ts = 100, pos = 10)), listOf(localItem(ts = 50, pos = 5)))
        assertEquals(1, merged.size)
        assertEquals(10, merged[0].positionMs)
        assertEquals(100, merged[0].updatedAt)
        assertEquals(1, merged[0].episodeIndex)
    }

    @Test
    fun `历史合并 本地更新则保留本地`() {
        val merged = BridgeCodec.mergeHistory(listOf(webItem(ts = 10, pos = 1)), listOf(localItem(ts = 99, pos = 77)))
        assertEquals(77, merged[0].positionMs)
        assertEquals(99, merged[0].updatedAt)
    }

    @Test
    fun `历史合并 双向并集不丢数据`() {
        val otherLocal = localItem(ts = 200).copy(vodId = "99", name = "片B")
        val merged = BridgeCodec.mergeHistory(listOf(webItem()), listOf(localItem(), otherLocal))
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.vodId == "99" })
    }

    @Test
    fun `收藏合并取并集按 source 与 vodId 去重`() {
        val web = listOf(
            BridgeCodec.WebFavoriteItem(videoId = "88", title = "片A", source = "s1", sourceName = "资源1", poster = null, addedAt = 1, typeName = null, year = null, remarks = null),
            BridgeCodec.WebFavoriteItem(videoId = "77", title = "片C", source = "s1", sourceName = "资源1", poster = null, addedAt = 3, typeName = null, year = null, remarks = null),
        )
        val local = listOf(FavoriteEntity(source = "s1", vodId = "88", name = "片A", addedAt = 2))
        val merged = BridgeCodec.mergeFavorites(web, local)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.vodId == "77" })
        assertEquals(1, merged.first { it.vodId == "88" }.addedAt) // 取更早的加入时间（Web 首次收藏时间）
    }
}
