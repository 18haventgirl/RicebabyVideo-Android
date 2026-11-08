package com.ricebaby.video.data.sources

/** 清单保鲜：超过一天没重同步就在冷启动时补一次，用户无感 */
object SourceFreshness {
    fun isStale(lastSyncedAt: Long?, nowMillis: Long, maxAgeMillis: Long = 24L * 3600 * 1000): Boolean =
        lastSyncedAt == null || nowMillis - lastSyncedAt > maxAgeMillis
}