package com.ricebaby.video.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ricebaby.video.ui.theme.Dimens

/** M3 胶囊的固定高度，用来估算折叠后实际占了几行 */
private val ChipHeight = 32.dp

/**
 * 线路选择：详情页与播放页共用。默认只展示两行，超出部分由用户主动展开，
 * 因为采集站动辄十几条线路，全部铺开会把选集挤出首屏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChipRow(
    lines: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(lines.size) { mutableStateOf(false) }
    var visibleRows by rememberSaveable(lines.size) { mutableIntStateOf(1) }
    val gapPx = with(LocalDensity.current) { Dimens.xs.roundToPx() }
    val chipPx = with(LocalDensity.current) { ChipHeight.roundToPx() }

    Column(modifier = modifier) {
        FlowRow(
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            modifier = Modifier.onSizeChanged {
                visibleRows = ((it.height + gapPx) / (chipPx + gapPx)).coerceAtLeast(1)
            },
        ) {
            lines.forEachIndexed { index, name ->
                SelectChip(label = name, selected = index == selected, onClick = { onSelect(index) })
            }
        }
        if (visibleRows > 1 || expanded) {
            Text(
                if (expanded) "收起" else "展开全部 ${lines.size} 条线路",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = Dimens.xs)
                    .clip(RoundedCornerShape(Dimens.sm))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Dimens.xs, vertical = Dimens.xs),
            )
        }
    }
}

/** 选集：与线路同款的紧凑胶囊 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeChipRow(
    episodes: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        modifier = modifier,
    ) {
        episodes.forEachIndexed { index, name ->
            SelectChip(label = name, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

/**
 * 首页标签行：标签与「管理」排在同一条 FlowRow 里，
 * 否则管理按钮会被并排的 Column 顶到行首，跟标签不在同一水平线上。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipRow(
    tags: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        tags.forEach { tag ->
            SelectChip(label = tag, selected = tag == selected, onClick = { onSelect(tag) })
        }
        Box(
            modifier = Modifier
                .height(ChipHeight)
                .clip(RoundedCornerShape(Dimens.pillCorner))
                .clickable(onClick = onManage)
                .padding(horizontal = Dimens.sm),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "管理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
