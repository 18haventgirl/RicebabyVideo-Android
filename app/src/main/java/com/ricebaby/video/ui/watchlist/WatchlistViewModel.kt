package com.ricebaby.video.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.FavoriteEntity
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.HistoryEntity
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
}
