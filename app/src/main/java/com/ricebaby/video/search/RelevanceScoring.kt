package com.ricebaby.video.search

/** 移植自 Web 端 lib/utils/search.ts（hasMinimumMatch / calculateRelevanceScore），规则与分值保持一致 */

fun hasMinimumMatch(title: String, query: String): Boolean {
    val normalizedTitle = title.lowercase()
    val normalizedQuery = query.lowercase().trim()
    for (i in 0..normalizedQuery.length - 2) {
        if (normalizedTitle.contains(normalizedQuery.substring(i, i + 2))) return true
    }
    return false
}

fun relevanceScore(item: VideoItem, query: String, currentYear: Int): Int {
    var score = 0
    val normalizedQuery = query.lowercase().trim()
    val normalizedTitle = item.name.lowercase()

    if (normalizedTitle == normalizedQuery) return 1000
    if (normalizedTitle.startsWith(normalizedQuery)) score += 500
    if (normalizedTitle.contains(normalizedQuery)) {
        score += 200
        score += maxOf(0, 50 - normalizedTitle.indexOf(normalizedQuery) * 2)
    }
    val queryWords = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (queryWords.size > 1 && queryWords.all { normalizedTitle.contains(it) }) score += 100
    queryWords.forEach { word ->
        if (word.length < 2) return@forEach
        if (normalizedTitle.contains(word)) {
            score += 30
            if (normalizedTitle.startsWith(word)) score += 20
        }
    }
    item.actor?.let { actor ->
        val a = actor.lowercase()
        if (a.contains(normalizedQuery)) score += 80
        queryWords.forEach { word -> if (word.length >= 2 && a.contains(word)) score += 15 }
    }
    item.director?.let { d ->
        val dir = d.lowercase()
        if (dir.contains(normalizedQuery)) score += 60
        queryWords.forEach { word -> if (word.length >= 2 && dir.contains(word)) score += 10 }
    }
    item.content?.let { if (it.lowercase().contains(normalizedQuery)) score += 20 }
    val itemYear = item.year?.toIntOrNull() ?: 0
    if (itemYear > 0) {
        when (currentYear - itemYear) {
            0 -> score += 15
            1 -> score += 10
            else -> if (currentYear - itemYear <= 3) score += 5
        }
    }
    if (item.name.length > 50) score -= 5
    item.remarks?.let { r ->
        val remarks = r.lowercase()
        if ("hd" in remarks || "1080" in remarks || "4k" in remarks) score += 5
        if ("完结" in remarks || "全集" in remarks) score += 3
    }
    return maxOf(0, score)
}
