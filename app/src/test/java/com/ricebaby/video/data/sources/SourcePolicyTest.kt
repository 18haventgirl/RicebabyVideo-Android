package com.ricebaby.video.data.sources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePolicyTest {
    @Test
    fun `清单里出现过的成人站全部命中`() {
        val adults = listOf(
            "黄色仓库" to "https://hsckzy.vip/api.php/provide/vod",
            "淫水机资源站" to "https://www.xrbsp.com/api/json.php",
            "白嫖资源站" to "https://www.kxgav.com/api/json.php",
            "黄AV资源站" to "https://www.pgxdy.com/api/json.php",
            "91麻豆" to "https://91md.me/api.php/provide/vod",
        )
        adults.forEach { (name, url) -> assertTrue("$name 应被拦下", SourcePolicy.isAdult(name, url)) }
    }

    @Test
    fun `正常影视采集站不被误伤`() {
        val normal = listOf(
            "非凡资源" to "http://ffzy5.tv/api.php/provide/vod",
            "卧龙资源" to "https://wolongzyw.com/api.php/provide/vod",
            "暴风资源" to "https://bfzyapi.com/api.php/provide/vod",
            "最大资源" to "https://api.zuidapi.com/api.php/provide/vod",
            "无尽资源" to "https://api.wujinapi.me/api.php/provide/vod",
            "极速资源" to "https://jszyapi.com/api.php/provide/vod",
            "魔都资源" to "https://moduzy.com/api.php/provide/vod",
            "乐子HTTP" to "https://lezizy.com/api.php/provide/vod",
            "1080JSON" to "https://1080zyk1.com/api.php/provide/vod",
            "360资源" to "https://360zy.com/api.php/provide/vod",
            "iKun资源" to "https://ikunzy.net/api.php/provide/vod",
            "量子资源" to "https://cj.lziapi.com/api.php/provide/vod",
        )
        normal.forEach { (name, url) -> assertFalse("$name 不该被拦", SourcePolicy.isAdult(name, url)) }
    }

    @Test
    fun `域名带成人关键词但名字正常的站也命中`() {
        assertTrue(SourcePolicy.isAdult("某某资源", "https://missav.com/api"))
        assertTrue(SourcePolicy.isAdult("某某资源", "https://javbus.com/api"))
    }

    @Test
    fun `baseUrl 不是合法 URL 时不抛异常`() {
        assertFalse(SourcePolicy.isAdult("非凡资源", "not a url"))
    }
}