package com.ricebaby.video.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekBarMathTest {
    private val width = 1000f
    private val radius = 20f

    @Test
    fun `行程两端对应进度 0 与 1`() {
        assertEquals(0f, SeekBarMath.fractionAt(radius, width, radius), 1e-4f)
        assertEquals(1f, SeekBarMath.fractionAt(width - radius, width, radius), 1e-4f)
    }

    @Test
    fun `超出行程的触点被夹住`() {
        assertEquals(0f, SeekBarMath.fractionAt(-50f, width, radius), 1e-4f)
        assertEquals(1f, SeekBarMath.fractionAt(width + 50f, width, radius), 1e-4f)
    }

    @Test
    fun `球心与填充末端重合且往返一致`() {
        for (x in listOf(20f, 250f, 500f, 750f, 980f)) {
            val fraction = SeekBarMath.fractionAt(x, width, radius)
            assertEquals(x, SeekBarMath.fillEndPx(fraction, width, radius), 1e-3f)
        }
    }

    @Test
    fun `极窄轨道不除零且夹在边界`() {
        assertEquals(0f, SeekBarMath.fractionAt(5f, 10f, 20f), 1e-4f)
        assertEquals(21f, SeekBarMath.fillEndPx(1f, 10f, 20f), 1e-4f)
    }
}
