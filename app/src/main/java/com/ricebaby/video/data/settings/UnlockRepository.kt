package com.ricebaby.video.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ricebaby.video.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unlockDataStore: DataStore<Preferences> by preferencesDataStore(name = "ricebaby_unlock")

/** 入口口令只需要验证一次，之后记住解锁状态。 */
@Singleton
class UnlockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val unlocked: Flow<Boolean> = context.unlockDataStore.data.map { it[KEY_UNLOCKED] == true }

    suspend fun submit(passphrase: String): Boolean {
        if (!AppUnlock.matches(passphrase, BuildConfig.UNLOCK_SHA256)) return false
        grant()
        return true
    }

    /** 自带站点地址的进阶用法不需要口令，直接把解锁状态打开。 */
    suspend fun grant() {
        context.unlockDataStore.edit { prefs -> prefs[KEY_UNLOCKED] = true }
    }

    companion object {
        private val KEY_UNLOCKED = booleanPreferencesKey("unlocked")
    }
}
