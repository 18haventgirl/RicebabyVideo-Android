package com.ricebaby.video.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ricebaby.video.data.model.SiteInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ricebaby_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val state: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            instances = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty()),
            activeInstanceId = prefs[KEY_ACTIVE],
            passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()),
            proxyHost = prefs[KEY_PROXY_HOST].orEmpty(),
            proxyPort = prefs[KEY_PROXY_PORT] ?: 0,
        )
    }

    suspend fun addInstance(name: String, baseUrlRaw: String): String? {
        val normalized = SiteInstanceCodec.normalizeBaseUrl(baseUrlRaw) ?: return null
        val id = SiteInstanceCodec.newId()
        val instance = SiteInstance(id, name.trim(), normalized)
        context.settingsDataStore.edit { prefs ->
            val current = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty())
            prefs[KEY_INSTANCES] = SiteInstanceCodec.encodeInstances(current + instance)
            if (prefs[KEY_ACTIVE] == null) prefs[KEY_ACTIVE] = id
        }
        return id
    }

    suspend fun removeInstance(id: String) {
        context.settingsDataStore.edit { prefs ->
            val remaining = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty())
                .filterNot { it.id == id }
            prefs[KEY_INSTANCES] = SiteInstanceCodec.encodeInstances(remaining)
            val passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()).toMutableMap()
            passwords.remove(id)
            prefs[KEY_PASSWORDS] = SiteInstanceCodec.encodePasswords(passwords)
            if (prefs[KEY_ACTIVE] == id) {
                val next = remaining.firstOrNull()?.id
                if (next != null) prefs[KEY_ACTIVE] = next else prefs.remove(KEY_ACTIVE)
            }
        }
    }

    suspend fun restoreInstances(instances: List<SiteInstance>, activeId: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_INSTANCES] = SiteInstanceCodec.encodeInstances(instances)
            if (activeId != null) prefs[KEY_ACTIVE] = activeId else prefs.remove(KEY_ACTIVE)
        }
    }

    suspend fun setActive(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ACTIVE] = id }
    }

    suspend fun setPassword(id: String, password: String) {
        context.settingsDataStore.edit { prefs ->
            val passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()).toMutableMap()
            if (password.isEmpty()) passwords.remove(id) else passwords[id] = password
            prefs[KEY_PASSWORDS] = SiteInstanceCodec.encodePasswords(passwords)
        }
    }

    suspend fun setProxy(host: String, port: Int) {
        context.settingsDataStore.edit { prefs ->
            if (host.isBlank() || port <= 0) {
                prefs.remove(KEY_PROXY_HOST)
                prefs.remove(KEY_PROXY_PORT)
            } else {
                prefs[KEY_PROXY_HOST] = host.trim()
                prefs[KEY_PROXY_PORT] = port
            }
        }
    }

    companion object {
        private val KEY_INSTANCES = stringPreferencesKey("instances")
        private val KEY_ACTIVE = stringPreferencesKey("active_instance_id")
        private val KEY_PASSWORDS = stringPreferencesKey("passwords")
        private val KEY_PROXY_HOST = stringPreferencesKey("proxy_host")
        private val KEY_PROXY_PORT = intPreferencesKey("proxy_port")
    }
}
