package com.ricebaby.video.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.douban.DoubanItem
import com.ricebaby.video.data.douban.DoubanRepository
import com.ricebaby.video.data.douban.RecommendTagMapper
import com.ricebaby.video.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val RECOMMEND_LABEL = "为你推荐"

data class HomeUiState(
    val items: List<DoubanItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val activeTag: String = "",
    val isTv: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val douban: DoubanRepository,
    private val settingsRepository: SettingsRepository,
    private val historyDao: HistoryDao,
) : ViewModel() {

    private data class Internal(
        val items: List<DoubanItem> = emptyList(),
        val tags: List<String> = emptyList(),
        val activeTag: String = "",
        val isTv: Boolean = false,
        val pageStart: Int = 0,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val internal = MutableStateFlow(Internal())

    val uiState: StateFlow<HomeUiState> = combine(
        historyDao.observeAll(),
        internal,
    ) { history, t ->
        val recommendTag = RecommendTagMapper.bestTag(
            history.mapNotNull { it.typeName?.takeIf { n -> n.isNotBlank() } }
                .groupingBy { it }.eachCount(),
        )
        val tags = buildList {
            if (recommendTag != null) add(RECOMMEND_LABEL)
            addAll(t.tags)
        }
        HomeUiState(
            items = t.items,
            tags = tags,
            activeTag = t.activeTag,
            isTv = t.isTv,
            loading = t.loading,
            error = t.error,
            canLoadMore = t.items.isNotEmpty() && t.error == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            internal.value = internal.value.copy(loading = true, error = null)
            val base = settingsRepository.state.first().activeInstance?.baseUrl
            if (base == null) {
                internal.value = internal.value.copy(loading = false, error = "先在设置里添加并选择观影站点")
                return@launch
            }
            val tags = douban.tags(base, "movie")
            val recommendTag = RecommendTagMapper.bestTag(
                historyDao.observeAll().first()
                    .mapNotNull { it.typeName?.takeIf { n -> n.isNotBlank() } }
                    .groupingBy { it }.eachCount(),
            )
            val initial = if (recommendTag != null) RECOMMEND_LABEL else tags.firstOrNull().orEmpty()
            internal.value = internal.value.copy(tags = tags, activeTag = initial)
            if (initial.isNotEmpty()) fetch(base, if (initial == RECOMMEND_LABEL) recommendTag!! else initial, replace = true)
        }
    }

    private suspend fun loadRecommend(base: String) {
        val recommendTag = RecommendTagMapper.bestTag(
            historyDao.observeAll().first()
                .mapNotNull { it.typeName?.takeIf { n -> n.isNotBlank() } }
                .groupingBy { it }.eachCount(),
        ) ?: "热门"
        fetch(base, recommendTag, replace = true)
    }

    fun setTag(tag: String) {
        val t = internal.value
        if (tag == t.activeTag) return
        viewModelScope.launch {
            internal.value = t.copy(activeTag = tag, loading = true, error = null)
            val base = settingsRepository.state.first().activeInstance?.baseUrl ?: return@launch
            if (tag == RECOMMEND_LABEL) loadRecommend(base) else fetch(base, tag, replace = true)
        }
    }

    fun setType(tv: Boolean) {
        val t = internal.value
        if (tv == t.isTv) return
        viewModelScope.launch {
            internal.value = t.copy(isTv = tv, loading = true, items = emptyList(), pageStart = 0, error = null)
            val base = settingsRepository.state.first().activeInstance?.baseUrl ?: return@launch
            val type = if (tv) "tv" else "movie"
            val tags = douban.tags(base, type)
            internal.value = internal.value.copy(tags = tags)
            if (internal.value.activeTag == RECOMMEND_LABEL) loadRecommend(base)
            else fetch(base, internal.value.activeTag, replace = true)
        }
    }

    fun loadMore() {
        val t = internal.value
        if (t.loading || t.pageStart == 0 && t.items.isEmpty()) return
        viewModelScope.launch {
            val base = settingsRepository.state.first().activeInstance?.baseUrl ?: return@launch
            fetch(base, if (t.activeTag == RECOMMEND_LABEL) {
                RecommendTagMapper.bestTag(
                    historyDao.observeAll().first()
                        .mapNotNull { it.typeName }.groupingBy { it }.eachCount(),
                ) ?: "热门"
            } else t.activeTag, replace = false)
        }
    }

    private suspend fun fetch(base: String, tag: String, replace: Boolean) {
        val t = internal.value
        val pageStart = if (replace) 0 else t.pageStart + 20
        val type = if (t.isTv) "tv" else "movie"
        val items = douban.recommend(base, tag, type, pageStart)
        val merged = if (replace) items else (t.items + items).distinctBy { it.id }
        internal.value = internal.value.copy(
            items = merged,
            pageStart = pageStart,
            loading = false,
            error = if (merged.isEmpty()) "暂无内容，稍后再试" else null,
        )
    }
}
