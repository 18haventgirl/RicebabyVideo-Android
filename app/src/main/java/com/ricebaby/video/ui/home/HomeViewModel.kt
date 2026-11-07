package com.ricebaby.video.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.douban.DoubanItem
import com.ricebaby.video.data.douban.DoubanRepository
import com.ricebaby.video.data.douban.RecommendTagMapper
import com.ricebaby.video.data.feed.FeedTagRules
import com.ricebaby.video.data.feed.FeedTagStore
import com.ricebaby.video.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val RECOMMEND_LABEL = "为你推荐"

data class HomeUiState(
    val items: List<DoubanItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val serverTags: List<String> = emptyList(),
    val hiddenTags: List<String> = emptyList(),
    val customTags: List<String> = emptyList(),
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
    private val tagStore: FeedTagStore,
) : ViewModel() {

    private data class Internal(
        val items: List<DoubanItem> = emptyList(),
        val serverTags: List<String> = emptyList(),
        val activeTag: String = "",
        val isTv: Boolean = false,
        val pageStart: Int = 0,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val internal = MutableStateFlow(Internal())
    private var baseUrl: String? = null

    // 连点标签/类型时只认最后一次请求的结果，否则慢响应会盖掉新数据、翻页也会串到别的标签上
    private var fetchJob: Job? = null
    private var generation = 0
    private val tagsCache = mutableMapOf<String, List<String>>()

    val uiState: StateFlow<HomeUiState> = combine(
        historyDao.observeAll(),
        internal,
        tagStore.hiddenTags,
        tagStore.customTags,
    ) { history, t, hidden, custom ->
        val type = if (t.isTv) "tv" else "movie"
        val hiddenForType = hidden[type].orEmpty()
        val visible = mergeTags(
            serverTags = t.serverTags,
            recommend = recommendOf(history),
            hidden = hiddenForType,
            custom = custom[type].orEmpty(),
        )
        HomeUiState(
            items = t.items,
            tags = visible,
            serverTags = t.serverTags,
            hiddenTags = hiddenForType,
            customTags = custom[type].orEmpty(),
            activeTag = t.activeTag,
            isTv = t.isTv,
            loading = t.loading,
            error = t.error,
            canLoadMore = t.items.isNotEmpty() && t.error == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // 站点可能在解锁后才写入：只读一次 activeInstance 会让
        // 新用户配置完回到首页仍停在空态，所以跟随站点选择变化自动重试。
        viewModelScope.launch {
            settingsRepository.state
                .map { it.activeInstance?.baseUrl }
                .distinctUntilChanged()
                .collect { base ->
                    if (base != baseUrl) {
                        baseUrl = base
                        tagsCache.clear()
                    }
                    bootstrap(base)
                }
        }
    }

    private suspend fun bootstrap(base: String?) {
        cancelFetch()
        internal.value = Internal(isTv = internal.value.isTv, loading = true)
        if (base == null) {
            internal.value = internal.value.copy(loading = false, error = "站点还没准备好，请稍后重试")
            return
        }
        val type = if (internal.value.isTv) "tv" else "movie"
        // 先用上次成功的标签把位置钉住，网络回来再静默刷新
        val cached = tagStore.serverTags("$base|$type").first()
        if (cached.isNotEmpty() && internal.value.serverTags != cached) {
            internal.value = internal.value.copy(serverTags = cached)
        }
        val serverTags = loadTags(base, type)
        internal.value = internal.value.copy(serverTags = serverTags.orEmpty())
        val visible = currentVisibleTags()
        if (visible.isEmpty()) {
            internal.value = internal.value.copy(loading = false, error = tagError(serverTags))
            return
        }
        internal.value = internal.value.copy(activeTag = visible.first())
        startFetch(replace = true)
    }

    fun setTag(tag: String) {
        val t = internal.value
        if (tag == t.activeTag) return
        internal.value = t.copy(activeTag = tag, items = emptyList(), pageStart = 0, loading = true, error = null)
        startFetch(replace = true)
    }

    fun setType(tv: Boolean) {
        val t = internal.value
        if (tv == t.isTv) return
        val base = baseUrl ?: return
        cancelFetch()
        // 先切状态让骨架屏立刻出现；标签命中缓存时不用再等一次网络往返
        internal.value = t.copy(isTv = tv, items = emptyList(), pageStart = 0, loading = true, error = null)
        viewModelScope.launch {
            val serverTags = loadTags(base, if (tv) "tv" else "movie")
            if (internal.value.isTv != tv) return@launch
            internal.value = internal.value.copy(serverTags = serverTags.orEmpty())
            val visible = currentVisibleTags()
            if (visible.isEmpty()) {
                internal.value = internal.value.copy(loading = false, error = tagError(serverTags))
            } else {
                val keep = internal.value.activeTag.takeIf { it in visible }
                internal.value = internal.value.copy(activeTag = keep ?: visible.first())
                startFetch(replace = true)
            }
        }
    }

    /** 下拉刷新：标签重新拉一次（缓存也作废），内容按当前标签重新出第一页 */
    fun refresh() {
        val base = baseUrl ?: return
        val type = if (internal.value.isTv) "tv" else "movie"
        cancelFetch()
        tagsCache.remove("$base|$type")
        viewModelScope.launch {
            val serverTags = loadTags(base, type)
            if (!serverTags.isNullOrEmpty()) internal.value = internal.value.copy(serverTags = serverTags)
            val visible = currentVisibleTags()
            val active = if (internal.value.activeTag in visible) internal.value.activeTag else visible.firstOrNull().orEmpty()
            internal.value = internal.value.copy(
                items = emptyList(),
                activeTag = active,
                pageStart = 0,
                loading = true,
                error = null,
            )
            if (active.isNotEmpty()) startFetch(replace = true)
        }
    }

    fun retry() {
        val base = baseUrl ?: return
        if (internal.value.activeTag.isEmpty()) {
            viewModelScope.launch { bootstrap(base) }
        } else {
            internal.value = internal.value.copy(items = emptyList(), pageStart = 0, loading = true, error = null)
            startFetch(replace = true)
        }
    }

    fun loadMore() {
        val t = internal.value
        if (t.loading || t.error != null || t.activeTag.isEmpty() || t.items.isEmpty()) return
        startFetch(replace = false)
    }

    /** 标签管理：隐藏站点标签、增删自定义标签，改完把当前标签校正回可见集合 */
    fun setTagHidden(tag: String, hidden: Boolean) {
        val type = if (internal.value.isTv) "tv" else "movie"
        viewModelScope.launch {
            tagStore.setHidden(type, tag, hidden)
            alignActiveTag()
        }
    }

    fun addCustomTag(tag: String) {
        val type = if (internal.value.isTv) "tv" else "movie"
        viewModelScope.launch {
            if (tagStore.addCustom(type, tag)) alignActiveTag()
        }
    }

    fun removeCustomTag(tag: String) {
        val type = if (internal.value.isTv) "tv" else "movie"
        viewModelScope.launch {
            tagStore.removeCustom(type, tag)
            alignActiveTag()
        }
    }

    fun resetTags() {
        val type = if (internal.value.isTv) "tv" else "movie"
        viewModelScope.launch {
            tagStore.reset(type)
            alignActiveTag()
        }
    }

    private suspend fun alignActiveTag() {
        val visible = currentVisibleTags()
        if (visible.isNotEmpty() && internal.value.activeTag !in visible) setTag(visible.first())
    }

    private suspend fun currentVisibleTags(): List<String> {
        val type = if (internal.value.isTv) "tv" else "movie"
        val (hidden, custom) = combine(tagStore.hiddenTags, tagStore.customTags) { h, c -> h to c }.first()
        return mergeTags(
            serverTags = internal.value.serverTags,
            recommend = recommendOf(historyDao.observeAll().first()),
            hidden = hidden[type].orEmpty(),
            custom = custom[type].orEmpty(),
        )
    }

    private fun mergeTags(
        serverTags: List<String>,
        recommend: String?,
        hidden: List<String>,
        custom: List<String>,
    ): List<String> = FeedTagRules.merge(serverTags, recommend, hidden, custom, RECOMMEND_LABEL)

    private fun recommendOf(history: List<com.ricebaby.video.data.db.HistoryEntity>): String? =
        RecommendTagMapper.bestTag(
            history.mapNotNull { it.typeName?.takeIf { n -> n.isNotBlank() } }
                .groupingBy { it }.eachCount(),
        )

    private fun tagError(serverTags: List<String>?): String =
        if (serverTags == null) "站点标签加载失败，点击重试" else "站点没有返回标签"

    private suspend fun loadTags(base: String, type: String): List<String>? {
        val scope = "$base|$type"
        tagsCache[scope]?.let { return it }
        val fetched = douban.tags(base, type)
        // 失败与空结果都不落缓存，下次进来仍会重试
        if (!fetched.isNullOrEmpty()) {
            tagsCache[scope] = fetched
            tagStore.saveServerTags(scope, fetched)
        }
        return fetched?.takeIf { it.isNotEmpty() }
    }

    private fun cancelFetch() {
        generation++
        fetchJob?.cancel()
        fetchJob = null
    }

    private fun startFetch(replace: Boolean) {
        val base = baseUrl ?: return
        val snapshot = internal.value
        val tag = snapshot.activeTag
        if (tag.isEmpty()) return
        val type = if (snapshot.isTv) "tv" else "movie"
        val pageStart = if (replace) 0 else snapshot.pageStart + 20
        cancelFetch()
        val g = generation
        internal.value = snapshot.copy(loading = true, error = null)
        fetchJob = viewModelScope.launch {
            val target = if (tag == RECOMMEND_LABEL) {
                recommendOf(historyDao.observeAll().first()) ?: "热门"
            } else {
                tag
            }
            val fetched = douban.recommend(base, target, type, pageStart)
            if (g != generation) return@launch
            val merged = if (replace) fetched.orEmpty()
            else (internal.value.items + fetched.orEmpty()).distinctBy { it.id }
            internal.value = internal.value.copy(
                items = merged,
                pageStart = pageStart,
                loading = false,
                error = when {
                    fetched == null -> "加载失败，点击重试"
                    merged.isEmpty() -> "这个标签下暂时没有内容"
                    else -> null
                },
            )
        }
    }
}
