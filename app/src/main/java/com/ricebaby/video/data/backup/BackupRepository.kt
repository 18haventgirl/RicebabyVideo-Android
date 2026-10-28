package com.ricebaby.video.data.backup

import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.SearchHistoryDao
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class BackupRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sourceDao: SourceDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
) {
    suspend fun collectPayload(): BackupPayload {
        val settings = settingsRepository.state.first()
        return BackupPayload(
            instances = settings.instances,
            activeInstanceId = settings.activeInstanceId,
            passwords = settings.passwords,
            proxyHost = settings.proxyHost,
            proxyPort = settings.proxyPort,
            favorites = favoriteDao.observeAll().first(),
            history = historyDao.observeAll().first(),
            searchHistory = searchHistoryDao.observeRecent().first(),
        )
    }

    /** 恢复为整体替换语义；解析失败由调用方挡在门外 */
    suspend fun applyPayload(payload: BackupPayload): Int {
        settingsRepository.restoreInstances(payload.instances, payload.activeInstanceId)
        payload.passwords.forEach { (id, pw) -> settingsRepository.setPassword(id, pw) }
        settingsRepository.setProxy(payload.proxyHost, payload.proxyPort)
        favoriteDao.clearAll()
        favoriteDao.upsertAll(payload.favorites)
        historyDao.clearAll()
        historyDao.upsertAll(payload.history)
        searchHistoryDao.clear()
        searchHistoryDao.upsertAll(payload.searchHistory)
        return payload.favorites.size + payload.history.size
    }

    /** 视频源不随备份走（由订阅同步重建），导出前确保订阅已同步 */
    suspend fun sourcesSnapshot(): Int = sourceDao.enabledOnce().size
}
