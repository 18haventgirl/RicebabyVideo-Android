package com.ricebaby.video.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uParserTest {

    @Test
    fun `标准两行格式解析组名与显示名`() {
        val raw = """
            |#EXTM3U x-tvg-url="https://epg.xml"
            |#EXTINF:-1 tvg-name="CCTV1" tvg-logo="https://logo/1.png" group-title="央视频道",CCTV-1综合
            |http://live.example.com/cctv1.m3u8?token=abc
            |""".trimMargin()
        val channels = M3uParser.parse(raw)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1综合", channels[0].name)
        assertEquals("央视频道", channels[0].group)
        assertEquals("https://logo/1.png", channels[0].logo)
        assertEquals("http://live.example.com/cctv1.m3u8?token=abc", channels[0].url)
    }

    @Test
    fun `缺 group-title 归未分组`() {
        val raw = "#EXTINF:-1 tvg-name=\"x\",无组频道\nhttp://a/b.m3u8\n"
        val c = M3uParser.parse(raw).single()
        assertEquals("未分组", c.group)
        assertEquals("无组频道", c.name)
    }

    @Test
    fun `注释与空行跳过 裸url无EXTINF不产出`() {
        val raw = """
            |#EXTM3U
           |# something
            |
            |http://orphan/stream.m3u8
            |#EXTINF:-1 group-title="组",有主频道
            |http://ok/stream.m3u8
            |""".trimMargin()
        val channels = M3uParser.parse(raw)
        assertEquals(1, channels.size)
        assertEquals("有主频道", channels[0].name)
    }

    @Test
    fun `显示名含逗号取最后一个逗号后内容`() {
        val raw = "#EXTINF:-1 group-title=\"组\",名字,带逗号\nhttp://a/b\n"
        assertEquals("名字,带逗号", M3uParser.parse(raw).single().name)
    }
}
