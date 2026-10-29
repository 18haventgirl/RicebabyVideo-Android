package com.ricebaby.video.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ricebaby.video.data.iptv.M3uParser

@Composable
fun LiveScreen(
    navController: NavHostController,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                "直播",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("搜索频道") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.error != null && uiState.groups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (uiState.failedSources > 0) {
                        item {
                            Text(
                                "${uiState.failedSources} 个频道源暂不可达，已跳过",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    uiState.groups.forEach { (group, channels) ->
                        item(key = "g-$group") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleGroup(group) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (group in uiState.collapsed) {
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                                    } else {
                                        Icons.Outlined.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "$group（${channels.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                        if (group !in uiState.collapsed) {
                            items(channels.size, key = { "c-$group-$it" }) { index ->
                                ChannelRow(channels[index]) { channel ->
                                    navController.navigate(
                                        "liveplayer?url=${android.net.Uri.encode(channel.url)}&name=${android.net.Uri.encode(channel.name)}",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: M3uParser.IptvChannel,
    onClick: (M3uParser.IptvChannel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(channel) }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = channel.logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
