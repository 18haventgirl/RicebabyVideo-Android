package com.ricebaby.video.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.search.VideoItem
import com.ricebaby.video.ui.common.VideoCard

@Composable
fun WatchlistScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("收藏") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("历史") })
            }
            when (selectedTab) {
                0 -> FavoritesTab(uiState.favorites, viewModel, navController)
                else -> HistoryTab(uiState.history, viewModel, navController)
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
        columns = GridCells.Adaptive(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
            )
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<HistoryEntity>,
    viewModel: WatchlistViewModel,
    navController: androidx.navigation.NavHostController,
) {
    if (history.isEmpty()) {
        EmptyHint("还没有观看记录，播放后自动留在这里")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(history, key = { "${it.source}:${it.vodId}" }) { entry ->
            HistoryRow(
                entry = entry,
                onClick = {
                    val key = viewModel.handOffHistory(entry)
                    navController.navigate("detail?vid=${android.net.Uri.encode(key)}")
                },
                onDelete = { viewModel.removeHistory(entry.source, entry.vodId) },
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.pic,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 44.dp, height = 62.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    if (entry.episodeIndex > 0) append("第${entry.episodeIndex + 1}集")
                    if (entry.durationMs > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("已看 ${(entry.positionMs * 100 / entry.durationMs).coerceAtMost(100)}%")
                    }
                }.ifBlank { "观看记录" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除记录")
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
