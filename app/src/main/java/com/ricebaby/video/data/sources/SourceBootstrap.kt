package com.ricebaby.video.data.sources

import android.content.Context
import com.ricebaby.video.data.db.SourceDao
import com.ricebaby.video.data.settings.SettingsRepository
import com.ricebaby.video.net.ProbeOutcome
import com.ricebaby.video.net.SiteClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BootstrapState { Ready, NoSite, NoSubscription, Unreachable, NothingImported }

/**
 * 资源订阅是搜索的前提，所以由 App 自己保证它有：冷启动、解锁、搜索前各兜一次，
 * 用户全程不需要手动同步。Mutex 保证同一时刻只有一处在拉取。
 */
@Singleton
class SourceBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val siteClient: SiteClient,
    private val syncer: SubscriptionSyncer,
    private val dao: SourceDao,
) {
    private val mutex = Mutex()

    companion object {
        private const val SNAPSHOT_ASSET = "sources-snapshot.json"
    }

    data class Result(
        val state: BootstrapState,
        val detail: String? = null,
        val accepted: Int = 0,
        val errors: Int = 0,
    )

    suspend fun ensureReady(force: Boolean = false): Result = mutex.withLock {
        // 旧版本导入过的成人源要就地清掉，否则它们一直躺在库里参与搜索
        val adult = dao.allOnce().filter { SourcePolicy.isAdult(it.name, it.baseUrl) }
        if (adult.isNotEmpty()) dao.deleteByIds(adult.map { it.id })
        if (!force && dao.enabledOnce().isNotEmpty()) return Result(BootstrapState.Ready)
        val baseUrl = settingsRepository.state.first().activeInstance?.baseUrl
            ?: return Result(BootstrapState.NoSite)
        when (val probe = siteClient.probe(baseUrl)) {
            is ProbeOutcome.Unreachable -> Result(BootstrapState.Unreachable, probe.reason)
            is ProbeOutcome.Reachable -> {
                if (probe.subscriptionSources.isBlank()) return Result(BootstrapState.NoSubscription)
                val outcome = syncer.sync(probe.subscriptionSources, baseUrl)
                if (dao.enabledOnce().isNotEmpty()) {
                    return Result(BootstrapState.Ready, accepted = outcome.accepted, errors = outcome.errors.size)
                }
                // 直连与站点代理都失败（首启又没网）时用安装包内置快照兜底，保证输完口令就能看
                val snapshot = runCatching {
                    context.assets.open(SNAPSHOT_ASSET).bufferedReader().use { it.readText() }
                }.getOrNull()
                val bundled = snapshot?.let { syncer.parseSources(it) }.orEmpty()
                if (bundled.isNotEmpty()) {
                    dao.upsertAll(bundled.map { it.copy(subscriptionUrl = "bundled") })
                    return Result(BootstrapState.Ready, accepted = bundled.size)
                }
                Result(BootstrapState.NothingImported, outcome.errors.firstOrNull())
            }
        }
    }
}
