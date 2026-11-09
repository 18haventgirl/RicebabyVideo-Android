package com.ricebaby.video.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {
    @Test
    fun `档边界按相关性阈值划分`() {
        assertEquals(0, Ranking.tier(1000))
        assertEquals(1, Ranking.tier(999))
        assertEquals(1, Ranking.tier(500))
        assertEquals(2, Ranking.tier(499))
        assertEquals(2, Ranking.tier(200))
        assertEquals(3, Ranking.tier(199))
        assertEquals(3, Ranking.tier(0))
    }

    @Test
    fun `跨档时延迟再低也不能反超相关性`() {
        assertTrue(Ranking.key(1000, 5000L) < Ranking.key(999, 10L))
        assertTrue(Ranking.key(500, 9000L) < Ranking.key(499, 1L))
    }

    @Test
    fun `同档内延迟低的排在前面`() {
        assertTrue(Ranking.key(600, 200L) < Ranking.key(600, 3000L))
        assertTrue(Ranking.key(50, 100L) < Ranking.key(150, 900L))
    }

    @Test
    fun `同档同延迟时相关性高的在前`() {
        assertTrue(Ranking.key(700, 240L) < Ranking.key(600, 240L))
    }

    @Test
    fun `延迟缺失排到本档末尾`() {
        assertTrue(Ranking.key(600, 99999L) < Ranking.key(600, null))
        assertTrue(Ranking.key(600, null) < Ranking.key(499, 1L))
    }
}