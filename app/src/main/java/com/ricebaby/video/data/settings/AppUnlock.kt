package com.ricebaby.video.data.settings

import java.security.MessageDigest

/**
 * 应用侧入口口令校验：只比对 SHA-256 摘要，代码与配置里都不出现口令原文。
 * 摘要由 app/build.gradle.kts 注入，换口令只需改那一处重新打包。
 */
object AppUnlock {
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun matches(passphrase: String, expectedSha256Hex: String): Boolean {
        val input = passphrase.trim()
        if (input.isEmpty() || expectedSha256Hex.isBlank()) return false
        return MessageDigest.isEqual(
            sha256Hex(input).toByteArray(Charsets.UTF_8),
            expectedSha256Hex.lowercase().toByteArray(Charsets.UTF_8),
        )
    }
}
