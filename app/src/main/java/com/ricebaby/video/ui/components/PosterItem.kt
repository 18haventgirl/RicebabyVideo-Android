package com.ricebaby.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ricebaby.video.ui.theme.Dimens

/**
 * 统一海报条目：圆角海报 + 底部渐变角标 + 两行标题 + 一行副信息 + 可选收藏钮。
 * 首页 / 搜索结果 / 收藏页共用，保证视觉一致。
 */
@Composable
fun PosterItem(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    cornerBadge: String? = null,
    bottomBadge: String? = null,
    subtitle: String? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(Dimens.posterCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            bottomBadge?.takeIf { it.isNotBlank() }?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = Dimens.posterCorner, bottomEnd = Dimens.posterCorner))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Dimens.sm, vertical = 5.dp),
                    )
                }
            }
            cornerBadge?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.sm)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (onToggleFavorite != null && isFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(30.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "已收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f))
                            .padding(Dimens.xs),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
            )
        }
    }
}
