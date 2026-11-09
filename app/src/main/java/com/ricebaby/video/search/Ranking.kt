package com.ricebaby.video.search

/**
 * 排序键：先相关性档、再源延迟、最后分数细排。
 * 延迟是源的属性不是影片的属性，跨档比较会让"名字只沾边的快源"压过"完全同名的慢源"，
 * 所以档与档之间永远只看相关性。
 */
data class RankKey(val tier: Int, val latency: Long, val score: Int) : Comparable<RankKey> {
    override fun compareTo(other: RankKey): Int =
        compareValuesBy(this, other, { it.tier }, { it.latency }, { -it.score })
}

object Ranking {
    /** 与 relevanceScore 的分值台阶对齐：精确 / 前缀 / 包含 / 其余 */
    fun tier(score: Int): Int = when {
        score >= 1000 -> 0
        score >= 500 -> 1
        score >= 200 -> 2
        else -> 3
    }

    fun key(score: Int, latency: Long?): RankKey = RankKey(tier(score), latency ?: Long.MAX_VALUE, score)
}