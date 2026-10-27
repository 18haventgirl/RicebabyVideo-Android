package com.ricebaby.video.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.HistoryEntity
import com.ricebaby.video.search.VideoItem
import com.ricebaby.video.ui.detail.DetailPayload
import com.ricebaby.video.ui.detail.DetailRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val favorites: List<FavoriteEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val detailRegistry: DetailRegistry,
) : ViewModel() {

    val uiState: StateFlow<WatchlistUiState> =
        combine(favoriteDao.observeAll(), historyDao.observeAll()) { favs, hist ->
            WatchlistUiState(favs, hist)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())

    fun removeFavorite(source: String, vodId: String) = viewModelScope.launch {
        favoriteDao.remove(source, vodId)
    }

    fun removeHistory(source: String, vodId: String) = viewModelScope.launch {
        historyDao.remove(source, vodId)
    }

    fun handOffFavorite(fav: FavoriteEntity): String {
        val item = VideoItem(
            vodIdElement = kotlinx.serialization.json.JsonPrimitive(fav.vodId),
            name = fav.name,
            pic = fav.pic,
            typeName = fav.typeName,
            remarks = fav.remarks,
            year = fav.year,
            source = fav.source,
            sourceDisplayName = fav.sourceName,
        )
        val key = "${fav.source}:${fav.vodId}"
        detailRegistry.put(key, DetailPayload(item, listOf(item)))
        return key
    }

    fun handOffHistory(entry: HistoryEntity): String {
        val item = VideoItem(
            vodIdElement = kotlinx.serialization.json.JsonPrimitive(entry.vodId),
            name = entry.name,
            pic = entry.pic,
            typeName = entry.typeName,
            source = entry.source,
        )
        val key = "${entry.source}:${entry.vodId}"
        detailRegistry.put(key, DetailPayload(item, listOf(item)))
        return key
    }
}
