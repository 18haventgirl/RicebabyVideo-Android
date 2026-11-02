package com.ricebaby.video.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.ricebaby.video.ui.theme.Dimens

/** iOS 风格分段控件：选中块滑动切换，首页与待看共用同一套圆角与留白 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.segmentCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val count = options.size.coerceAtLeast(1)
        val slide by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = tween(220),
            label = "segmentSlide",
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = maxWidth / count * slide)
                .padding(Dimens.segmentInset)
                .clip(RoundedCornerShape(Dimens.segmentItemCorner))
                .background(MaterialTheme.colorScheme.surface),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = Dimens.sm),
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
