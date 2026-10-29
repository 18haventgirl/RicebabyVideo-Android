package com.ricebaby.video.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ricebaby.video.ui.components.PosterItem
import com.ricebaby.video.ui.components.PosterSkeleton
import com.ricebaby.video.ui.theme.Dimens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            uiState.items.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && uiState.canLoadMore) viewModel.loadMore()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                "米宝影视",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = Dimens.xl, end = Dimens.xl, top = Dimens.md, bottom = Dimens.md),
            )

            // 电影 / 剧集：Apple segmented 风格
            Row(
                modifier = Modifier
                    .padding(horizontal = Dimens.xl)
                    .clip(RoundedCornerShape(Dimens.md))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf(false to "电影", true to "剧集").forEach { (tv, label) ->
                    val selected = uiState.isTv == tv
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { viewModel.setType(tv) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (uiState.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                    modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.lg),
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
                columns = GridCells.Adaptive(116.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.lg),
                contentPadding = PaddingValues(start = Dimens.xl, end = Dimens.xl, bottom = 96.dp),
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
                        onClick = {
                            navController.navigate("search?q=${android.net.Uri.encode(item.title)}")
                        },
                    )
                }
            }
        }
    }
}
