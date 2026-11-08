package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshNoticeTest {
    private fun decision(keptOld: Boolean = false, failed: Boolean = false) =
        ReplaceDecision(emptyList<DoubanItem>(), keptOld, failed)

    @Test
    fun `同步或内容失败时给出原因并说明保留旧内容`() {
        assertEquals(
            "刷新失败：站点连不上，已保留上次内容",
            RefreshNotice.compose("站点连不上", 73, decision(failed = true), 0),
        )
    }

    @Test
    fun `清单更新失败但内容刷新成功时分开说`() {
        assertEquals(
            "资源清单更新失败：站点连不上 · 内容已是最新",
            RefreshNotice.compose("站点连不上", 73, decision(), 0),
        )
    }

    @Test
    fun `截断保留旧内容时单独提示`() {
        assertEquals(
            "网络返回不完整，已保留上次内容",
            RefreshNotice.compose(null, 73, decision(keptOld = true), 0),
        )
    }

    @Test
    fun `有新增时报新增数量`() {
        assertEquals(
            "资源清单已更新（73 个采集站）· 新增 3 部",
            RefreshNotice.compose(null, 73, decision(), 3),
        )
    }

    @Test
    fun `无新增时报内容已是最新`() {
        assertEquals(
            "资源清单已更新（73 个采集站）· 内容已是最新",
            RefreshNotice.compose(null, 73, decision(), 0),
        )
    }
}