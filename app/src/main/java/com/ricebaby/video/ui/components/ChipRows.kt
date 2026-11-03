package com.ricebaby.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.ricebaby.video.ui.theme.Dimens

/** 线路选择：详情页与播放页共用，换行完整展示并带选中态 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChipRow(
    lines: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        modifier = modifier,
    ) {
        lines.forEachIndexed { index, name ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(name) },
            )
        }
    }
}

/** 选集：与线路同宽同圆角的一组图块，选中填充 primaryContainer */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeChipRow(
    episodes: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        modifier = modifier,
    ) {
        episodes.forEachIndexed { index, name ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.pillCorner))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
