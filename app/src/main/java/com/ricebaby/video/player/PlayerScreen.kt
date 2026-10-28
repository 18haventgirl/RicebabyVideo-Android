package com.ricebaby.video.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat

private const val SYNC_JS =    "JSON.stringify({h:localStorage.getItem('ricebabyvideo-history-store')," +
        "f:localStorage.getItem('ricebabyvideo-favorites-store')})"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    navController: NavHostController,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var crashed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun syncNow() {
        webView?.evaluateJavascript(SYNC_JS) { viewModel.syncSnapshot(it) }
    }

    fun leave() {
        syncNow()
        navController.popBackStack()
    }

    BackHandler(enabled = fullscreen != null || state.ready) {
        val fs = fullscreen
        if (fs != null) {
            fs.second.onCustomViewHidden()
            fullscreen = null
        } else {
            leave()
        }
    }

    // 退出前兜底回读（进程被杀等路径由下一次进入时 Web 端数据仍在，可接受）
    DisposableEffect(Unit) {
        onDispose {
            webView?.evaluateJavascript(SYNC_JS) { viewModel.syncSnapshot(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!state.ready && state.error == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        state.error?.let {
            Text(
                it,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
        }

        if (state.ready && !crashed) {
            AndroidWebView(
                state = state,
                onCreated = { webView = it; loading = true },
                onLoaded = { loading = false },
                onFullscreen = { view, callback -> fullscreen = view to callback },
                onExitFullscreen = { fullscreen = null },
                onRenderCrashed = {
                    syncNow()
                    webView?.destroy()
                    webView = null
                    crashed = true
                },
            )
        }
        if (crashed) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("播放器意外停止", color = Color.White)
                androidx.compose.material3.TextButton(onClick = {
                    crashed = false
                    loading = true
                }) { Text("重试", color = MaterialTheme.colorScheme.primary) }
            }
        }

        fullscreen?.let { (view, _) ->
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.ready && loading && fullscreen == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }

        if (fullscreen == null) {
            IconButton(
                onClick = { leave() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }

    // 播放期间周期回读进度
    LaunchedEffect(state.ready, webView) {
        val wv = webView ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000)
            wv.evaluateJavascript(SYNC_JS) { viewModel.syncSnapshot(it) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun AndroidWebView(
    state: PlayerState,
    onCreated: (WebView) -> Unit,
    onLoaded: () -> Unit,
    onFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onRenderCrashed: () -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            WebView(context).apply {
                if (android.os.Build.VERSION.SDK_INT >= 30 || context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // Cloudflare 会拦截带 "; wv" 标记的 WebView 默认 UA，返回反爬页而非站点
                settings.userAgentString = android.webkit.WebSettings.getDefaultUserAgent(context).replace("; wv", "")
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // 播放器页内导航留在 WebView；跳出站点则拦截
                        return !request.url.toString().startsWith(state.origin)
                    }
                    override fun onPageFinished(view: WebView, url: String?) = onLoaded()

                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                        // 渲染进程死亡必须自行销毁 WebView，否则宿主进程随之崩溃
                        onRenderCrashed()
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                        onFullscreen(view, callback)
                    }

                    override fun onHideCustomView() {
                        onExitFullscreen()
                    }
                }
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    state.injectScript,
                    setOf(state.origin),
                )
                onCreated(this)
            }
        },
        update = { wv ->
            if (wv.url.isNullOrBlank()) wv.loadUrl(state.url)
        },
        modifier = Modifier.fillMaxSize(),
    )
}
