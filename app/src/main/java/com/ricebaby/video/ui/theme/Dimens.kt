package com.ricebaby.video.ui.theme

import androidx.compose.ui.unit.dp

/** 统一 4/8pt 栅格：间距与圆角只从这里取值 */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 屏幕左右留白：每页只允许这一套，不再出现 12/16 混用 */
    val screen = 24.dp

    /** 列表/网格滚到底部时的留白（Scaffold 已避开导航栏，这里只是收尾） */
    val scrollEnd = 24.dp

    /** 带 FAB 的页面需要多留出一个悬浮按钮的高度 */
    val fabEnd = 88.dp

    val cardCorner = 16.dp
    val posterCorner = 12.dp
    val pillCorner = 999.dp

    /** 分段控件：外壳圆角 = 内衬 3dp + 选中块圆角 9dp */
    val segmentCorner = 12.dp
    val segmentInset = 3.dp
    val segmentItemCorner = 9.dp

    /** 海报网格最小列宽，三屏共用同一密度 */
    val gridMin = 116.dp
}
