package com.ricebaby.video.player

/**
 * 进度条几何：轨道两端各缩一个滑块半径，滑块行程与填充共用同一条内宽，
 * 球心在任何进度都精确压在白色填充末端（此前把像素当 dp 用，球会跑到填充前面去）。
 */
object SeekBarMath {
    fun innerWidth(widthPx: Float, thumbRadiusPx: Float): Float =
        (widthPx - 2 * thumbRadiusPx).coerceAtLeast(1f)

    fun fractionAt(xPx: Float, widthPx: Float, thumbRadiusPx: Float): Float =
        ((xPx - thumbRadiusPx) / innerWidth(widthPx, thumbRadiusPx)).coerceIn(0f, 1f)

    fun fillEndPx(fraction: Float, widthPx: Float, thumbRadiusPx: Float): Float =
        thumbRadiusPx + fraction.coerceIn(0f, 1f) * innerWidth(widthPx, thumbRadiusPx)
}
