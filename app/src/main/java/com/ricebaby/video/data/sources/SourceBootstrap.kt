package com.ricebaby.video.data.sources

import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.ProbeOutcome
import com.ricebaby.video.net.SiteClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BootstrapState { Ready, NoSite, NoSubscription, Unreachable, NothingImported }

/**
 * 资源订阅是搜索的前提，所以由 App 自己保证它有：冷启动、解锁、搜索前各兜一次，
 * 不再要求用户去设置页手动点「同步资源」。Mutex 保证同一时刻只有一处在拉取。
 */
@Singleton
class SourceBootstrap @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val siteClient: SiteClient,
    private val syncer: SubscriptionSyncer,
    private val dao: SourceDao,
) {
    private val mutex = Mutex()

    data class Result(
        val state: BootstrapState,
        val detail: String? = null,
        val accepted: Int = 0,
        val errors: Int = 0,
    )

    suspend fun ensureReady(force: Boolean = false): Result = mutex.withLock {
        if (!force && dao.enabledOnce().isNotEmpty()) return Result(BootstrapState.Ready)
        val baseUrl = settingsRepository.state.first().activeInstance?.baseUrl
            ?: return Result(BootstrapState.NoSite)
        when (val probe = siteClient.probe(baseUrl)) {
            is ProbeOutcome.Unreachable -> Result(BootstrapState.Unreachable, probe.reason)
            is ProbeOutcome.Reachable -> {
                if (probe.subscriptionSources.isBlank()) return Result(BootstrapState.NoSubscription)
                val outcome = syncer.sync(probe.subscriptionSources)
                if (dao.enabledOnce().isNotEmpty()) {
                    Result(BootstrapState.Ready, accepted = outcome.accepted, errors = outcome.errors.size)
                } else {
                    Result(BootstrapState.NothingImported, outcome.errors.firstOrNull())
                }
            }
        }
    }
}
