package com.ricebaby.video.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.search.VideoItem
import com.ricebaby.video.ui.common.VideoCard
import com.ricebaby.video.ui.theme.Dimens

@Composable
fun WatchlistScreen(
    navController: NavHostController,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 外层 AppRoot 的 Scaffold 已避让系统栏，内层不能重复计算
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Text(
                "待看",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Dimens.screen, end = Dimens.screen, top = Dimens.md, bottom = Dimens.md),
            )
            if (uiState.favorites.isEmpty()) {
                EmptyHint("还没有收藏，搜索结果里点星标")
            } else {
                FavoritesTab(uiState.favorites, viewModel, navController)
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<FavoriteEntity>,
    viewModel: WatchlistViewModel,
    navController: androidx.navigation.NavHostController,
) {
    if (favorites.isEmpty()) {
        EmptyHint("还没有收藏，搜索结果里点星标")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.gridMin),
        horizontalArrangement = Arrangement.spacedBy(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.lg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.screen, end = Dimens.screen, bottom = Dimens.scrollEnd,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(favorites, key = { "${it.source}:${it.vodId}" }) { fav ->
            VideoCard(
                video = fav.toVideoItem(),
                isFavorite = true,
                onClick = {
                    val key = viewModel.handOffFavorite(fav)
                    navController.navigate("detail?vid=${android.net.Uri.encode(key)}")
                },
                onToggleFavorite = { viewModel.removeFavorite(fav.source, fav.vodId) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

private fun FavoriteEntity.toVideoItem() = VideoItem(
    vodIdElement = kotlinx.serialization.json.JsonPrimitive(vodId),
    name = name,
    pic = pic,
    typeName = typeName,
    remarks = remarks,
    year = year,
    source = source,
    sourceDisplayName = sourceName,
)

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
