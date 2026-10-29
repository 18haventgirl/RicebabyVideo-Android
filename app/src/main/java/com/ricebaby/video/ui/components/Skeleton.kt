package com.ricebaby.video.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricebaby.video.ui.theme.Dimens

/** 骨架屏面：surfaceVariant 底 + 斜向流光 */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(Dimens.posterCorner)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(base, base.copy(alpha = 0.5f), base),
                start = Offset(shift, 0f),
                end = Offset(shift + 420f, 420f),
            ),
        )
}

/** 海报卡片骨架（2:3 图 + 一行文字条） */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shimmer(),
        )
        Box(
            Modifier
                .padding(top = Dimens.sm)
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .shimmer(RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Dimens.xxl),
        )
    }
}
