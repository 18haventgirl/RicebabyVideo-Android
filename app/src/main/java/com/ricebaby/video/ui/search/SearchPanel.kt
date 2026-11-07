package com.ricebaby.video.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ricebaby.video.ui.common.VideoCard
import com.ricebaby.video.ui.components.EmptyState
import com.ricebaby.video.ui.components.PosterSkeleton
import com.ricebaby.video.ui.components.SelectChip
import com.ricebaby.video.ui.theme.Dimens

/** 准备与检索期间都算「在忙」，别在这一段露出空态 */
private val busyPhases = setOf(SearchPhase.Preparing, SearchPhase.Streaming)

/** 首页展开的搜索面板：输入、历史、检索进度与流式结果 */
@Composable
fun SearchPanel(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 单一数据源：字段内容即 VM 的 query，进程/页面重建后天然一致
    val input = uiState.query
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var filtersExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = input,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screen)
                .clip(RoundedCornerShape(Dimens.lg))
                .focusRequester(focusRequester),
            placeholder = { Text("影片名 / 演员 / 导演") },
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            trailingIcon = {
                if (input.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                viewModel.onQueryChange("")
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空")
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = Dimens.xs)
                                .clip(RoundedCornerShape(Dimens.pillCorner))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    keyboard?.hide()
                                    viewModel.submit(input)
                                }
                                .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                        ) {
                            Text("搜索", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.lg),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    viewModel.submit(input)
                },
            ),
        )

        VGap(Dimens.lg)

        if (uiState.history.isNotEmpty() && uiState.results.isEmpty() && uiState.phase == SearchPhase.Idle) {
            SectionLabel("搜索历史", action = "清空" to { viewModel.clearHistory() })
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                modifier = Modifier.padding(horizontal = Dimens.screen),
            ) {
                uiState.history.forEach { item ->
                    SelectChip(label = item, selected = false) {
                        viewModel.onQueryChange(item)
                        viewModel.submit(item)
                    }
                }
            }
        }

        if (uiState.phase in busyPhases) {
            val scanned = uiState.progress
            SearchProgressBar(
                fraction = scanned?.let { (done, total) -> if (total > 0) done.toFloat() / total else null },
                caption = when {
                    uiState.phase == SearchPhase.Preparing -> "正在准备资源…"
                    scanned != null -> "正在检索 ${scanned.first}/${scanned.second} 个资源"
                    else -> "正在检索…"
                },
                onCancel = viewModel::cancel,
            )
            VGap(Dimens.md)
        }

        if (uiState.phase !in busyPhases && (uiState.sourceCounts.isNotEmpty() || uiState.typeCounts.isNotEmpty())) {
            FlowRow(
                maxLines = if (filtersExpanded) Int.MAX_VALUE else 2,
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Dimens.screen),
            ) {
                // 不截断：与详情页「线路」同一批采集站，两处数量必须一致
                if (uiState.sourceCounts.isNotEmpty()) {
                    FilterCaption("线路")
                    uiState.sourceCounts.forEach { badge ->
                        FilterPill(
                            text = "${badge.displayName} ${badge.count}",
                            selected = badge.sourceId in uiState.selectedSources,
                        ) { viewModel.toggleSourceFilter(badge.sourceId) }
                    }
                }
                if (uiState.typeCounts.isNotEmpty()) {
                    FilterCaption("类型")
                    uiState.typeCounts.forEach { (name, count) ->
                        FilterPill(text = "$name $count", selected = name in uiState.selectedTypes) {
                            viewModel.toggleTypeFilter(name)
                        }
                    }
                }
                if (uiState.selectedSources.isNotEmpty() || uiState.selectedTypes.isNotEmpty()) {
                    FilterPill(text = "清除筛选", selected = false, onClick = viewModel::clearFilters)
                }
            }
            Text(
                if (filtersExpanded) "收起" else "展开全部筛选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Dimens.screen, end = Dimens.screen, top = Dimens.sm)
                    .clip(RoundedCornerShape(Dimens.sm))
                    .clickable { filtersExpanded = !filtersExpanded }
                    .padding(horizontal = Dimens.sm, vertical = 2.dp),
            )
            VGap(Dimens.md)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.gridMin),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
            contentPadding = PaddingValues(start = Dimens.screen, end = Dimens.screen, bottom = Dimens.scrollEnd),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (uiState.results.isEmpty() && uiState.phase in busyPhases) {
                items((1..6).toList(), key = { "sk-$it" }) {
                    PosterSkeleton(modifier = Modifier.animateItem())
                }
            }
            // 空态与错误态要留在页面上，不能只靠一闪而过的提示条
            if (uiState.results.isEmpty() && uiState.phase !in busyPhases && uiState.query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(uiState.message ?: "没有找到相关影片")
                }
            }
            items(uiState.results, key = { it.key }) { scored ->
                VideoCard(
                    video = scored.item,
                    isFavorite = scored.key in uiState.favoriteKeys,
                    onClick = {
                        val key = viewModel.handOffToDetail(scored)
                        navController.navigate("detail?vid=${android.net.Uri.encode(key)}")
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(scored.item) },
                )
            }
        }
    }
}

@Composable
private fun VGap(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height))
}

/**
 * 检索期间只说一件事：跑到哪了。
 * fraction 为空表示还数不出总进度（准备阶段、站点没报总数），用不确定态滚动。
 */
@Composable
private fun SearchProgressBar(fraction: Float?, caption: String, onCancel: () -> Unit) {
    val barModifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screen),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = barModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = barModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "取消",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.sm))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = Dimens.sm, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.sm))
                    .clickable(onClick = onClick)
                    .padding(horizontal = Dimens.sm, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    SelectChip(label = text, selected = selected, onClick = onClick)
}

/** 筛选行内的分组小标题，与胶囊同高，避免另起一行占空间 */
@Composable
private fun FilterCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = Dimens.xs),
    )
}
