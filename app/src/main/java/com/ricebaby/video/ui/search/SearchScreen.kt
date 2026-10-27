package com.ricebaby.video.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricebaby.video.ui.common.VideoCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf(uiState.query) }
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索影片") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    input = ""
                                    viewModel.onQueryChange("")
                                },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            viewModel.submit(input)
                        },
                    ),
                )
                Button(
                    onClick = {
                        keyboard?.hide()
                        viewModel.submit(input)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = uiState.phase != SearchPhase.Streaming && input.isNotBlank(),
                ) { Text("搜索") }
            }

            if (uiState.history.isNotEmpty() && uiState.results.isEmpty() && uiState.phase == SearchPhase.Idle) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.history.forEach { item ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                input = item
                                viewModel.submit(item)
                            },
                            label = { Text(item) },
                        )
                    }
                    TextButton(onClick = viewModel::clearHistory) { Text("清空记录") }
                }
            }

            if (uiState.phase == SearchPhase.Streaming) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        uiState.progress?.let {
                            "已检索 ${it.first}/${it.second} 个资源 · 找到 ${uiState.results.size} 个结果"
                        } ?: "正在检索…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = viewModel::cancel) { Text("取消") }
                }
            }

            if (uiState.sourceCounts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.sourceCounts.take(8).forEach { badge ->
                        FilterChip(
                            selected = badge.sourceId in uiState.selectedSources,
                            onClick = { viewModel.toggleSourceFilter(badge.sourceId) },
                            label = { Text("${badge.displayName} ${badge.count}") },
                        )
                    }
                    uiState.typeCounts.take(8).forEach { (name, count) ->
                        FilterChip(
                            selected = name in uiState.selectedTypes,
                            onClick = { viewModel.toggleTypeFilter(name) },
                            label = { Text("$name $count") },
                        )
                    }
                    if (uiState.selectedSources.isNotEmpty() || uiState.selectedTypes.isNotEmpty()) {
                        AssistChip(onClick = viewModel::clearFilters, label = { Text("清除筛选") })
                    }
                }
            }

            if (uiState.results.isEmpty() && uiState.phase == SearchPhase.Idle && uiState.history.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "输入影片名称，同时搜索所有已同步的资源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.results, key = { it.key }) { scored ->
                    VideoCard(
                        video = scored.item,
                        isFavorite = scored.key in uiState.favoriteKeys,
                        onClick = { /* M2 接入详情页 */ },
                        onToggleFavorite = { viewModel.toggleFavorite(scored.item) },
                    )
                }
            }
        }
    }
}
