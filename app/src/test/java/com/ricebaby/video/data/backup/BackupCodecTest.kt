package com.ricebaby.video.data.backup

import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.model.SiteInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {

    @Test
    fun `round-trip 保留全部字段`() {
        val payload = BackupPayload(
            instances = listOf(SiteInstance("i1", "主站", "https://a.com")),
            activeInstanceId = "i1",
            passwords = mapOf("i1" to "pw"),
            proxyHost = "127.0.0.1",
            proxyPort = 7890,
            favorites = listOf(FavoriteEntity(source = "s1", vodId = "9", name = "片", addedAt = 1)),
            history = emptyList(),
            searchHistory = emptyList(),
        )
        val back = BackupCodec.deserialize(BackupCodec.serialize(payload))!!
        assertEquals(payload, back)
    }

    @Test
    fun `坏数据返回 null 不抛出`() {
        assertNull(BackupCodec.deserialize("not json"))
        assertNull(BackupCodec.deserialize(""))
    }
}
