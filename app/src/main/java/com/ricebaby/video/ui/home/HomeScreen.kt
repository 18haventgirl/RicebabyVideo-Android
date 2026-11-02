package com.ricebaby.video.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ricebaby.video.ui.components.PosterItem
import com.ricebaby.video.ui.components.PosterSkeleton
import com.ricebaby.video.ui.components.SegmentedToggle
import com.ricebaby.video.ui.search.SearchPanel
import com.ricebaby.video.ui.search.SearchViewModel
import com.ricebaby.video.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searching by rememberSaveable { mutableStateOf(false) }

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            uiState.items.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && uiState.canLoadMore) viewModel.loadMore()
    }

    BackHandler(enabled = searching) { searching = false }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (searching) {
                SearchPanel(
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    onBack = { searching = false },
                    viewModel = searchViewModel,
                )
            } else {
                BrowseFeed(
                    uiState = uiState,
                    viewModel = viewModel,
                    gridState = gridState,
                    onSearchEntryClick = { searching = true },
                    onPickTitle = { title ->
                        searchViewModel.onQueryChange(title)
                        searchViewModel.submit(title)
                        searching = true
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseFeed(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    gridState: LazyGridState,
    onSearchEntryClick: () -> Unit,
    onPickTitle: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "米宝影视",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = Dimens.screen, end = Dimens.screen, top = Dimens.md),
        )

        SearchEntry(
            onClick = onSearchEntryClick,
            modifier = Modifier.padding(start = Dimens.screen, end = Dimens.screen, top = Dimens.md, bottom = Dimens.lg),
        )

        // 电影 / 剧集
        SegmentedToggle(
            options = listOf("电影", "剧集"),
            selectedIndex = if (uiState.isTv) 1 else 0,
            onSelect = { viewModel.setType(it == 1) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screen),
        )

        if (uiState.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                modifier = Modifier.padding(horizontal = Dimens.screen, vertical = Dimens.lg),
            ) {
                uiState.tags.forEach { tag ->
                    val selected = tag == uiState.activeTag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.pillCorner))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                            .clickable { viewModel.setTag(tag) }
                            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(Dimens.gridMin),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.lg),
            contentPadding = PaddingValues(start = Dimens.screen, end = Dimens.screen, bottom = Dimens.scrollEnd),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (uiState.items.isEmpty() && uiState.loading) {
                items((1..8).toList(), key = { "sk-$it" }) {
                    PosterSkeleton(modifier = Modifier.animateItem())
                }
            }
            if (uiState.items.isEmpty() && !uiState.loading && uiState.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.xxl),
                    )
                }
            }
            items(uiState.items, key = { it.id }) { item ->
                PosterItem(
                    title = item.title,
                    imageUrl = item.cover,
                    modifier = Modifier.animateItem(),
                    cornerBadge = item.rate.takeIf { it.isNotBlank() },
                    onClick = { onPickTitle(item.title) },
                )
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.md),
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "影片名 / 演员 / 导演",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
