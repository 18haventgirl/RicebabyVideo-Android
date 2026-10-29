package com.ricebaby.video.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.DetailClient
import com.ricebaby.video.net.DetailOutcome
import com.ricebaby.video.net.Episode
import com.ricebaby.video.ui.detail.DetailRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaybackState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val poster: String? = null,
    val episodes: List<Episode> = emptyList(),
    val selectedEpisode: Int = 0,
    val lines: List<PlaybackLine> = emptyList(),
    val activeLine: Int = 0,
    /** 交给播放器的直连流地址；null 表示尚未就绪 */
    val streamUrl: String? = null,
    /** 代理地址后缀不含类型信息，需按原始流地址显式告知 Media3 */
    val streamMimeType: String? = null,
    /** 起播前需要 seek 到的毫秒数（续播） */
    val resumePositionMs: Long = 0,
    val isFavorite: Boolean = false,
    val playbackToken: Long = 0,
)

data class PlaybackLine(val sourceId: String, val name: String, val vodId: String)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailRegistry: DetailRegistry,
    private val detailClient: DetailClient,
    private val sourceDao: SourceDao,
    private val settingsRepository: SettingsRepository,
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
    private val playerFactory: PlayerFactory,
) : ViewModel() {

    private val key: String = savedStateHandle.get<String>("vid").orEmpty()
    private val initialEpisode: Int = savedStateHandle.get<String>("ep")?.toIntOrNull() ?: 0
    private val payload = detailRegistry.take(key)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** ExoPlayer 侧周期性回写，退出/暂停时落库 */
    private var latestPositionMs = 0L
    private var latestDurationMs = 0L

    /** 载入线路时缓存，切集时复用，避免 UI 线程再取 Flow */
    private var activeBaseUrl: String? = null
    init {
        val p = payload
        if (p == null) {
            _state.value = _state.value.copy(loading = false, error = "播放信息已失效，请重新搜索")
        } else {
            _state.value = _state.value.copy(
                title = p.target.name,
                poster = p.target.pic,
                lines = p.candidates.map { PlaybackLine(it.source, it.sourceDisplayName ?: it.source, it.vodId) },
            )
            viewModelScope.launch {
                val favKey = "${p.target.source}:${p.target.vodId}"
                val favs = favoriteDao.observeAll().first()
                _state.value = _state.value.copy(isFavorite = favs.any { "${it.source}:${it.vodId}" == favKey })
            }
            loadLine(initialEpisodeLine(), initialEpisode)
        }
    }

    private fun initialEpisodeLine(): Int {
        val target = payload?.target ?: return 0
        return payload.candidates.indexOfFirst { it.source == target.source }.coerceAtLeast(0)
    }

    private fun loadLine(lineIndex: Int, episodeIndex: Int, attemptsLeft: Int = 6) {
        val p = payload ?: return
        val line = p.candidates.getOrNull(lineIndex) ?: return
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            activeLine = lineIndex,
            episodes = emptyList(),
            streamUrl = null,
            streamMimeType = null,
            selectedEpisode = episodeIndex,
        )
        viewModelScope.launch {
            val base = settingsRepository.state.first().activeInstance?.baseUrl
            activeBaseUrl = base
            val sourceEntity = sourceDao.enabledOnce().firstOrNull { it.id == line.source }
            if (base == null || sourceEntity == null) {
                _state.value = _state.value.copy(loading = false, error = "资源不可用，请检查站点与同步状态")
                return@launch
            }
            when (val outcome = detailClient.fetch(base, line.vodId, sourceEntity)) {
                is DetailOutcome.Success -> {
                    val detail = outcome.detail
                    val resume = resolveResume(detail.episodes.size, episodeIndex)
                    val raw = detail.episodes.getOrNull(resume.first)?.url
                    _state.value = _state.value.copy(
                        loading = false,
                        episodes = detail.episodes,
                        selectedEpisode = resume.first,
                        // 与 Web 端"智能代理"一致：流地址走站点 /api/proxy，绕开头端 DNS/UA/CORS 限制
                        streamUrl = raw?.let { proxyStreamUrl(base, it) },
                        streamMimeType = raw?.let { streamMimeType(it) },
                        resumePositionMs = resume.second,
                        playbackToken = System.currentTimeMillis(),
                        error = if (detail.episodes.isEmpty()) "该线路暂无可播剧集" else null,
                    )
                }
                is DetailOutcome.Failed -> {
                    // 采集站时常整条线路抽风，自动顺延到下一条
                    val next = lineIndex + 1
                    if (attemptsLeft > 1 && next < p.candidates.size) {
                        loadLine(next, episodeIndex, attemptsLeft - 1)
                    } else {
                        _state.value = _state.value.copy(loading = false, error = "「${line.name}」无法播放：${outcome.reason}")
                    }
                }
            }
        }
    }

    /** 续播：优先调用方指定集；否则取历史记录里的集与位置 */
    private suspend fun resolveResume(episodeCount: Int, requestedEpisode: Int): Pair<Int, Long> {
        val p = payload ?: return requestedEpisode.coerceIn(0, (episodeCount - 1).coerceAtLeast(0)) to 0L
        if (requestedEpisode > 0 || episodeCount == 0) {
            return requestedEpisode.coerceAtMost(episodeCount - 1) to 0L
        }
        val record = historyDao.observeAll().first().firstOrNull {
            it.source == p.target.source && it.vodId == p.target.vodId
        }
        val ep = (record?.episodeIndex ?: 0).coerceIn(0, episodeCount - 1)
        val pos = if (ep == (record?.episodeIndex ?: 0)) (record?.positionMs ?: 0L) else 0L
        return ep to pos
    }

    fun selectEpisode(index: Int) {
        val ep = _state.value.episodes.getOrNull(index)?.url ?: return
        val base = activeBaseUrl ?: return
        persistProgress()
        latestPositionMs = 0
        latestDurationMs = 0
        _state.value = _state.value.copy(
            selectedEpisode = index,
            streamUrl = proxyStreamUrl(base, ep),
            streamMimeType = streamMimeType(ep),
            resumePositionMs = 0,
            playbackToken = System.currentTimeMillis(),
        )
    }

    fun switchLine(index: Int) {
        if (index == _state.value.activeLine) return
        persistProgress()
        loadLine(index, _state.value.selectedEpisode)
    }

    fun createPlayer(): ExoPlayer = playerFactory.create()

    fun updateProgress(positionMs: Long, durationMs: Long) {
        latestPositionMs = positionMs
        latestDurationMs = durationMs
    }

    fun toggleFavorite() {
        val p = payload ?: return
        viewModelScope.launch {
            val target = p.candidates.getOrNull(_state.value.activeLine) ?: p.target
            val favKey = "${target.source}:${target.vodId}"
            val exists = favoriteDao.observeAll().first().any { "${it.source}:${it.vodId}" == favKey }
            if (exists) {
                favoriteDao.remove(target.source, target.vodId)
            } else {
                favoriteDao.upsert(
                    FavoriteEntity(
                        source = target.source,
                        vodId = target.vodId,
                        name = _state.value.title,
                        pic = _state.value.poster,
                        sourceName = _state.value.lines.getOrNull(_state.value.activeLine)?.name,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.value = _state.value.copy(isFavorite = !exists)
        }
    }

    fun persistProgress() {
        val p = payload ?: return
        if (latestDurationMs <= 0) return
        viewModelScope.launch {
            val s = _state.value
            // 记录在点片时那条源上，换线路播放也只留一条历史，续播才找得到
            historyDao.upsert(
                HistoryEntity(
                    source = p.target.source,
                    vodId = p.target.vodId,
                    name = s.title,
                    pic = s.poster,
                    episodeIndex = s.selectedEpisode,
                    episodeName = s.episodes.getOrNull(s.selectedEpisode)?.name,
                    positionMs = latestPositionMs,
                    durationMs = latestDurationMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onCleared() {
        persistProgress()
        super.onCleared()
    }
}
