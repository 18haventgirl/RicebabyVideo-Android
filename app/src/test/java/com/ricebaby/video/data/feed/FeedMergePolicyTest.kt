package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedMergePolicyTest {
    private fun items(n: Int) = (1..n).map { DoubanItem(id = "$it", title = "片$it", cover = "", rate = "", playable = true) }

    @Test
    fun `正常替换用新列表`() {
        val d = FeedMergePolicy.decideReplace(items(20), items(20))
        assertEquals(20, d.items.size)
        assertFalse(d.keptOld)
        assertFalse(d.failed)
    }

    @Test
    fun `请求失败且有旧数据时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, null)
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
        assertTrue(d.failed)
    }

    @Test
    fun `请求失败且无旧数据时标记失败`() {
        val d = FeedMergePolicy.decideReplace(emptyList(), null)
        assertTrue(d.items.isEmpty())
        assertTrue(d.failed)
        assertFalse(d.keptOld)
    }

    @Test
    fun `新列表骤减到旧列表三分之一以下时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, items(6))
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
        assertFalse(d.failed)
    }

    @Test
    fun `新列表为空且旧列表非空时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, emptyList())
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
    }

    @Test
    fun `旧列表为空时空结果不算截断`() {
        val d = FeedMergePolicy.decideReplace(emptyList(), emptyList())
        assertTrue(d.items.isEmpty())
        assertFalse(d.keptOld)
        assertFalse(d.failed)
    }
}