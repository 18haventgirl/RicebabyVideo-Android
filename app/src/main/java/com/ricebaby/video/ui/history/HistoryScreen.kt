package com.ricebaby.video.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.ui.theme.Dimens
import com.ricebaby.video.ui.watchlist.WatchlistViewModel

@Composable
fun HistoryScreen(
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
                "历史",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Dimens.screen, end = Dimens.screen, top = Dimens.md, bottom = Dimens.md),
            )
            if (uiState.history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有观看记录，播放后自动留在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                    contentPadding = PaddingValues(
                        start = Dimens.screen, end = Dimens.screen, bottom = Dimens.scrollEnd,
                    ),
                ) {
                    items(uiState.history, key = { "${it.source}:${it.vodId}" }) { entry ->
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
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.pic,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 44.dp, height = 62.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = Dimens.md)) {
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
