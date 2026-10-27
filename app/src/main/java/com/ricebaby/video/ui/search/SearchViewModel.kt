package com.ricebaby.video.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.SearchHistoryDao
import com.ricebaby.video.data.db.SearchHistoryEntity
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.SiteClient
import com.ricebaby.video.search.SearchEvent
import com.ricebaby.video.search.SearchHandle
import com.ricebaby.video.search.SearchStreamClient
import com.ricebaby.video.search.VideoItem
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

enum class SearchPhase { Idle, Streaming, Done, Failed }

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
    private val settingsRepository: SettingsRepository,
    private val sourceDao: SourceDao,
    private val favoriteDao: FavoriteDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val streamClient: SearchStreamClient,
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
                .sortedByDescending { it.count },
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
            transient.value = Transient(query = query, phase = SearchPhase.Streaming)
            searchHistoryDao.upsert(SearchHistoryEntity(query, System.currentTimeMillis()))
            val instance = settingsRepository.state.first().activeInstance
            if (instance == null) {
                transient.value = transient.value.copy(phase = SearchPhase.Failed, message = "先在设置里添加并选择观影站点")
                return@launch
            }
            val sources = sourceDao.enabledOnce()
            if (sources.isEmpty()) {
                transient.value = transient.value.copy(phase = SearchPhase.Failed, message = "还没有可搜索的资源，请在设置页同步资源订阅")
                return@launch
            }
            handle = streamClient.start(instance.baseUrl, query, sources) { event -> onSearchEvent(event, query) }
        }
    }

    private fun onSearchEvent(event: SearchEvent, query: String) {
        val year = Year.now().value
        when (event) {
            is SearchEvent.Start -> Unit
            is SearchEvent.Videos -> {
                val additions = event.videos
                    .filter { hasMinimumMatch(it.name, query) }
                    .associateBy(
                        keySelector = { "${it.source}:${it.vodId}" },
                        valueTransform = { ScoredVideo(it, "${it.source}:${it.vodId}", relevanceScore(it, query, year)) },
                    )
                if (additions.isNotEmpty()) {
                    transient.value = transient.value.copy(items = transient.value.items + additions, message = null)
                }
            }
            is SearchEvent.Progress -> transient.value = transient.value.copy(progress = event.completed to event.total)
            is SearchEvent.Complete -> transient.value = transient.value.copy(
                phase = SearchPhase.Done,
                progress = null,
                message = if (transient.value.items.isEmpty()) "没有找到相关影片" else null,
            )
            is SearchEvent.Error -> transient.value = transient.value.copy(phase = SearchPhase.Failed, message = event.message)
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
}
