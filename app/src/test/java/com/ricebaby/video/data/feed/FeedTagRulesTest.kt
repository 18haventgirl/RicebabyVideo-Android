package com.ricebaby.video.data.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTagRulesTest {

    private fun merge(
        server: List<String> = listOf("热门", "最新", "豆瓣高分"),
        recommend: String? = null,
        hidden: List<String> = emptyList(),
        custom: List<String> = emptyList(),
    ) = FeedTagRules.merge(server, recommend, hidden, custom, "为你推荐")

    @Test
    fun `有观看记录时推荐位排在最前`() {
        assertEquals(listOf("为你推荐", "热门", "最新", "豆瓣高分"), merge(recommend = "华语"))
    }

    @Test
    fun `没有记录时不加推荐位`() {
        assertEquals(listOf("热门", "最新", "豆瓣高分"), merge(recommend = null))
    }

    @Test
    fun `隐藏的站点标签不再出现`() {
        assertEquals(listOf("为你推荐", "最新"), merge(recommend = "华语", hidden = listOf("热门", "豆瓣高分")))
    }

    @Test
    fun `自定义标签追加在末尾且去重`() {
        assertEquals(
            listOf("热门", "最新", "豆瓣高分", "悬疑"),
            merge(custom = listOf("悬疑", "热门")),
        )
    }

    @Test
    fun `自定义标签被隐藏时同样消失`() {
        assertEquals(listOf("热门"), merge(server = listOf("热门"), custom = listOf("悬疑"), hidden = listOf("悬疑")))
    }
}
