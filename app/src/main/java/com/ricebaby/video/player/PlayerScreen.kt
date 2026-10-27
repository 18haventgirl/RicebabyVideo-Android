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

private const val SYNC_JS =
    "JSON.stringify({h:localStorage.getItem('ricebabyvideo-history-store')," +
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

        if (state.ready) {
            AndroidWebView(
                state = state,
                onCreated = { webView = it; loading = true },
                onLoaded = { loading = false },
                onFullscreen = { view, callback -> fullscreen = view to callback },
                onExitFullscreen = { fullscreen = null },
            )
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
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // 播放器页内导航留在 WebView；跳出站点则拦截
                        return !request.url.toString().startsWith(state.origin)
                    }
                    override fun onPageFinished(view: WebView, url: String?) = onLoaded()
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
