package com.ricebaby.video.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.ricebaby.video.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class IptvPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val channelName: String = savedStateHandle.get<String>("name").orEmpty()
    private val channelUrl: String = savedStateHandle.get<String>("url").orEmpty()

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl

    init {
        viewModelScope.launch {
            val base = settingsRepository.state.first().activeInstance?.baseUrl
            _streamUrl.value = if (base == null || channelUrl.isBlank()) null else
                "$base/api/iptv/stream?url=" + URLEncoder.encode(channelUrl, "UTF-8")
        }
    }
}

@Composable
fun IptvPlayerScreen(
    navController: NavHostController,
    viewModel: IptvPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val streamUrl by viewModel.streamUrl.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember(streamUrl) {
        streamUrl?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(it))
                addListener(object : Player.Listener {
                    override fun onPlayerError(playerError: PlaybackException) {
                        error = "无法播放该频道，换一个试试"
                    }
                })
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        player?.let { p ->
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = true } },
                update = { it.player = p },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (streamUrl == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Text(
            viewModel.channelName,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        )
    }
}
