package com.ricebaby.video.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ricebaby.video.ui.common.VideoCard
import com.ricebaby.video.ui.components.PosterSkeleton
import com.ricebaby.video.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 单一数据源：字段内容即 VM 的 query，进程/页面重建后天然一致
    val input = uiState.query
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                "搜索",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Dimens.xl, end = Dimens.xl, top = Dimens.md, bottom = Dimens.md),
            )

            TextField(
                value = input,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.xl)
                    .clip(RoundedCornerShape(Dimens.lg)),
                placeholder = { Text("影片名 / 演员 / 导演") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
                                    .padding(end = 6.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                    modifier = Modifier.padding(horizontal = Dimens.xl),
                ) {
                    uiState.history.forEach { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.pillCorner))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    viewModel.onQueryChange(item)
                                    viewModel.submit(item)
                                }
                                .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                        ) {
                            Text(item, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (uiState.phase == SearchPhase.Streaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    modifier = Modifier.padding(horizontal = Dimens.xl),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        uiState.progress?.let {
                            "已检索 ${it.first}/${it.second} 个资源 · 找到 ${uiState.results.size} 个结果"
                        } ?: "正在检索…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "取消",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.sm))
                            .clickable(onClick = viewModel::cancel)
                            .padding(horizontal = Dimens.sm, vertical = 2.dp),
                    )
                }
                VGap(Dimens.md)
            }

            if (uiState.sourceCounts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                    modifier = Modifier.padding(horizontal = Dimens.xl),
                ) {
                    uiState.sourceCounts.take(8).forEach { badge ->
                        FilterPill(
                            text = "${badge.displayName} ${badge.count}",
                            selected = badge.sourceId in uiState.selectedSources,
                        ) { viewModel.toggleSourceFilter(badge.sourceId) }
                    }
                    uiState.typeCounts.take(8).forEach { (name, count) ->
                        FilterPill(text = "$name $count", selected = name in uiState.selectedTypes) {
                            viewModel.toggleTypeFilter(name)
                        }
                    }
                    if (uiState.selectedSources.isNotEmpty() || uiState.selectedTypes.isNotEmpty()) {
                        FilterPill(text = "清除筛选", selected = false, onClick = viewModel::clearFilters)
                    }
                }
                VGap(Dimens.md)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(116.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.lg),
                contentPadding = PaddingValues(start = Dimens.xl, end = Dimens.xl, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.results.isEmpty() && uiState.phase == SearchPhase.Streaming) {
                    items((1..6).toList(), key = { "sk-$it" }) {
                        PosterSkeleton(modifier = Modifier.animateItem())
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VGap(height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height))
}

@Composable
fun SectionLabel(text: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.xl, vertical = Dimens.sm),
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.pillCorner))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
