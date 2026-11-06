package com.ricebaby.video.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUnlockTest {
    private val digest = AppUnlock.sha256Hex("seed-passphrase")

    @Test
    fun `sha256Hex 与标准实现一致`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AppUnlock.sha256Hex(""),
        )
    }

    @Test
    fun `口令与摘要匹配时通过`() {
        assertTrue(AppUnlock.matches("seed-passphrase", digest))
    }

    @Test
    fun `口令不匹配时拒绝`() {
        assertFalse(AppUnlock.matches("seed-passphrase1", digest))
    }

    @Test
    fun `忽略首尾空白但空口令仍然拒绝`() {
        assertTrue(AppUnlock.matches("  seed-passphrase  ", digest))
        assertFalse(AppUnlock.matches("   ", digest))
    }

    @Test
    fun `未配置摘要时一律拒绝`() {
        assertFalse(AppUnlock.matches("seed-passphrase", ""))
    }
}
