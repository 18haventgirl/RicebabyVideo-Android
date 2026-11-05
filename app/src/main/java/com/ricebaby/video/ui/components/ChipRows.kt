package com.ricebaby.video.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        modifier = modifier,
    ) {
        lines.forEachIndexed { index, name ->
            SelectChip(label = name, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

/** 选集：与线路、搜索筛选完全同款的胶囊 */
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
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        modifier = modifier,
    ) {
        episodes.forEachIndexed { index, name ->
            SelectChip(label = name, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}
