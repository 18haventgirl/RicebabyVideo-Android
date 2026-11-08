package com.ricebaby.video.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRetryPolicyTest {
    @Test
    fun `无结果且没重试过时重连`() {
        assertTrue(SearchRetryPolicy.shouldRetry(hasResults = false, attempts = 0))
    }

    @Test
    fun `只重连一次`() {
        assertFalse(SearchRetryPolicy.shouldRetry(hasResults = false, attempts = 1))
    }

    @Test
    fun `已有结果时不重连避免重复流`() {
        assertFalse(SearchRetryPolicy.shouldRetry(hasResults = true, attempts = 0))
    }
}