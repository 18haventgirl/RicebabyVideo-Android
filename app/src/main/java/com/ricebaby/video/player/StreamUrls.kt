package com.ricebaby.video.player

import androidx.media3.common.MimeTypes
import java.net.URLEncoder

/** 与 Web 端"智能代理"一致：流地址走站点代理，绕开头端 DNS/UA/CORS 限制 */
fun proxyStreamUrl(base: String, raw: String): String =
    "$base/api/proxy?url=" + URLEncoder.encode(raw, "UTF-8")

/** 代理地址的路径不含扩展名，Media3 无法据此选择解封装器，需按原始流地址显式声明 */
fun streamMimeType(raw: String): String? = when {
    raw.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
    raw.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
    else -> null
}
