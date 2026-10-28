package com.ricebaby.video.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.iptv.IptvRepository
import com.ricebaby.video.data.iptv.M3uParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val groups: List<Pair<String, List<M3uParser.IptvChannel>>> = emptyList(),
    val collapsed: Set<String> = emptySet(),
    val query: String = "",
    val failedSources: Int = 0,
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: IptvRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var allChannels: List<M3uParser.IptvChannel> = emptyList()
    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val result = repository.loadChannels()
            result.fold(
                onSuccess = { load ->
                    allChannels = load.channels
                    val groups = buildGroups(load.channels, _uiState.value.query)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        groups = groups,
                        // 默认展开前 3 组
                        collapsed = groups.drop(3).map { it.first }.toSet(),
                        failedSources = load.failedSources.size,
                        error = if (load.channels.isEmpty()) "频道列表为空，检查站点 IPTV 源配置" else null,
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(loading = false, error = "频道加载失败：${it.message ?: "网络异常"}")
                },
            )
        }
    }

    fun toggleGroup(group: String) {
        val current = _uiState.value.collapsed
        _uiState.value = _uiState.value.copy(
            collapsed = if (group in current) current - group else current + group,
        )
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(
                groups = buildGroups(allChannels, value),
                collapsed = emptySet(),
            )
        }
    }

    private fun buildGroups(channels: List<M3uParser.IptvChannel>, query: String): List<Pair<String, List<M3uParser.IptvChannel>>> {
        val filtered = if (query.isBlank()) channels else channels.filter { it.name.contains(query, ignoreCase = true) }
        return filtered.groupBy { it.group }.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
