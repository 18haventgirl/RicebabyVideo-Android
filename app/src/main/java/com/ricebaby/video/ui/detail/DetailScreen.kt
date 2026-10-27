package com.ricebaby.video.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    navController: NavHostController,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    val snackbarScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = uiState.poster,
                    contentDescription = uiState.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(uiState.title, style = MaterialTheme.typography.titleLarge)
                    uiState.detail?.let { d ->
                        Text(
                            listOfNotNull(
                                d.year?.takeIf { it.isNotBlank() },
                                d.area?.takeIf { it.isNotBlank() },
                                d.typeName?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        d.remarks?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        d.director?.takeIf { it.isNotBlank() }?.let {
                            Text("导演：$it", style = MaterialTheme.typography.bodySmall)
                        }
                        d.actor?.takeIf { it.isNotBlank() }?.let {
                            Text("主演：$it", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            uiState.detail?.content?.takeIf { it.isNotBlank() }?.let { raw ->
                val plain = remember(raw) { Regex("<[^>]*>").replace(raw, "").trim() }
                if (plain.isNotBlank()) {
                    Text(
                        plain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text(if (expanded) "收起" else "展开") }
                }
            }

            if (uiState.lines.size > 1) {
                Text("线路", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.lines.forEachIndexed { index, line ->
                        FilterChip(
                            selected = index == uiState.activeLine,
                            onClick = { viewModel.loadLine(index) },
                            label = { Text(line.name) },
                        )
                    }
                }
            }

            when {
                uiState.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.error != null -> Text(
                    uiState.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {
                    val episodes = uiState.detail?.episodes.orEmpty()
                    if (episodes.isEmpty()) {
                        Text("该线路暂无可选剧集，试试切换线路", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("选集", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            episodes.forEachIndexed { index, episode ->
                                FilterChip(
                                    selected = index == uiState.selectedEpisode,
                                    onClick = { viewModel.selectEpisode(index) },
                                    label = { Text(episode.name) },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                snackbarScope.launch {
                                    if (viewModel.canPlay()) {
                                        navController.navigate("player?vid=${android.net.Uri.encode(viewModel.registryKey)}&ep=${uiState.selectedEpisode}")
                                    } else {
                                        snackbarHostState.showSnackbar("播放器将在下一里程碑接入")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            val ep = episodes.getOrNull(uiState.selectedEpisode)
                            Text(if (ep != null) "播放 · ${ep.name}" else "播放")
                        }
                    }
                }
            }
        }
    }
}
