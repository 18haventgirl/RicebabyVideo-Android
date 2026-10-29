package com.ricebaby.video.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ricebaby.video.search.VideoItem
import com.ricebaby.video.ui.components.PosterItem

/** 搜索结果/收藏统一卡片：新设计语言下仅是 PosterItem 的数据适配层 */
@Composable
fun VideoCard(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosterItem(
        title = video.name,
        imageUrl = video.pic,
        modifier = modifier,
        bottomBadge = video.remarks?.takeIf { it.isNotBlank() },
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        subtitle = listOfNotNull(
            video.sourceDisplayName?.takeIf { it.isNotBlank() } ?: video.source.takeIf { it.isNotBlank() },
            video.year,
            video.typeName?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onClick = onClick,
    )
}
