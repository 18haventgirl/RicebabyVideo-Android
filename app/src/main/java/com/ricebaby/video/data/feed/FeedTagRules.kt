package com.ricebaby.video.data.feed

/** 首页标签的可见集合 = 推荐位 + 站点标签 + 自定义标签，去掉被隐藏的，保持先到先得的顺序 */
object FeedTagRules {
    fun merge(
        serverTags: List<String>,
        recommend: String?,
        hidden: List<String>,
        custom: List<String>,
        recommendLabel: String,
    ): List<String> = buildList {
        if (recommend != null) add(recommendLabel)
        addAll(serverTags)
        addAll(custom)
    }.distinct().filterNot { it in hidden }
}
