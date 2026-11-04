package com.ricebaby.video.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ricebaby.video.ui.theme.Dimens

/** iOS 风格分段控件：选中块按格宽滑动，首页与待看共用同一套圆角与留白 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            // 高度必须固定：否则 Column 会把剩余全高传进来，滑块会撑满整屏
            .height(Dimens.segmentHeight)
            .clip(RoundedCornerShape(Dimens.segmentCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val count = options.size.coerceAtLeast(1)
        // 滑块与文字格必须用同一套几何：都基于内缩后的可用宽度均分
        val slotWidth = (maxWidth - Dimens.segmentInset * 2) / count
        val slide by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = tween(220),
            label = "segmentSlide",
        )
        Box(
            modifier = Modifier
                .padding(Dimens.segmentInset)
                .offset(x = slotWidth * slide)
                .fillMaxHeight()
                .width(slotWidth)
                .clip(RoundedCornerShape(Dimens.segmentItemCorner))
                .background(MaterialTheme.colorScheme.surface),
        )
        Row(modifier = Modifier.padding(Dimens.segmentInset)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
