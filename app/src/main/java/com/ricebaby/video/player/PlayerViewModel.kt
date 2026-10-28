package com.ricebaby.video.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.ui.detail.DetailRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlayerState(
    val ready: Boolean = false,
    val error: String? = null,
    val url: String = "",
    val injectScript: String = "",
    val origin: String = "",
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    detailRegistry: DetailRegistry,
    private val settingsRepository: SettingsRepository,
    private val sourceDao: SourceDao,
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    private val key: String = savedStateHandle.get<String>("vid").orEmpty()
    private val episodeIndex: Int = savedStateHandle.get<String>("ep")?.toIntOrNull() ?: 0
    private val payload = detailRegistry.take(key)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = payload
            if (p == null) {
                _state.value = PlayerState(error = "播放信息已失效，请重新搜索")
                return@launch
            }
            val instance = settingsRepository.state.first().activeInstance
            if (instance == null) {
                _state.value = PlayerState(error = "先在设置里选择观影站点")
                return@launch
            }
            val sources = sourceDao.enabledOnce()
            if (sources.none { it.id == p.target.source }) {
                _state.value = PlayerState(error = "该资源已不在已同步列表中")
                return@launch
            }
            val history = historyDao.observeAll().first()
            val favorites = favoriteDao.observeAll().first()
            val gsKey = "app" + (100000..999999).random()
            val script = BridgeCodec.documentStartScript(
                settingsJson = BridgeCodec.settingsJson(sources),
                historyJson = BridgeCodec.historyJson(history),
                favoritesJson = BridgeCodec.favoritesJson(favorites),
                gsScript = BridgeCodec.gsScript(p.candidates, gsKey),
            )
            val url = buildString {
                append(instance.baseUrl)
                append("/player?id=").append(URLEncoder.encode(p.target.vodId, "UTF-8"))
                append("&source=").append(URLEncoder.encode(p.target.source, "UTF-8"))
                append("&title=").append(URLEncoder.encode(p.target.name, "UTF-8"))
                append("&episode=").append(episodeIndex)
                append("&gs=").append(gsKey)
            }
            _state.value = PlayerState(ready = true, url = url, injectScript = script, origin = instance.baseUrl)
        }
    }

    /** evaluateJavascript 回调值是 JSON 字面量字符串，先解包再解析 */
    fun syncSnapshot(raw: String?) {
        viewModelScope.launch {
            runCatching {
                val json = Json { ignoreUnknownKeys = true }
                val outer = json.parseToJsonElement(raw ?: return@launch).jsonPrimitive.contentOrNullSafe() ?: return@launch
                val obj = json.parseToJsonElement(outer).jsonObject
                val historyRaw = obj["h"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content
                val favoritesRaw = obj["f"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content

                val webHistory = BridgeCodec.parseWebHistory(historyRaw)
                if (webHistory.isNotEmpty()) {
                    BridgeCodec.mergeHistory(webHistory, historyDao.observeAll().first())
                        .forEach { historyDao.upsert(it) }
                }
                val webFavorites = BridgeCodec.parseWebFavorites(favoritesRaw)
                if (webFavorites.isNotEmpty()) {
                    BridgeCodec.mergeFavorites(webFavorites, favoriteDao.observeAll().first())
                        .forEach { favoriteDao.upsert(it) }
                }
            }
            .onFailure { }
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
