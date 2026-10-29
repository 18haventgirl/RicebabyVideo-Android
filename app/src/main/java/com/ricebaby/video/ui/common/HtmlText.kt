package com.ricebaby.video.ui.common

private val TAGS = Regex("<[^>]*>")
private val ENTITIES = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
private val BLANKS = Regex("[ \\t\\u3000]+")
private val NEWLINES = Regex("\n{3,}")

private val NAMED = mapOf(
    "nbsp" to ' ', "ensp" to ' ', "emsp" to ' ', "thinsp" to ' ',
    "amp" to '&', "lt" to '<', "gt" to '>', "quot" to '"', "apos" to '\'',
    "hellip" to '…', "middot" to '·', "mdash" to '—', "ndash" to '–',
    "ldquo" to '“', "rdquo" to '”', "lsquo" to '‘', "rsquo" to '’',
    "laquo" to '«', "raquo" to '»', "bull" to '•', "copy" to '©',
    "reg" to '®', "trade" to '™', "deg" to '°',
)

/** 采集站简介里混有 HTML 标签与实体，且常出现二次转义（&amp;nbsp;），需还原成可读文本 */
fun stripHtml(raw: String): String {
    var text = raw.replace(TAGS, "")
    repeat(2) { text = decodeEntities(text) }
    return text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(BLANKS, " ")
        .replace(NEWLINES, "\n\n")
        .trim()
}

private fun decodeEntities(text: String): String =
    ENTITIES.replace(text) { match ->
        val body = match.groupValues[1]
        val decoded: Char? = when {
            body.startsWith("&#x") || body.startsWith("&#X") ->
                body.drop(3).dropLast(1).toIntOrNull(16)?.toCharOrNull()
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.toCharOrNull()
            else -> NAMED[body.lowercase()]
        }
        decoded?.toString() ?: match.value
    }

private fun Int.toCharOrNull(): Char? = if (this in 1..0xFFFF) toChar() else null
