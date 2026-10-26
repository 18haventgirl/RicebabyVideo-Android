package com.ricebaby.video.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.data.settings.AppSettings
import com.ricebaby.video.data.settings.SettingsRepository
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val siteClient: SiteClient,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.state, transient) { settings, t ->
            t.copy(settings = settings)
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

    private fun setStatus(id: String, status: TestStatus, detail: String?) {
        transient.value = transient.value.copy(
            testStatuses = transient.value.testStatuses + (id to status),
            testDetails = if (detail != null) transient.value.testDetails + (id to detail) else transient.value.testDetails - id,
        )
    }
}
