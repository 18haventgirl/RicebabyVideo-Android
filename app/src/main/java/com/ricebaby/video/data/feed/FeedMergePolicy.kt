package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem

data class ReplaceDecision(val items: List<DoubanItem>, val keptOld: Boolean, val failed: Boolean)

/** 第一页替换时的残缺判定：站点代理会在约 15KB 处截断 JSON，一次坏响应不该清空整屏 */
object FeedMergePolicy {
    fun decideReplace(old: List<DoubanItem>, fetched: List<DoubanItem>?): ReplaceDecision = when {
        fetched == null -> ReplaceDecision(old, keptOld = old.isNotEmpty(), failed = true)
        old.isNotEmpty() && fetched.size * 3 < old.size -> ReplaceDecision(old, keptOld = true, failed = false)
        else -> ReplaceDecision(fetched, keptOld = false, failed = false)
    }
}