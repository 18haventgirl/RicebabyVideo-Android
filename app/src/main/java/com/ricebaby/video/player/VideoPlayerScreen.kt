package com.ricebaby.video.player

import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.VolumeUp
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
import androidx.compose.material3.LinearProgressIndicator
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
import kotlin.math.max
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
import com.ricebaby.video.ui.components.EpisodeChipRow
import com.ricebaby.video.ui.components.LineChipRow
import com.ricebaby.video.ui.theme.Dimens

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun VideoPlayerScreen(
    navController: NavHostController,
    onFullscreenChange: (Boolean) -> Unit = {},
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

    // 滑动手势：0 未识别 / 1 横向调进度 / 2 纵向调亮度音量
    var dragAxis by remember { mutableIntStateOf(0) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }
    var hudKind by remember { mutableIntStateOf(0) } // 1 亮度 2 音量 3 进度
    var hudValue by remember { mutableFloatStateOf(0f) }

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
            // 手势调过的亮度属于这个 Window，不还原会带着离开播放页
            activity?.window?.attributes?.let { lp ->
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity?.window?.attributes = lp
            }
            player.release()
        }
    }

    // 手势反馈浮层：抬手后短暂停留再消失
    LaunchedEffect(hudKind, hudValue, seekPreviewMs) {
        if (hudKind != 0) {
            delay(700)
            hudKind = 0
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
        var lastPersistAt = 0L
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            buffering = player.playbackState == Player.STATE_BUFFERING
            if (durationMs > 0) viewModel.updateProgress(positionMs, durationMs)
            // 只在离开播放页时落库的话，进程被杀（安装更新/系统回收）就整条历史都不剩
            val now = System.currentTimeMillis()
            if (durationMs > 0 && player.playWhenReady && now - lastPersistAt >= 10_000) {
                lastPersistAt = now
                viewModel.persistProgress()
            }
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

    // 全屏方向、刘海与系统栏：必须在退出全屏**和**离开播放页时还原，否则从全屏直接返回
    // 会把 Activity 永久留在横屏，上层页面全部跟着横过来
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val previousWindowBg = window?.decorView?.background
        if (fullscreen) {
            // 用 LANDSCAPE 而非 SENSOR_*/USER_*：关闭自动旋转时后两者会被系统降级为竖屏，
            // 点全屏没反应；LANDSCAPE 是硬性请求，任何旋转设置下都生效
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window?.let { w -> w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = cutoutMode(extend = true) } }
            // 状态栏/摄像头一侧露出的是**窗口背景**（浅色主题下就是一条白边），decor 视图盖不住它
            window?.decorView?.setBackgroundColor(android.graphics.Color.BLACK)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // 还原成 PORTRAIT 而不是 UNSPECIFIED：关闭自动旋转时后者会保持当前（横屏）不转回来
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window?.let { w -> w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = cutoutMode(extend = false) } }
            window?.decorView?.background = previousWindowBg
            if (previousWindowBg != null) window?.setBackgroundDrawable(previousWindowBg)
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 全屏时系统返回先回到竖屏继续播放，不直接退出播放页
    BackHandler(enabled = fullscreen) { fullscreen = false }

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

    val fullscreenModifier = if (fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    }.background(Color.Black)

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = fullscreenModifier.pointerInput(Unit) {
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
        }.pointerInput(durationMs) {
            val audio = context.getSystemService(AudioManager::class.java)
            detectDragGestures(
                onDragStart = { offset ->
                    dragAxis = 0
                    dragAccumX = 0f
                    dragAccumY = 0f
                    dragStartX = offset.x
                    dragStartY = offset.y
                    seekBaseMs = player.currentPosition
                    seekPreviewMs = seekBaseMs
                },
                onDragEnd = {
                    if (dragAxis == 1 && durationMs > 0) player.seekTo(seekPreviewMs)
                    dragAxis = 0
                },
                onDragCancel = { dragAxis = 0 },
            ) { change, drag ->
                dragAccumX += drag.x
                dragAccumY += drag.y
                if (dragAxis == 0) {
                    // 起手方向决定手势：横向拖=调进度，纵向拖=左半屏亮度 / 右半屏音量
                    if (max(kotlin.math.abs(dragAccumX), kotlin.math.abs(dragAccumY)) <
                        viewConfiguration.touchSlop * 2
                    ) return@detectDragGestures
                    dragAxis = if (kotlin.math.abs(dragAccumX) > kotlin.math.abs(dragAccumY)) 1 else 2
                }
                when (dragAxis) {
                    1 -> {
                        if (durationMs <= 0) return@detectDragGestures
                        seekPreviewMs = (seekBaseMs + dragAccumX / size.width * durationMs)
                            .toLong().coerceIn(0L, durationMs)
                        hudKind = 3
                    }
                    2 -> {
                        val step = (dragStartY - change.position.y) / size.height
                        dragStartY = change.position.y
                        if (dragStartX < size.width / 2) {
                            val lp = activity?.window?.attributes ?: return@detectDragGestures
                            val current = if (lp.screenBrightness >= 0f) lp.screenBrightness else 0.5f
                            val next = (current + step).coerceIn(0.01f, 1f)
                            lp.screenBrightness = next
                            activity?.window?.attributes = lp
                            hudKind = 1
                            hudValue = next
                        } else {
                            val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                            val cur = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val next = (cur + step * max).toInt().coerceIn(0, max)
                            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                            hudKind = 2
                            hudValue = if (max > 0) next.toFloat() / max else 0f
                        }
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
                            // AndroidView 内嵌 PlayerView 时媒体帧与合成层不同步会闪黑帧，
                            // media3 1.11 提供的 Compose 同步补丁专为此场景
                            setEnableComposeSurfaceSyncWorkaround(true)
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
                    modifier = Modifier.align(Alignment.Center).size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            // 手势反馈浮层：拖动时给出当前值，抬手 0.7 秒后消失
            if (hudKind != 0 && !inPip) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(Dimens.md))
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = Dimens.xl, vertical = Dimens.md),
                ) {
                    val (icon, label, ratio) = when (hudKind) {
                        1 -> Triple(
                            Icons.Outlined.BrightnessHigh,
                            "亮度 ${(hudValue * 100).toInt()}%",
                            hudValue,
                        )
                        2 -> Triple(
                            Icons.Outlined.VolumeUp,
                            "音量 ${(hudValue * 100).toInt()}%",
                            hudValue,
                        )
                        else -> Triple(
                            Icons.Filled.Forward10,
                            "${fmt(seekPreviewMs)} / ${fmt(durationMs)}",
                            if (durationMs > 0) seekPreviewMs.toFloat() / durationMs else 0f,
                        )
                    }
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(top = Dimens.xs),
                    )
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.padding(top = Dimens.sm).width(112.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                    )
                }
            }

            // 顶部栏：外层 Scaffold 已避让状态栏，这里再加 systemBarsPadding 会把整行压到画面中间
            if (controlsVisible && !inPip) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .then(if (fullscreen) Modifier.displayCutoutPadding() else Modifier)
                        .padding(start = Dimens.sm, end = Dimens.sm, top = Dimens.xs, bottom = Dimens.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(
                        onClick = {
                            if (fullscreen) {
                                // 全屏时左上角与返回键都表示"回到竖屏继续播放"，再按一次才离开播放页
                                fullscreen = false
                            } else {
                                viewModel.persistProgress()
                                navController.popBackStack()
                            }
                        },
                        contentDescription = if (fullscreen) "退出全屏" else "返回",
                        icon = if (fullscreen) Icons.Outlined.FullscreenExit
                        else Icons.AutoMirrored.Outlined.ArrowBack,
                    )
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
                    ControlButton(
                        onClick = viewModel::toggleFavorite,
                        contentDescription = "收藏",
                        icon = if (state.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        tint = if (state.isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                    )
                    ControlButton(
                        onClick = { enterPip() },
                        contentDescription = "小窗",
                        icon = Icons.Outlined.PictureInPictureAlt,
                    )
                }
            }

            // 底部控制条：同样不再叠加 systemBarsPadding，控制层贴着画面底边
            if (controlsVisible && !inPip) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .then(if (fullscreen) Modifier.displayCutoutPadding() else Modifier)
                        .padding(horizontal = Dimens.sm, vertical = Dimens.xs),
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
                        ControlButton(
                            onClick = { if (isPlaying) player.pause() else player.play() },
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        )
                        ControlButton(
                            onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                            contentDescription = "快退 10 秒",
                            icon = Icons.Filled.Replay10,
                        )
                        ControlButton(
                            onClick = { player.seekTo(player.currentPosition + 10_000) },
                            contentDescription = "快进 10 秒",
                            icon = Icons.Filled.Forward10,
                        )
                        Spacer(Modifier.weight(1f))
                        Box {
                            ControlButton(
                                onClick = { speedMenuOpen = true },
                                contentDescription = "倍速",
                                icon = Icons.Outlined.Speed,
                            )
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
                        ControlButton(
                            onClick = { fullscreen = !fullscreen },
                            contentDescription = if (fullscreen) "退出全屏" else "全屏",
                            icon = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        )
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

        // 竖屏信息区：与详情页同款的线路 / 选集呈现，换行完整展示且可纵向滚动
        if (!fullscreen && !inPip) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(top = Dimens.md, bottom = Dimens.xl),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                if (state.lines.size > 1) {
                    Text(
                        "线路",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Dimens.screen),
                    )
                    LineChipRow(
                        lines = state.lines.map { it.name },
                        selected = state.activeLine,
                        onSelect = { viewModel.switchLine(it) },
                        modifier = Modifier.padding(horizontal = Dimens.screen),
                    )
                }
                if (state.episodes.isNotEmpty()) {
                    Text(
                        "选集",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Dimens.screen),
                    )
                    EpisodeChipRow(
                        episodes = state.episodes.map { it.name },
                        selected = state.selectedEpisode,
                        onSelect = { viewModel.selectEpisode(it) },
                        modifier = Modifier.padding(horizontal = Dimens.screen),
                    )
                }
            }
        }
    }
}

/** 控制层统一规格：40dp 触摸区 + 20dp 图标，避免每个按钮各写一遍尺寸 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 全屏时允许画面延伸到刘海/挖孔区，退出后回到默认 */
private fun cutoutMode(extend: Boolean): Int =
    if (extend && android.os.Build.VERSION.SDK_INT >= 27)
        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    else
        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT

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
