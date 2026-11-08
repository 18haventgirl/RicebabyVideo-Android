package com.ricebaby.video.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.SearchHistoryDao
import com.ricebaby.video.data.db.SearchHistoryEntity
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.db.VideoSourceEntity
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.data.sources.BootstrapState
import com.ricebaby.video.data.sources.SourceBootstrap
import com.ricebaby.video.net.SiteClient
import com.ricebaby.video.search.SearchEvent
import com.ricebaby.video.search.SearchHandle
import com.ricebaby.video.search.SearchRetryPolicy
import com.ricebaby.video.search.SearchStreamClient
import com.ricebaby.video.search.VideoItem
import com.ricebaby.video.ui.detail.DetailPayload
import com.ricebaby.video.ui.detail.DetailRegistry
import com.ricebaby.video.search.hasMinimumMatch
import com.ricebaby.video.search.relevanceScore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchPhase { Idle, Preparing, Streaming, Done, Failed }

data class SourceBadge(val displayName: String, val sourceId: String, val count: Int)

data class ScoredVideo(val item: VideoItem, val key: String, val score: Int)

data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val results: List<ScoredVideo> = emptyList(),
    val sourceCounts: List<SourceBadge> = emptyList(),
    val typeCounts: List<Pair<String, Int>> = emptyList(),
    val selectedSources: Set<String> = emptySet(),
    val selectedTypes: Set<String> = emptySet(),
    val history: List<String> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val sourceDao: SourceDao,
    private val sourceBootstrap: SourceBootstrap,
    private val favoriteDao: FavoriteDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val streamClient: SearchStreamClient,
    private val detailRegistry: DetailRegistry,
) : ViewModel() {

    private data class Transient(
        val query: String = "",
        val phase: SearchPhase = SearchPhase.Idle,
        val items: Map<String, ScoredVideo> = emptyMap(),
        val selectedSources: Set<String> = emptySet(),
        val selectedTypes: Set<String> = emptySet(),
        val progress: Pair<Int, Int>? = null,
        val message: String? = null,
    )

    private val transient = MutableStateFlow(Transient())
    private var handle: SearchHandle? = null
    private var searchAttempts = 0
    private var lastBaseUrl = ""
    private var lastQuery = ""
    private var lastSources: List<VideoSourceEntity> = emptyList()

    init {
        val prefill = savedStateHandle.get<String>("q")
        if (!prefill.isNullOrBlank()) {
            onQueryChange(prefill)
            submit(prefill)
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        searchHistoryDao.observeRecent(),
        favoriteDao.observeAll(),
        transient,
    ) { history, favorites, t ->
        val visible = t.items.values
            .filter { t.selectedSources.isEmpty() || it.item.source in t.selectedSources }
            .filter { t.selectedTypes.isEmpty() || (it.item.typeName ?: "未分类") in t.selectedTypes }
            .sortedWith(compareByDescending<ScoredVideo> { it.score }.thenBy { it.item.latency ?: Long.MAX_VALUE })
        SearchUiState(
            query = t.query,
            phase = t.phase,
            results = visible,
            sourceCounts = t.items.values
                .groupingBy { it.item.source }
                .eachCount()
                .map { (sourceId, count) ->
                    SourceBadge(
                        displayName = t.items.values.first { it.item.source == sourceId }.item.sourceDisplayName ?: sourceId,
                        sourceId = sourceId,
                        count = count,
                    )
                }
                // 与详情页线路同一排序规则，两处看到的顺序与数量才一致
                .sortedBy { it.displayName },
            typeCounts = t.items.values
                .groupingBy { it.item.typeName ?: "未分类" }
                .eachCount()
                .toList()
                .sortedByDescending { it.second },
            selectedSources = t.selectedSources,
            selectedTypes = t.selectedTypes,
            history = history.map { it.query },
            favoriteKeys = favorites.map { "${it.source}:${it.vodId}" }.toSet(),
            progress = t.progress,
            message = t.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        transient.value = transient.value.copy(query = value)
    }

    fun submit(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        handle?.cancel()
        viewModelScope.launch {
            transient.value = Transient(query = query, phase = SearchPhase.Preparing)
            searchHistoryDao.upsert(SearchHistoryEntity(query, System.currentTimeMillis()))
            val instance = settingsRepository.state.first().activeInstance
            if (instance == null) {
                transient.value = transient.value.copy(phase = SearchPhase.Failed, message = "站点还没准备好，请稍后重试")
                return@launch
            }
            val sources = prepareSources()
            if (sources.isEmpty()) return@launch
            searchAttempts = 0
            lastBaseUrl = instance.baseUrl
            lastQuery = query
            lastSources = sources
            handle = streamClient.start(instance.baseUrl, query, sources) { event -> onSearchEvent(event, query) }
        }
    }

    /**
     * 资源为空时当场同步一次，成功就继续搜。
     * 返回空列表表示已经把失败文案写进状态了。
     */
    private suspend fun prepareSources(): List<VideoSourceEntity> {
        val existing = sourceDao.enabledOnce()
        if (existing.isNotEmpty()) return existing
        val boot = sourceBootstrap.ensureReady()
        if (boot.state == BootstrapState.Ready) return sourceDao.enabledOnce()
        transient.value = transient.value.copy(
            phase = SearchPhase.Failed,
            message = when (boot.state) {
                BootstrapState.NoSubscription -> "这个站点没有配置资源订阅"
                BootstrapState.Unreachable -> boot.detail ?: "站点连不上，请检查网络后重试"
                else -> "资源还没准备好，请检查网络后重试"
            },
        )
        return emptyList()
    }

    private fun onSearchEvent(event: SearchEvent, query: String) {
        val year = Year.now().value
        when (event) {
            is SearchEvent.Start -> transient.value = transient.value.copy(
                phase = SearchPhase.Streaming,
                progress = 0 to event.totalSources,
            )
            is SearchEvent.Videos -> {
                val additions = event.videos
                    .filter { hasMinimumMatch(it.name, query) }
                    .associateBy(
                        keySelector = { "${it.source}:${it.vodId}" },
                        valueTransform = { ScoredVideo(it, "${it.source}:${it.vodId}", relevanceScore(it, query, year)) },
                    )
                if (additions.isNotEmpty()) {
                    transient.value = transient.value.copy(
                        items = transient.value.items + additions,
                        phase = SearchPhase.Streaming,
                        message = null,
                    )
                }
            }
            is SearchEvent.Progress -> transient.value = transient.value.copy(
                phase = SearchPhase.Streaming,
                progress = event.completed to event.total,
            )
            is SearchEvent.Complete -> transient.value = transient.value.copy(
                phase = SearchPhase.Done,
                progress = null,
                message = if (transient.value.items.isEmpty()) "没有找到相关影片" else null,
            )
            is SearchEvent.Error -> {
                val hasResults = transient.value.items.isNotEmpty()
                if (SearchRetryPolicy.shouldRetry(hasResults, searchAttempts)) {
                    searchAttempts++
                    handle = streamClient.start(lastBaseUrl, lastQuery, lastSources) { onSearchEvent(it, lastQuery) }
                } else {
                    transient.value = transient.value.copy(
                        phase = if (hasResults) SearchPhase.Done else SearchPhase.Failed,
                        message = if (hasResults) "搜索连接中断，已保留现有结果" else event.message,
                    )
                }
            }
        }
    }

    fun cancel() {
        handle?.cancel()
        handle = null
        transient.value = transient.value.copy(phase = SearchPhase.Done, progress = null)
    }

    fun toggleSourceFilter(sourceId: String) {
        val current = transient.value.selectedSources
        transient.value = transient.value.copy(
            selectedSources = if (sourceId in current) current - sourceId else current + sourceId,
        )
    }

    fun toggleTypeFilter(type: String) {
        val current = transient.value.selectedTypes
        transient.value = transient.value.copy(
            selectedTypes = if (type in current) current - type else current + type,
        )
    }

    fun clearFilters() {
        transient.value = transient.value.copy(selectedSources = emptySet(), selectedTypes = emptySet())
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            val key = "${video.source}:${video.vodId}"
            if (key in uiState.value.favoriteKeys) {
                favoriteDao.remove(video.source, video.vodId)
            } else {
                favoriteDao.upsert(
                    FavoriteEntity(
                        source = video.source,
                        vodId = video.vodId,
                        name = video.name,
                        pic = video.pic,
                        typeName = video.typeName,
                        remarks = video.remarks,
                        year = video.year,
                        sourceName = video.sourceDisplayName ?: video.source,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun removeHistory(query: String) = viewModelScope.launch { searchHistoryDao.remove(query) }

    fun clearHistory() = viewModelScope.launch { searchHistoryDao.clear() }

    /** 把同名结果打包交给详情页，返回导航用的 key */
    fun handOffToDetail(scored: ScoredVideo): String {
        val candidates = transient.value.items.values
            .map { it.item }
            .filter { it.name == scored.item.name }
            .distinctBy { it.source }
            .sortedBy { it.sourceDisplayName ?: it.source }
        detailRegistry.put(scored.key, DetailPayload(scored.item, candidates))
        return scored.key
    }
}
