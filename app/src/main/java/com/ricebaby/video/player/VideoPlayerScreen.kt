package com.ricebaby.video.player

import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ricebaby.video.ui.theme.Dimens

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun VideoPlayerScreen(
    navController: NavHostController,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()

    var fullscreen by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val player = remember {
        viewModel.createPlayer().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(e: PlaybackException) {
                    error = "播放失败，正在尝试切换线路…"
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose {
            viewModel.updateProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
            viewModel.persistProgress()
            player.release()
        }
    }

    // 小窗只保留画面：控制层与信息区在 PiP 矩形里会挤成一团
    LaunchedEffect(activity) {
        while (true) {
            inPip = activity?.isInPictureInPictureMode == true
            delay(400)
        }
    }

    // 起播/切集：streamUrl 变化时替换媒体并续播
    LaunchedEffect(state.streamUrl, state.playbackToken) {
        val url = state.streamUrl ?: return@LaunchedEffect
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .apply { state.streamMimeType?.let { setMimeType(it) } }
                .build(),
        )
        player.prepare()
        if (state.resumePositionMs > 1000) {
            player.seekTo(state.resumePositionMs)
            toast = "已从上次位置继续播放"
        }
        player.playWhenReady = true
    }

    // 错误自动切线路
    LaunchedEffect(error) {
        if (error != null && !state.loading) {
            delay(1200)
            error = null
            val next = state.activeLine + 1
            if (next < state.lines.size) viewModel.switchLine(next)
        }
    }

    // 进度轮询 + 控制层自动隐藏
    LaunchedEffect(player) {
        var lastPosition = -1L
        var stalledAt = 0L
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (durationMs > 0) viewModel.updateProgress(positionMs, durationMs)
            // 代理线路可能整段卡死：位置长时间不推进就换线路，避免停在缓冲圈
            if (player.playWhenReady && positionMs == lastPosition) {
                if (stalledAt == 0L) stalledAt = System.currentTimeMillis()
                else if (System.currentTimeMillis() - stalledAt > 25_000) {
                    stalledAt = 0
                    error = "线路卡顿，正在切换…"
                }
            } else {
                stalledAt = 0
            }
            lastPosition = positionMs
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, speedMenuOpen) {
        if (controlsVisible && isPlaying && !speedMenuOpen) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(2200); toast = null }
    }

    // 全屏方向与系统栏
    DisposableEffect(fullscreen) {
        val window = activity?.window
        if (fullscreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    fun enterPip() {
        if (activity == null) return
        fullscreen = false
        if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            activity.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    var dragMode by remember { mutableIntStateOf(0) } // 0 none, 1 brightness(left), 2 volume(right)
    var dragStartY by remember { mutableFloatStateOf(0f) }

    val videoModifier = if (fullscreen) {
        Modifier.fillMaxSize().background(Color.Black)
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = videoModifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    if (inPip) {
                        if (player.isPlaying) player.pause() else player.play()
                    } else {
                        controlsVisible = !controlsVisible
                    }
                },
                onDoubleTap = { offset ->
                    val jump = if (offset.x < size.width / 2) -10_000L else 10_000L
                    player.seekTo((player.currentPosition + jump).coerceAtLeast(0))
                    controlsVisible = true
                },
            )
        }.pointerInput(Unit) {
            val audio = context.getSystemService(AudioManager::class.java)
            detectDragGestures(
                onDragStart = { offset ->
                    dragMode = if (offset.x < size.width / 2) 1 else 2
                    dragStartY = offset.y
                },
                onDragEnd = { dragMode = 0 },
                onDragCancel = { dragMode = 0 },
            ) { change, drag ->
                val delta = (dragStartY - change.position.y) / size.height
                dragStartY = change.position.y
                when (dragMode) {
                    1 -> {
                        val lp = activity?.window?.attributes
                        if (lp != null) {
                            lp.screenBrightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1f)
                            activity?.window?.attributes = lp
                        }
                    }
                    2 -> {
                        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                        val cur = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                        val next = (cur + delta * max).toInt().coerceIn(0, max)
                        audio?.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    }
                }
            }
        }) {
            key(state.playbackToken) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 中央播放/暂停大按钮
            if (!isPlaying && !buffering && !state.loading && controlsVisible && !inPip) {
                IconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    modifier = Modifier.align(Alignment.Center).size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            // 顶部栏
            if (controlsVisible && !inPip) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .systemBarsPadding()
                        .padding(horizontal = Dimens.sm, vertical = Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        viewModel.persistProgress()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Text(
                        buildString {
                            append(state.title)
                            if (!fullscreen && state.episodes.isNotEmpty()) {
                                append(" · ").append(state.episodes.getOrNull(state.selectedEpisode)?.name.orEmpty())
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "收藏",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        )
                    }
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "小窗", tint = Color.White)
                    }
                }
            }

            // 底部控制条
            if (controlsVisible && !inPip) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .systemBarsPadding()
                        .padding(horizontal = Dimens.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(positionMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                            onValueChange = { fraction ->
                                if (durationMs > 0) player.seekTo((fraction * durationMs).toLong())
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                            ),
                        )
                        Text(
                            if (durationMs > 0) fmt(durationMs) else "--:--",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "快退 10 秒", tint = Color.White)
                        }
                        IconButton(onClick = { player.seekTo(player.currentPosition + 10_000) }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "快进 10 秒", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { speedMenuOpen = true }) {
                                Icon(Icons.Outlined.Speed, contentDescription = "倍速", tint = Color.White)
                            }
                            if (speed != 1f) {
                                Text(
                                    "${speed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                            ) {
                                SPEEDS.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${s}x",
                                                color = if (s == speed) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.inverseOnSurface,
                                            )
                                        },
                                        onClick = {
                                            speed = s
                                            player.setPlaybackSpeed(s)
                                            speedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { fullscreen = !fullscreen }) {
                            Icon(
                                imageVector = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                contentDescription = if (fullscreen) "退出全屏" else "全屏",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            if (state.loading || buffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                    strokeWidth = 2.5.dp,
                )
            }
            state.error?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center).padding(Dimens.xl),
                )
            }
            toast?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = Dimens.xl)
                        .clip(RoundedCornerShape(Dimens.pillCorner))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                )
            }
        }

        // 竖屏信息区：线路 + 选集
        if (!fullscreen && !inPip) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                if (state.lines.size > 1) {
                    Text(
                        "线路",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Dimens.xl),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                        contentPadding = PaddingValues(horizontal = Dimens.xl),
                    ) {
                        items(state.lines.size) { index ->
                            LineChip(
                                text = state.lines[index].name,
                                selected = index == state.activeLine,
                            ) { viewModel.switchLine(index) }
                        }
                    }
                }
                if (state.episodes.isNotEmpty()) {
                    Text(
                        "选集",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Dimens.xl),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                        contentPadding = PaddingValues(horizontal = Dimens.xl),
                        modifier = Modifier.padding(bottom = Dimens.lg),
                    ) {
                        items(state.episodes.indices.toList()) { index ->
                            LineChip(
                                text = state.episodes[index].name,
                                selected = index == state.selectedEpisode,
                            ) { viewModel.selectEpisode(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.pillCorner))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun fmt(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
