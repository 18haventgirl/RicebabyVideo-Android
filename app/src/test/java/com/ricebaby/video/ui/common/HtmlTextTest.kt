package com.ricebaby.video.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test
    fun removesTagsAndDecodesEntities() {
        val raw = "<p>少年&nbsp;剑客&mdash;&mdash;《杯雪》</p>&#13;&#10;改编自小说"
        assertEquals("少年 剑客——《杯雪》\n改编自小说", stripHtml(raw))
    }

    @Test
    fun decodesDoubleEscapedEntities() {
        assertEquals("a b", stripHtml("a&amp;nbsp;b"))
    }

    @Test
    fun keepsUnknownEntityIntact() {
        assertEquals("价格 &xyz; 元", stripHtml("价格 &xyz; 元"))
    }

    @Test
    fun collapsesBlankLines() {
        assertEquals("第一段\n\n第二段", stripHtml("第一段\n\n\n\n\n第二段"))
    }
}
