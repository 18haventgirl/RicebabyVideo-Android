package com.ricebaby.video.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ricebaby.video.ui.theme.Dimens

/**
 * 全 App 统一的可选中胶囊：搜索结果筛选、线路、选集、首页标签共用同一尺寸、圆角与配色。
 * 官方 FilterChip 默认带描边和阴影，这里全部去掉，只留填充色区分选中态。
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
