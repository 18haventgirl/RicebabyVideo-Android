package com.ricebaby.video.data.feed

/** 下拉刷新结束后的一条提示：失败说原因，成功说清单与内容变化 */
object RefreshNotice {
    fun compose(reason: String?, sourceCount: Int, decision: ReplaceDecision, added: Int): String = when {
        decision.failed || decision.keptOld ->
            (if (reason != null) "刷新失败：$reason" else "网络返回不完整") + "，已保留上次内容"
        reason != null -> "资源清单更新失败：$reason · 内容已是最新"
        else -> "资源清单已更新（$sourceCount 个采集站）· " + if (added > 0) "新增 $added 部" else "内容已是最新"
    }
}