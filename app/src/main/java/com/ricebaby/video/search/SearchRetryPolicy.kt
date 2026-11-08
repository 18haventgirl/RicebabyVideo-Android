package com.ricebaby.video.search

/** 搜索流中断且一无所获时重连一次；已有结果就保留，重连只会带来重复流与更长等待 */
object SearchRetryPolicy {
    fun shouldRetry(hasResults: Boolean, attempts: Int): Boolean = !hasResults && attempts < 1
}