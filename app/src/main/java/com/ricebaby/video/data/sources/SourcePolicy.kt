package com.ricebaby.video.data.sources

import java.net.URI

/**
 * 订阅清单里混着成人采集站，它们的名字与域名都在厂商的色情 URL 库里：
 * 留在安装包或数据库里都会让整包被判涉黄。导入过滤与存量清理共用这一条判定。
 */
object SourcePolicy {
    private val nameKeywords = listOf(
        "黄色", "淫", "麻豆", "蜜桃", "精东", "天美传媒", "果冻传媒", "星空传媒",
        "杏吧", "草榴", "色花", "福利", "伦理", "成人", "约炮", "嫩模", "黄av", "av资源",
    )
    private val hostTokens = listOf(
        "hsck", "xrbsp", "kxgav", "pgxdy", "91md", "madou", "sehuatang", "91porn",
        "javbus", "javdb", "pornhub", "xvideos", "missav", "netflav", "jable",
        "hanime", "hentai", "sis001", "sexinsex", "caoliu", "porn", "jav", "sex", "xxx", "adult",
    )

    fun isAdult(name: String, baseUrl: String): Boolean {
        val lowered = name.lowercase()
        if (nameKeywords.any { lowered.contains(it) }) return true
        val host = runCatching { URI(baseUrl).host.orEmpty() }.getOrNull().orEmpty().lowercase()
        return hostTokens.any { host.contains(it) }
    }
}