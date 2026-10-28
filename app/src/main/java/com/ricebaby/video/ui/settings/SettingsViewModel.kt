package com.ricebaby.video.ui.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.backup.BackupCodec
import com.ricebaby.video.data.backup.BackupRepository
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.AppSettings
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.data.sources.SubscriptionSyncer
import com.ricebaby.video.net.LoginOutcome
import com.ricebaby.video.net.ProbeOutcome
import com.ricebaby.video.net.SiteClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TestStatus { Idle, Testing, ReachableNoAuth, LoginOk, Rejected, NeedPassword, Unreachable }

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val testStatuses: Map<String, TestStatus> = emptyMap(),
    val testDetails: Map<String, String> = emptyMap(),
    val lastAddInvalidUrl: Boolean = false,
    val sourceCount: Int = 0,
    val syncMessage: String? = null,
    val proxyMessage: String? = null,
    val backupMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val siteClient: SiteClient,
    private val syncer: SubscriptionSyncer,
    sourceDao: SourceDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.state, sourceDao.observeCount(), transient) { settings, count, t ->
            t.copy(settings = settings, sourceCount = count)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addInstance(name: String, baseUrl: String) = viewModelScope.launch {
        val id = settingsRepository.addInstance(name, baseUrl)
        if (id == null) {
            transient.value = transient.value.copy(lastAddInvalidUrl = true)
        }
    }

    fun removeInstance(id: String) = viewModelScope.launch { settingsRepository.removeInstance(id) }

    fun selectInstance(id: String) = viewModelScope.launch { settingsRepository.setActive(id) }

    fun savePassword(id: String, password: String) = viewModelScope.launch {
        settingsRepository.setPassword(id, password)
    }

    fun saveProxy(host: String, portText: String) = viewModelScope.launch {
        val port = portText.trim().toIntOrNull()
        if (host.isNotBlank() && (port == null || port !in 1..65535)) {
            transient.value = transient.value.copy(proxyMessage = "端口需为 1-65535")
            return@launch
        }
        settingsRepository.setProxy(host.trim(), port ?: 0)
        transient.value = transient.value.copy(
            proxyMessage = if (host.isBlank() || port == 0) "已停用代理" else "已启用代理 ${host.trim()}:$port",
        )
    }

    fun syncSources() = viewModelScope.launch {
        val instance = settingsRepository.state.first().activeInstance
        if (instance == null) {
            transient.value = transient.value.copy(syncMessage = "请先添加并选择观影站点")
            return@launch
        }
        transient.value = transient.value.copy(syncMessage = "正在同步…")
        when (val probe = siteClient.probe(instance.baseUrl)) {
            is ProbeOutcome.Unreachable ->
                transient.value = transient.value.copy(syncMessage = probe.reason)
            is ProbeOutcome.Reachable -> {
                if (probe.subscriptionSources.isBlank()) {
                    transient.value = transient.value.copy(syncMessage = "该站点未配置资源订阅")
                } else {
                    val outcome = syncer.sync(probe.subscriptionSources)
                    val suffix = if (outcome.errors.isNotEmpty()) "，${outcome.errors.size} 个订阅失败" else ""
                    transient.value = transient.value.copy(
                        syncMessage = "已同步 ${outcome.accepted} 个资源$suffix",
                    )
                }
            }
        }
    }

    fun testConnection(id: String) = viewModelScope.launch {
        val snapshot = settingsRepository.state.first()
        val instance = snapshot.instances.firstOrNull { it.id == id } ?: return@launch
        setStatus(id, TestStatus.Testing, null)
        when (val probe = siteClient.probe(instance.baseUrl)) {
            is ProbeOutcome.Unreachable -> setStatus(id, TestStatus.Unreachable, probe.reason)
            is ProbeOutcome.Reachable -> {
                if (!probe.hasAuth) {
                    setStatus(id, TestStatus.ReachableNoAuth, null)
                } else {
                    val password = snapshot.passwords[id]
                    if (password.isNullOrBlank()) {
                        setStatus(id, TestStatus.NeedPassword, "站点需要密码，请先填写")
                    } else {
                        when (val login = siteClient.login(instance.baseUrl, password)) {
                            is LoginOutcome.Success -> setStatus(id, TestStatus.LoginOk, login.name)
                            LoginOutcome.Rejected -> setStatus(id, TestStatus.Rejected, "密码不正确")
                            is LoginOutcome.Unreachable -> setStatus(id, TestStatus.Unreachable, login.reason)
                        }
                    }
                }
            }
        }
    }

    fun exportBackup(onUriReady: (Uri) -> Unit) = viewModelScope.launch {
        runCatching {
            val payload = backupRepository.collectPayload()
            val dir = java.io.File(appContext.cacheDir, "backups").apply { mkdirs() }
            val file = java.io.File(dir, "ricebaby-backup.json")
            file.writeText(BackupCodec.serialize(payload))
            FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
        }.onSuccess { uri ->
            transient.value = transient.value.copy(backupMessage = "备份已生成，选择保存位置")
            onUriReady(uri)
        }.onFailure {
            transient.value = transient.value.copy(backupMessage = "备份失败：${it.message}")
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文件")
            val payload = BackupCodec.deserialize(text) ?: error("文件格式不正确")
            val count = backupRepository.applyPayload(payload)
            "已恢复 $count 条收藏与记录"
        }.fold(
            onSuccess = { transient.value = transient.value.copy(backupMessage = it) },
            onFailure = { transient.value = transient.value.copy(backupMessage = "恢复失败：${it.message}") },
        )
    }

    private fun setStatus(id: String, status: TestStatus, detail: String?) {
        transient.value = transient.value.copy(
            testStatuses = transient.value.testStatuses + (id to status),
            testDetails = if (detail != null) transient.value.testDetails + (id to detail) else transient.value.testDetails - id,
        )
    }
}
