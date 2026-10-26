package com.ricebaby.video.data.settings

import com.ricebaby.video.data.model.SiteInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteInstanceCodecTest {

    @Test
    fun `normalizeBaseUrl 补全协议并去掉尾部斜杠`() {
        assertEquals("https://a.com", SiteInstanceCodec.normalizeBaseUrl("a.com"))
        assertEquals("https://a.com/deploy", SiteInstanceCodec.normalizeBaseUrl("  https://a.com/deploy/  "))
        assertEquals("https://a.com", SiteInstanceCodec.normalizeBaseUrl("https://a.com/"))
    }

    @Test
    fun `normalizeBaseUrl 拒绝非法输入`() {
        assertNull(SiteInstanceCodec.normalizeBaseUrl(""))
        assertNull(SiteInstanceCodec.normalizeBaseUrl("ftp://a.com"))
        assertNull(SiteInstanceCodec.normalizeBaseUrl("not a url"))
    }

    @Test
    fun `实例列表编解码 round-trip`() {
        val list = listOf(SiteInstance("id-1", "主站", "https://a.com"), SiteInstance("id-2", "备用", "https://b.com"))
        assertEquals(list, SiteInstanceCodec.decodeInstances(SiteInstanceCodec.encodeInstances(list)))
    }

    @Test
    fun `解码脏数据回落空集合`() {
        assertEquals(emptyList<SiteInstance>(), SiteInstanceCodec.decodeInstances("not json"))
        assertEquals(emptyMap<String, String>(), SiteInstanceCodec.decodePasswords(""))
        val pw = mapOf("id-1" to "secret")
        assertEquals(pw, SiteInstanceCodec.decodePasswords(SiteInstanceCodec.encodePasswords(pw)))
    }
}
