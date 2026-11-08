package com.ricebaby.video.data.sources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFreshnessTest {
    private val day = 24L * 3600 * 1000

    @Test
    fun `从未同步过算过期`() {
        assertTrue(SourceFreshness.isStale(null, 1_000_000L))
    }

    @Test
    fun `一天内不算过期`() {
        assertFalse(SourceFreshness.isStale(1_000_000L, 1_000_000L + day - 1))
    }

    @Test
    fun `超过一天算过期`() {
        assertTrue(SourceFreshness.isStale(1_000_000L, 1_000_000L + day + 1))
    }
}