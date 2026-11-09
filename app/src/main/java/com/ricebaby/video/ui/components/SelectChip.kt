package com.ricebaby.video.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ricebaby.video.ui.theme.Dimens

/**
 * 全 App 统一的可选中胶囊：搜索结果筛选、线路、选集、首页标签共用同一尺寸、圆角与配色。
 * 官方 FilterChip 默认带描边和阴影，这里全部去掉，只留填充色区分选中态。
 *
 * 关掉 M3 的 48dp 触控盒扩展：它把 32dp 的胶囊撑成 50dp 一行，行间多出 16dp 透明死带，
 * 无论怎么调行间距都缩不掉。代价是点按高度回到 32dp，胶囊本身够宽，实测可点。
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(Dimens.pillCorner),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            elevation = null,
            border = null,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        )
    }
}
