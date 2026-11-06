package com.ricebaby.video.ui.unlock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricebaby.video.BuildConfig
import com.ricebaby.video.R
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.data.settings.UnlockRepository
import com.ricebaby.video.data.sources.SourceBootstrap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockUiState(
    val resolved: Boolean = false,
    val unlocked: Boolean = false,
    val passphrase: String = "",
    val reveal: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val errorTick: Int = 0,
    val selfHostedError: String? = null,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unlockRepository: UnlockRepository,
    private val settingsRepository: SettingsRepository,
    private val sourceBootstrap: SourceBootstrap,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            unlockRepository.unlocked.collect { isUnlocked ->
                _uiState.update { it.copy(resolved = true, unlocked = isUnlocked) }
            }
        }
    }

    fun setPassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value, error = null) }
    }

    fun toggleReveal() {
        _uiState.update { it.copy(reveal = !_uiState.value.reveal) }
    }

    fun clearSelfHostedError() {
        _uiState.update { it.copy(selfHostedError = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            if (unlockRepository.submit(state.passphrase)) {
                settingsRepository.ensureDefaultInstance(
                    name = context.getString(R.string.app_name),
                    baseUrlRaw = BuildConfig.DEFAULT_BASE_URL,
                )
                _uiState.update { it.copy(busy = false, unlocked = true) }
                // 资源订阅当场补齐，否则搜片和播放都要先去设置页点一次
                sourceBootstrap.ensureReady()
            } else {
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = "口令不正确，请再试一次",
                        errorTick = it.errorTick + 1,
                        passphrase = "",
                    )
                }
            }
        }
    }

    /** 进阶用法：自带站点地址时不经过口令，直接放行。 */
    fun submitSelfHosted(name: String, url: String) {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(busy = true, selfHostedError = null) }
        viewModelScope.launch {
            val id = settingsRepository.addInstance(name.ifBlank { "自定义站点" }, url)
            if (id == null) {
                _uiState.update {
                    it.copy(busy = false, selfHostedError = "地址无法识别，示例：video.example.com")
                }
            } else {
                unlockRepository.grant()
                _uiState.update { it.copy(busy = false, unlocked = true) }
                sourceBootstrap.ensureReady()
            }
        }
    }
}
