package com.ricebaby.video.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelevanceScoringTest {

    private fun item(
        name: String,
        actor: String? = null,
        director: String? = null,
        year: String? = null,
        remarks: String? = null,
    ) = VideoItem(name = name, actor = actor, director = director, year = year, remarks = remarks)

    @Test
    fun `最小匹配-查询任意连续两字命中即通过`() {
        assertTrue(hasMinimumMatch("凡人修仙传", "凡人"))
        assertFalse(hasMinimumMatch("仙逆", "凡人修仙")) // 查询两字子串 凡人/人修/修仙 都不在标题
        assertTrue(hasMinimumMatch("仙逆", "凡人仙逆")) // 子串 仙逆 命中
        assertFalse(hasMinimumMatch("abc", "凡人"))
    }

    @Test
    fun `完全同名直接返回最高分`() {
        assertEquals(1000, relevanceScore(item("斗破苍穹"), "斗破苍穹", 2026))
    }

    @Test
    fun `前缀与包含叠加位置与词元加分`() {
        val score = relevanceScore(item("凡人修仙传"), "凡人", 2026)
        // startsWith 500 + contains 200 + 位置 50 + 词元 30+20 = 800
        assertEquals(800, score)
    }

    @Test
    fun `演员与年份画质备注提升排名`() {
        val base = relevanceScore(item("测试片"), "测试", 2026)
        val withActor = relevanceScore(item("测试片", actor = "测试某人"), "测试", 2026)
        assertTrue(withActor > base)
        val fresh = relevanceScore(item("测试片", year = "2026", remarks = "1080P"), "测试", 2026)
        assertTrue(fresh > base)
    }

    @Test
    fun `超长标题扣分但不为负`() {
        val longName = "测".repeat(60)
        assertTrue(relevanceScore(item(longName), "不存在", 2026) >= 0)
    }
}
