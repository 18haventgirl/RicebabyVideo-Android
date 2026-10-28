package com.ricebaby.video.data.douban

/**
 * 观看历史 → 豆瓣推荐标签。豆瓣标签词表固定（热门/最新/豆瓣高分/冷门佳片/华语/欧美/韩国/日本），
 * 取与最常看类型语义最近的一项；不足 2 部不推荐。
 */
object RecommendTagMapper {
    fun bestTag(typeCounts: Map<String, Int>): String? {
        if (typeCounts.values.sum() < 2) return null
        val top = typeCounts.maxByOrNull { it.value }?.key.orEmpty()
        return when {
            "动漫" in top || "动画" in top || "日漫" in top -> "日本"
            "韩剧" in top || "韩国" in top -> "韩国"
            "美剧" in top || "英剧" in top || "欧美" in top -> "欧美"
            "国产" in top || "大陆" in top || "华语" in top || "综艺" in top || "港剧" in top -> "华语"
            else -> "热门"
        }
    }
}
