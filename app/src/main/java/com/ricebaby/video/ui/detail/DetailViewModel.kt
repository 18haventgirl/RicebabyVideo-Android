package com.ricebaby.video.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.DetailClient
import com.ricebaby.video.net.DetailOutcome
import com.ricebaby.video.net.Episode
import com.ricebaby.video.net.VideoDetail
import com.ricebaby.video.search.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LineEntry(val sourceId: String, val name: String)

data class DetailUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: VideoDetail? = null,
    val lines: List<LineEntry> = emptyList(),
    val activeLine: Int = 0,
    val selectedEpisode: Int = 0,
    val title: String = "",
    val poster: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRegistry: DetailRegistry,
    private val detailClient: DetailClient,
    private val sourceDao: SourceDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val key: String = savedStateHandle.get<String>("vid").orEmpty()
    private var payload: DetailPayload? = detailRegistry.take(key)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        val p = payload
        if (p == null) {
            _uiState.value = _uiState.value.copy(error = "内容信息已失效，请重新搜索")
        } else {
            _uiState.value = _uiState.value.copy(
                title = p.target.name,
                poster = p.target.pic,
                lines = p.candidates.map { LineEntry(it.source, it.sourceDisplayName ?: it.source) },
            )
            loadLine(0)
        }
    }

    fun loadLine(index: Int, attemptsLeft: Int = 3) {
        val p = payload ?: return
        val target = p.candidates.getOrNull(index) ?: return
        _uiState.value = _uiState.value.copy(
            activeLine = index,
            loading = true,
            error = null,
            selectedEpisode = 0,
        )
        viewModelScope.launch {
            val instance = settingsRepository.state.first().activeInstance
            if (instance == null) {
                _uiState.value = _uiState.value.copy(loading = false, error = "先在设置里选择观影站点")
                return@launch
            }
            val sourceEntity = sourceDao.enabledOnce().firstOrNull { it.id == target.source }
            if (sourceEntity == null) {
                _uiState.value = _uiState.value.copy(loading = false, error = "该资源已不在已同步列表中")
                return@launch
            }
            when (val outcome = detailClient.fetch(instance.baseUrl, target.vodId, sourceEntity)) {
                is DetailOutcome.Success -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    detail = outcome.detail,
                    poster = outcome.detail.pic ?: _uiState.value.poster,
                    title = outcome.detail.name.ifBlank { _uiState.value.title },
                )

                is DetailOutcome.Failed -> {
                    val next = index + 1
                    if (attemptsLeft > 1 && next < p.candidates.size) {
                        // 采集站时常整条线路抽风，自动顺延比停在错误页更有用
                        loadLine(next, attemptsLeft - 1)
                    } else {
                        _uiState.value = _uiState.value.copy(loading = false, error = outcome.reason)
                    }
                }
            }
        }
    }

    fun selectEpisode(index: Int) {
        _uiState.value = _uiState.value.copy(selectedEpisode = index)
    }

    val currentTarget: VideoItem? get() = payload?.candidates?.getOrNull(_uiState.value.activeLine)

    val registryKey: String get() = key

    fun canPlay(): Boolean = payload != null && _uiState.value.detail != null

    fun episodeAt(index: Int): Episode? = _uiState.value.detail?.episodes?.getOrNull(index)
}
