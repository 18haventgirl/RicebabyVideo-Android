# M25 刷新与加载可靠性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让下拉刷新真正重同步资源清单并给出可见结果，让坏响应/网络抖动不再清空屏幕或永久报错。

**Architecture:** 策略判定全部抽成无 Android 依赖的纯对象（`FeedMergePolicy` / `RefreshNotice` / `SourceFreshness` / `SearchRetryPolicy`）以便 JUnit 直测；ViewModel 只负责编排与状态写入；网络重试落在 `DoubanRepository` 与 `SearchViewModel` 两处 choke point。不新增表、不新增字段（复用 `video_sources.updatedAt`）。

**Tech Stack:** Kotlin + Jetpack Compose（Material3 1.4.0）、Hilt 2.60.1、Room 2.8.5、OkHttp 4.12.0 + MockWebServer、kotlinx-coroutines、JUnit4。AGP 9.3.3 内置 Kotlin。

**Spec:** `docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md`（M25 六条见会话批准记录；验收记录落在该文档末尾）

## Global Constraints

- 所有面向用户的文案必须中文；UI 不暴露架构术语（不出现「同步」「订阅」「DAO」等词，用「资源清单」「采集站」）。
- 仓库只存源码：不提交令牌、keystore、APK；签名材料在仓库外 `signing.properties`（gitignored）。
- 只在 Studio 模拟器（Pixel_9 AVD，emulator-5554）验证，不碰用户真机。
- 构建命令：`JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat <task> --console=plain`，输出重定向到日志再 `echo $?`（管道 tail 会吞退出码）。
- adb 路径：`/d/Files/AndroidSDK/platform-tools/adb.exe`；uiautomator 前先 `export MSYS_NO_PATHCONV=1`。
- 模拟器直连 Cloudflare Pages 不通，验证期间保持 `adb reverse tcp:7890 tcp:7890` + `settings put global http_proxy 127.0.0.1:7890`。
- M3 1.4.0 线性进度条必须传 `gapSize = 0.dp` 与 `drawStopIndicator = {}`（确定态）才无末端白点。
- 每个 Task 结束 commit 一次；版本号与发版只在 Task 7 做。
- 单测目录约定：`app/src/test/java/com/ricebaby/video/<layer>/`，测试名用中文反引号。

## File Structure

| 文件 | 动作 | 职责 |
|---|---|---|
| `app/src/main/java/com/ricebaby/video/di/NetworkModule.kt` | 修改 | readTimeout 15s→30s |
| `app/src/main/java/com/ricebaby/video/data/douban/DoubanRepository.kt` | 修改 | GET 失败自动重试一次 |
| `app/src/main/java/com/ricebaby/video/data/feed/FeedMergePolicy.kt` | 新建 | 第一页替换的截断/骤减判定 |
| `app/src/main/java/com/ricebaby/video/data/feed/RefreshNotice.kt` | 新建 | 刷新结果文案组合 |
| `app/src/main/java/com/ricebaby/video/data/sources/SourceFreshness.kt` | 新建 | 24h 过期判定 |
| `app/src/main/java/com/ricebaby/video/data/db/Daos.kt` | 修改 | `SourceDao.lastSyncedAt()` |
| `app/src/main/java/com/ricebaby/video/data/sources/SourceBootstrap.kt` | 修改 | 冷启动过期自动 force 重同步 |
| `app/src/main/java/com/ricebaby/video/ui/home/HomeViewModel.kt` | 修改 | refresh 串行三步 + notice 通道 + 替换走策略 |
| `app/src/main/java/com/ricebaby/video/ui/home/HomeScreen.kt` | 修改 | snackbar 消费 notice |
| `app/src/main/java/com/ricebaby/video/search/SearchRetryPolicy.kt` | 新建 | SSE 中断重试判定 |
| `app/src/main/java/com/ricebaby/video/ui/search/SearchViewModel.kt` | 修改 | 中断重试一次 / 有结果保留 |
| `app/src/main/assets/sources-snapshot.json` | 修改 | 明文源实测后剔除死源 |
| 对应 `app/src/test/...` | 新建/修改 | 每个纯对象与重试行为的单测 |

---

### Task 1: 站点 GET 重试与读超时放宽

**Files:**
- Modify: `app/src/main/java/com/ricebaby/video/di/NetworkModule.kt:18`
- Modify: `app/src/main/java/com/ricebaby/video/data/douban/DoubanRepository.kt:60-68`
- Test: `app/src/test/java/com/ricebaby/video/data/douban/DoubanTest.kt`

**Interfaces:**
- Consumes: 现有 `DoubanRepository(http: OkHttpClient)`、`tags(baseUrl, type): List<String>?`、`recommend(baseUrl, tag, type, pageStart): List<DoubanItem>?`（签名不变）
- Produces: 签名不变；行为变为「失败自动重试一次，间隔 800ms」，供 Task 3 的刷新编排依赖

- [ ] **Step 0: 建里程碑分支（从 main 切出，后续 Task 都提交在它上面）**

```bash
git checkout main && git checkout -b feature/m25-refresh-resilience
```

- [ ] **Step 1: 写失败测试（重试后成功）**

先把现有 `请求失败返回 null 以便与空结果区分` 里单条 `MockResponse().setResponseCode(500)` 改成 enqueue 两条 500（否则重试的第二次请求会挂到读超时才返回）。再在 `DoubanTest.kt` 追加：

```kotlin
    @Test
    fun `首次失败自动重试一次并成功`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"tags":["热门"]}"""))
        val tags = repo().tags(server.url("/").toString().removeSuffix("/"), "movie")
        assertEquals(listOf("热门"), tags)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `两次都失败才返回 null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(repo().tags(server.url("/").toString().removeSuffix("/"), "movie"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `成功时不重试`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"tags":["热门"]}"""))
        assertEquals(listOf("热门"), repo().tags(server.url("/").toString().removeSuffix("/"), "movie"))
        assertEquals(1, server.requestCount)
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.data.douban.DoubanTest" --console=plain > .debug-ui/t1.log 2>&1; echo $?`
Expected: 非 0；`首次失败自动重试一次并成功` 报 tags 为 null（当前实现不重试）。

- [ ] **Step 3: 实现重试**

`DoubanRepository.kt` 顶部加 `import kotlinx.coroutines.delay`，把 `get` 替换为：

```kotlin
    private suspend fun <T> get(url: String, parse: (String) -> T): T? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        // 国内到 Pages 函数的路由会间歇性挂死或截断，单次请求等于把抖动直接甩给用户
        repeat(2) { attempt ->
            val result = runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parse(response.body?.string().orEmpty())
                }
            }.getOrNull()
            if (result != null) return@withContext result
            if (attempt == 0) delay(RETRY_DELAY_MS)
        }
        null
    }

    private companion object {
        const val RETRY_DELAY_MS = 800L
    }
```

注意：原实现里「成功但空列表」是合法结果（`emptyList()` 非 null），`result != null` 判定不会把空结果误当失败重试。

`NetworkModule.kt:18` 改为 `.readTimeout(30, TimeUnit.SECONDS)`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令。Expected: exit 0，DoubanTest 8 条全绿（原 5 + 新 3）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ricebaby/video/di/NetworkModule.kt app/src/main/java/com/ricebaby/video/data/douban/DoubanRepository.kt app/src/test/java/com/ricebaby/video/data/douban/DoubanTest.kt
git commit -m "feat: 站点 GET 失败自动重试一次，读超时放宽到 30s"
```

---

### Task 2: 第一页替换的截断防护与刷新文案（纯函数）

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/data/feed/FeedMergePolicy.kt`
- Create: `app/src/main/java/com/ricebaby/video/data/feed/RefreshNotice.kt`
- Test: `app/src/test/java/com/ricebaby/video/data/feed/FeedMergePolicyTest.kt`
- Test: `app/src/test/java/com/ricebaby/video/data/feed/RefreshNoticeTest.kt`

**Interfaces:**
- Consumes: `com.ricebaby.video.data.douban.DoubanItem`（现有 data class，含 `id`）
- Produces:
  - `data class ReplaceDecision(val items: List<DoubanItem>, val keptOld: Boolean, val failed: Boolean)`
  - `object FeedMergePolicy { fun decideReplace(old: List<DoubanItem>, fetched: List<DoubanItem>?): ReplaceDecision }`
  - `object RefreshNotice { fun compose(reason: String?, sourceCount: Int, decision: ReplaceDecision, added: Int): String }`

- [ ] **Step 1: 写失败测试**

`FeedMergePolicyTest.kt`：

```kotlin
package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedMergePolicyTest {
    private fun items(n: Int) = (1..n).map { DoubanItem(id = "$it", title = "片$it", cover = "", rate = "", playable = true) }

    @Test
    fun `正常替换用新列表`() {
        val d = FeedMergePolicy.decideReplace(items(20), items(20))
        assertEquals(20, d.items.size)
        assertFalse(d.keptOld)
        assertFalse(d.failed)
    }

    @Test
    fun `请求失败且有旧数据时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, null)
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
        assertTrue(d.failed)
    }

    @Test
    fun `请求失败且无旧数据时标记失败`() {
        val d = FeedMergePolicy.decideReplace(emptyList(), null)
        assertTrue(d.items.isEmpty())
        assertTrue(d.failed)
        assertFalse(d.keptOld)
    }

    @Test
    fun `新列表骤减到旧列表三分之一以下时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, items(6))
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
        assertFalse(d.failed)
    }

    @Test
    fun `新列表为空且旧列表非空时保留旧列表`() {
        val old = items(20)
        val d = FeedMergePolicy.decideReplace(old, emptyList())
        assertEquals(old, d.items)
        assertTrue(d.keptOld)
    }

    @Test
    fun `旧列表为空时空结果不算截断`() {
        val d = FeedMergePolicy.decideReplace(emptyList(), emptyList())
        assertTrue(d.items.isEmpty())
        assertFalse(d.keptOld)
        assertFalse(d.failed)
    }
}
```

`RefreshNoticeTest.kt`：

```kotlin
package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshNoticeTest {
    private fun decision(keptOld: Boolean = false, failed: Boolean = false) =
        ReplaceDecision(emptyList<DoubanItem>(), keptOld, failed)

    @Test
    fun `同步或内容失败时给出原因并说明保留旧内容`() {
        assertEquals(
            "刷新失败：站点连不上，已保留上次内容",
            RefreshNotice.compose("站点连不上", 73, decision(failed = true), 0),
        )
    }

    @Test
    fun `清单更新失败但内容刷新成功时分开说`() {
        assertEquals(
            "资源清单更新失败：站点连不上 · 内容已是最新",
            RefreshNotice.compose("站点连不上", 73, decision(), 0),
        )
    }

    @Test
    fun `截断保留旧内容时单独提示`() {
        assertEquals(
            "网络返回不完整，已保留上次内容",
            RefreshNotice.compose(null, 73, decision(keptOld = true), 0),
        )
    }

    @Test
    fun `有新增时报新增数量`() {
        assertEquals(
            "资源清单已更新（73 个采集站）· 新增 3 部",
            RefreshNotice.compose(null, 73, decision(), 3),
        )
    }

    @Test
    fun `无新增时报内容已是最新`() {
        assertEquals(
            "资源清单已更新（73 个采集站）· 内容已是最新",
            RefreshNotice.compose(null, 73, decision(), 0),
        )
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.data.feed.*" --console=plain > .debug-ui/t2.log 2>&1; echo $?`
Expected: 编译失败（对象不存在）。

- [ ] **Step 3: 实现两个纯对象**

`FeedMergePolicy.kt`：

```kotlin
package com.ricebaby.video.data.feed

import com.ricebaby.video.data.douban.DoubanItem

data class ReplaceDecision(val items: List<DoubanItem>, val keptOld: Boolean, val failed: Boolean)

/** 第一页替换时的残缺判定：站点代理会在约 15KB 处截断 JSON，一次坏响应不该清空整屏 */
object FeedMergePolicy {
    fun decideReplace(old: List<DoubanItem>, fetched: List<DoubanItem>?): ReplaceDecision = when {
        fetched == null -> ReplaceDecision(old, keptOld = old.isNotEmpty(), failed = true)
        old.isNotEmpty() && fetched.size * 3 < old.size -> ReplaceDecision(old, keptOld = true, failed = false)
        else -> ReplaceDecision(fetched, keptOld = false, failed = false)
    }
}
```

`RefreshNotice.kt`：

```kotlin
package com.ricebaby.video.data.feed

/** 下拉刷新结束后的一条提示：失败说原因，成功说清单与内容变化 */
object RefreshNotice {
    fun compose(reason: String?, sourceCount: Int, decision: ReplaceDecision, added: Int): String = when {
        decision.failed || decision.keptOld ->
            (if (reason != null) "刷新失败：$reason" else "网络返回不完整") + "，已保留上次内容"
        reason != null -> "资源清单更新失败：$reason · 内容已是最新"
        else -> "资源清单已更新（$sourceCount 个采集站）· " + if (added > 0) "新增 $added 部" else "内容已是最新"
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: exit 0，11 条全绿。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ricebaby/video/data/feed/FeedMergePolicy.kt app/src/main/java/com/ricebaby/video/data/feed/RefreshNotice.kt app/src/test/java/com/ricebaby/video/data/feed/FeedMergePolicyTest.kt app/src/test/java/com/ricebaby/video/data/feed/RefreshNoticeTest.kt
git commit -m "feat: 第一页替换的截断防护与刷新结果文案判定"
```

---

### Task 3: 清单 24h 保鲜

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/data/sources/SourceFreshness.kt`
- Modify: `app/src/main/java/com/ricebaby/video/data/db/Daos.kt`（SourceDao 内）
- Modify: `app/src/main/java/com/ricebaby/video/data/sources/SourceBootstrap.kt:42-46`
- Test: `app/src/test/java/com/ricebaby/video/data/sources/SourceFreshnessTest.kt`
- Modify: `app/src/test/java/com/ricebaby/video/data/sources/SubscriptionSyncerTest.kt`（FakeSourceDao 补新方法）

**Interfaces:**
- Consumes: `SourceDao.allOnce()/deleteByIds()/enabledOnce()`（M24b 已加）、`VideoSourceEntity.updatedAt: Long`
- Produces:
  - `object SourceFreshness { fun isStale(lastSyncedAt: Long?, nowMillis: Long, maxAgeMillis: Long = 24L * 3600 * 1000): Boolean }`
  - `SourceDao.lastSyncedAt(): Long?`（suspend，`SELECT MAX(updatedAt)`）
  - `SourceBootstrap.ensureReady(force)` 行为：非 force 且库非空但已过期时不再早退，继续走同步

- [ ] **Step 1: 写失败测试**

`SourceFreshnessTest.kt`：

```kotlin
package com.ricebaby.video.data.sources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFreshnessTest {
    private val day = 24L * 3600 * 1000

    @Test
    fun `从未同步过算过期`() {
        assertTrue(SourceFreshness.isStale(null, 1_000_000L))
    }

    @Test
    fun `一天内不算过期`() {
        assertFalse(SourceFreshness.isStale(1_000_000L, 1_000_000L + day - 1))
    }

    @Test
    fun `超过一天算过期`() {
        assertTrue(SourceFreshness.isStale(1_000_000L, 1_000_000L + day + 1))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.data.sources.SourceFreshnessTest" --console=plain > .debug-ui/t3.log 2>&1; echo $?`
Expected: 编译失败（对象不存在）。

- [ ] **Step 3: 实现纯函数与 DAO 查询**

`SourceFreshness.kt`：

```kotlin
package com.ricebaby.video.data.sources

/** 清单保鲜：超过一天没重同步就在冷启动时补一次，用户无感 */
object SourceFreshness {
    fun isStale(lastSyncedAt: Long?, nowMillis: Long, maxAgeMillis: Long = 24L * 3600 * 1000): Boolean =
        lastSyncedAt == null || nowMillis - lastSyncedAt > maxAgeMillis
}
```

`Daos.kt` 的 `SourceDao` 内追加：

```kotlin
    @Query("SELECT MAX(updatedAt) FROM video_sources")
    suspend fun lastSyncedAt(): Long?
```

`SubscriptionSyncerTest.kt` 的 `FakeSourceDao` 追加：

```kotlin
    override suspend fun lastSyncedAt(): Long? = stored.values.maxOfOrNull { it.updatedAt }
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: exit 0，3 条绿；同时 `--tests "com.ricebaby.video.data.sources.*"` 全绿（FakeSourceDao 编译通过）。

- [ ] **Step 5: 接进 SourceBootstrap**

`SourceBootstrap.kt` 把早退一行替换为：

```kotlin
        if (!force && dao.enabledOnce().isNotEmpty() &&
            !SourceFreshness.isStale(dao.lastSyncedAt(), System.currentTimeMillis())
        ) {
            return Result(BootstrapState.Ready)
        }
```

- [ ] **Step 6: 编译确认**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:compileReleaseKotlin --console=plain > .debug-ui/t3b.log 2>&1; echo $?`
Expected: exit 0。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ricebaby/video/data/sources/SourceFreshness.kt app/src/main/java/com/ricebaby/video/data/db/Daos.kt app/src/main/java/com/ricebaby/video/data/sources/SourceBootstrap.kt app/src/test/java/com/ricebaby/video/data/sources/SourceFreshnessTest.kt app/src/test/java/com/ricebaby/video/data/sources/SubscriptionSyncerTest.kt
git commit -m "feat: 资源清单超过 24h 未重同步时冷启动自动补一次"
```

---

### Task 4: 下拉刷新真重同步 + 结果可见

**Files:**
- Modify: `app/src/main/java/com/ricebaby/video/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/ricebaby/video/ui/home/HomeScreen.kt:75-90`

**Interfaces:**
- Consumes: Task 2 的 `FeedMergePolicy.decideReplace` / `RefreshNotice.compose` / `ReplaceDecision`；Task 3 的 `SourceBootstrap.ensureReady(force = true)`（返回 `Result(state, detail, accepted, errors)`，`BootstrapState.Ready` 为成功）；`SourceDao.enabledOnce()`
- Produces: `HomeUiState.notice: String?`、`HomeViewModel.consumeNotice()`；`refresh()` 新行为（不清空旧列表、串行 sync→tags→第一页、结束写 notice）

- [ ] **Step 1: HomeViewModel 注入与状态字段**

构造参数追加两个依赖（放在 `tagStore` 之后）：

```kotlin
    private val sourceBootstrap: SourceBootstrap,
    private val sourceDao: SourceDao,
```

并加 import：`com.ricebaby.video.data.sources.SourceBootstrap`、`com.ricebaby.video.data.sources.BootstrapState`、`com.ricebaby.video.data.db.SourceDao`、`com.ricebaby.video.data.feed.FeedMergePolicy`、`com.ricebaby.video.data.feed.RefreshNotice`。

`HomeUiState` 追加字段 `val notice: String? = null,`；`Internal` 追加 `val notice: String? = null,`；`combine` 块里 `HomeUiState(...)` 追加 `notice = t.notice,`。

追加消费函数与刷新上下文：

```kotlin
    fun consumeNotice() {
        internal.value = internal.value.copy(notice = null)
    }

    private data class RefreshContext(val sourceCount: Int, val reason: String?)
    private var refreshContext: RefreshContext? = null
```

- [ ] **Step 2: 重写 refresh()**

整段替换现有 `refresh()`：

```kotlin
    /** 下拉刷新：先重同步资源清单，再重拉标签，最后按当前标签重出第一页；旧内容在新结果落地前不清空 */
    fun refresh() {
        val base = baseUrl ?: return
        val type = if (internal.value.isTv) "tv" else "movie"
        cancelFetch()
        tagsCache.remove("$base|$type")
        viewModelScope.launch {
            // 同步+标签可能跑几十秒，先把 loading 置真，下拉指示器才不会中途消失
            internal.value = internal.value.copy(loading = true, error = null)
            val boot = sourceBootstrap.ensureReady(force = true)
            val reason = if (boot.state == BootstrapState.Ready) null else boot.detail ?: "资源清单更新失败"
            val sourceCount = sourceDao.enabledOnce().size
            val serverTags = loadTags(base, type)
            if (serverTags == null) {
                refreshContext = null
                internal.value = internal.value.copy(
                    loading = false,
                    notice = RefreshNotice.compose(reason ?: "站点标签加载失败", sourceCount, FeedMergePolicy.decideReplace(internal.value.items, null), 0),
                )
                return@launch
            }
            internal.value = internal.value.copy(serverTags = serverTags)
            val visible = currentVisibleTags()
            val active = if (internal.value.activeTag in visible) internal.value.activeTag else visible.firstOrNull().orEmpty()
            refreshContext = RefreshContext(sourceCount, reason)
            internal.value = internal.value.copy(activeTag = active, pageStart = 0, loading = true, error = null)
            if (active.isNotEmpty()) startFetch(replace = true) else {
                refreshContext = null
                internal.value = internal.value.copy(loading = false)
            }
        }
    }
```

- [ ] **Step 3: startFetch 的替换分支走策略并写 notice**

把 `startFetch` 里 `fetchJob = viewModelScope.launch { ... }` 的结果处理段替换为：

```kotlin
            val fetched = douban.recommend(base, target, type, pageStart)
            if (g != generation) return@launch
            if (replace) {
                val old = snapshot.items
                val decision = FeedMergePolicy.decideReplace(old, fetched)
                val added = decision.items.count { item -> old.none { it.id == item.id } }
                val ctx = refreshContext
                refreshContext = null
                internal.value = internal.value.copy(
                    items = decision.items,
                    pageStart = 0,
                    loading = false,
                    error = when {
                        decision.items.isEmpty() && decision.failed -> "加载失败，点击重试"
                        decision.items.isEmpty() -> "这个标签下暂时没有内容"
                        else -> null
                    },
                    notice = when {
                        ctx != null -> RefreshNotice.compose(ctx.reason, ctx.sourceCount, decision, added)
                        decision.keptOld -> "网络返回不完整，已保留上次内容"
                        else -> null
                    },
                )
            } else {
                val merged = (internal.value.items + fetched.orEmpty()).distinctBy { it.id }
                internal.value = internal.value.copy(
                    items = merged,
                    pageStart = pageStart,
                    loading = false,
                    error = when {
                        fetched == null -> "加载失败，点击重试"
                        merged.isEmpty() -> "这个标签下暂时没有内容"
                        else -> null
                    },
                )
            }
```

（`target` 的计算保持原样：`RECOMMEND_LABEL` 映射到历史最佳标签或「热门」。）

- [ ] **Step 4: HomeScreen 消费 notice**

在 `HomeScreen` 的 `LaunchedEffect(nearEnd)` 之后追加：

```kotlin
    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }
```

- [ ] **Step 5: 编译 + 全量单测**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --console=plain > .debug-ui/t4.log 2>&1; echo $?`
Expected: exit 0（Hilt 图编译通过即证明注入无误）。

- [ ] **Step 6: 模拟器实测三态**

```bash
ADB=/d/Files/AndroidSDK/platform-tools/adb.exe
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:assembleRelease --console=plain > .debug-ui/t4b.log 2>&1
"$ADB" -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
"$ADB" -s emulator-5554 shell am force-stop com.ricebaby.video
"$ADB" -s emulator-5554 shell monkey -p com.ricebaby.video -c android.intent.category.LAUNCHER 1
```

等首页出海报后：① 正常下拉（`input swipe 540 900 540 1600 400`）→ 截图应见「资源清单已更新（73 个采集站）· …」且海报仍在；② `settings put global http_proxy 127.0.0.1:7891`（死端口）→ 下拉 → 截图应见「刷新失败：…，已保留上次内容」且旧海报未被清空；③ 恢复 `settings put global http_proxy 127.0.0.1:7890` → 下拉 → 成功提示。三张截图存 `.debug-ui/m25-refresh-ok.jpg / m25-refresh-fail.jpg / m25-refresh-recover.jpg`。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ricebaby/video/ui/home/HomeViewModel.kt app/src/main/java/com/ricebaby/video/ui/home/HomeScreen.kt
git commit -m "feat: 下拉刷新重同步资源清单并给出可见结果，坏响应保留旧内容"
```

---

### Task 5: SSE 搜索中断重试一次 / 有结果保留

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/search/SearchRetryPolicy.kt`
- Modify: `app/src/main/java/com/ricebaby/video/ui/search/SearchViewModel.kt`
- Test: `app/src/test/java/com/ricebaby/video/search/SearchRetryPolicyTest.kt`

**Interfaces:**
- Consumes: `SearchStreamClient.start(baseUrl, query, sources, onEvent): SearchHandle`（现有）
- Produces: `object SearchRetryPolicy { fun shouldRetry(hasResults: Boolean, attempts: Int): Boolean }`；SearchViewModel 中断行为（无结果重连一次；有结果保留并置 Done）

- [ ] **Step 1: 写失败测试**

```kotlin
package com.ricebaby.video.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRetryPolicyTest {
    @Test
    fun `无结果且没重试过时重连`() {
        assertTrue(SearchRetryPolicy.shouldRetry(hasResults = false, attempts = 0))
    }

    @Test
    fun `只重连一次`() {
        assertFalse(SearchRetryPolicy.shouldRetry(hasResults = false, attempts = 1))
    }

    @Test
    fun `已有结果时不重连避免重复流`() {
        assertFalse(SearchRetryPolicy.shouldRetry(hasResults = true, attempts = 0))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.search.SearchRetryPolicyTest" --console=plain > .debug-ui/t5.log 2>&1; echo $?`
Expected: 编译失败。

- [ ] **Step 3: 实现纯对象**

```kotlin
package com.ricebaby.video.search

/** 搜索流中断且一无所获时重连一次；已有结果就保留，重连只会带来重复流与更长等待 */
object SearchRetryPolicy {
    fun shouldRetry(hasResults: Boolean, attempts: Int): Boolean = !hasResults && attempts < 1
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: exit 0，3 条绿。

- [ ] **Step 5: 接进 SearchViewModel**

字段区追加：

```kotlin
    private var searchAttempts = 0
    private var lastBaseUrl = ""
    private var lastQuery = ""
    private var lastSources: List<VideoSourceEntity> = emptyList()
```

`submit()` 里 `handle = streamClient.start(...)` 之前追加：

```kotlin
            searchAttempts = 0
            lastBaseUrl = instance.baseUrl
            lastQuery = query
            lastSources = sources
```

`onSearchEvent` 的 Error 分支替换为：

```kotlin
            is SearchEvent.Error -> {
                val hasResults = transient.value.items.isNotEmpty()
                if (SearchRetryPolicy.shouldRetry(hasResults, searchAttempts)) {
                    searchAttempts++
                    handle = streamClient.start(lastBaseUrl, lastQuery, lastSources) { onSearchEvent(it, lastQuery) }
                } else {
                    transient.value = transient.value.copy(
                        phase = if (hasResults) SearchPhase.Done else SearchPhase.Failed,
                        message = if (hasResults) "搜索连接中断，已保留现有结果" else event.message,
                    )
                }
            }
```

加 import `com.ricebaby.video.search.SearchRetryPolicy`（同包可省）。

- [ ] **Step 6: 编译 + 全量单测**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --console=plain > .debug-ui/t5b.log 2>&1; echo $?`
Expected: exit 0。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ricebaby/video/search/SearchRetryPolicy.kt app/src/main/java/com/ricebaby/video/ui/search/SearchViewModel.kt app/src/test/java/com/ricebaby/video/search/SearchRetryPolicyTest.kt
git commit -m "feat: 搜索流中断无结果时重连一次，有结果保留并提示"
```

---

### Task 6: 明文源实测与清单处置

**Files:**
- Modify: `app/src/main/assets/sources-snapshot.json`
- 产出结论写入 Task 7 的验收记录（不新建文档）

**Interfaces:**
- Consumes: 快照中 9 条 `http://` 源（`baseUrl` 字段）
- Produces: 处置后的快照（死源剔除、可 https 的改 https）；站点侧清单改动清单交给用户执行

- [ ] **Step 1: 实测 9 个明文源**

```bash
cd /d/Workplace/qoder_use/RicebabyVideo-Android
node -e '
const a=require("./app/src/main/assets/sources-snapshot.json");
console.log(a.filter(s=>s.baseUrl.startsWith("http://")).map(s=>s.id+" "+s.baseUrl).join("\n"));
' > .debug-ui/http-sources.txt
while read -r id url; do
  https="${url/http:\/\//https://}"
  probe() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 -x http://127.0.0.1:7890 "$1" 2>/dev/null; }
  h1=$(probe "$url"); h2=$(probe "$url?ac=list"); s1=$(probe "$https"); s2=$(probe "$https?ac=list")
  h=$(( h1 > h2 ? h1 : h2 )); s=$(( s1 > s2 ? s1 : s2 ))
  echo "$id http=$h https=$s"
done < .debug-ui/http-sources.txt | tee .debug-ui/http-probe.txt
```

判定规则：`https=200` → 快照改 https；`http=200 且 https!=200` → 保留 http（搜索走站点服务端代取，明文不影响搜索；播放链接若为明文会受 Android 限制，记入验收记录的已知限制）；两者都非 200/超时 → 死源，从快照剔除。

- [ ] **Step 2: 按结论改快照**

```bash
node -e '
const fs=require("fs");
const p="app/src/main/assets/sources-snapshot.json";
const list=JSON.parse(fs.readFileSync(p,"utf8"));
const verdict={};
for(const l of fs.readFileSync(".debug-ui/http-probe.txt","utf8").trim().split("\n")){
  const m=l.match(/^(\S+) http=(\d+) https=(\d+)$/); if(!m) continue;
  const [,id,h,s]=m;
  verdict[id]= s==="200" ? "https" : (h==="200" ? "keep" : "dead");
}
const out=[];
for(const s of list){
  const v=verdict[s.id];
  if(v==="dead") continue;
  if(v==="https") s.baseUrl=s.baseUrl.replace(/^http:\/\//,"https://");
  out.push(s);
}
fs.writeFileSync(p, JSON.stringify(out,null,4)+"\n");
console.log("kept",out.length,"of",list.length,JSON.stringify(verdict));
'
grep -c '"baseUrl": "http://' app/src/main/assets/sources-snapshot.json
```

`grep -c` 的剩余明文数记入验收记录；`keep` 类源的播放限制也记入。

- [ ] **Step 3: 单测回归（快照格式不被破坏）**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.data.sources.*" --console=plain > .debug-ui/t6.log 2>&1; echo $?`
Expected: exit 0。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/sources-snapshot.json
git commit -m "chore: 明文采集源实测处置，死源剔除、可用 https 的升级"
```

站点侧订阅清单的对应改动（改 https / 删死源）整理成一条清单交给用户在站点配置里执行，App 不开全局明文。

---

### Task 7: 验收、版本与发版

**Files:**
- Modify: `app/build.gradle.kts:30-31`（versionCode 31、versionName "0.25.0-M25"）
- Modify: `docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md`（里程碑表加 M25 行 + 末尾验收记录）

**Interfaces:**
- Consumes: Task 1–6 全部交付
- Produces: tag `v0.25.0-M25` + GitHub Release（签名 APK）

- [ ] **Step 1: 全量单测 + release 构建**

Run: `JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest :app:assembleRelease --console=plain > .debug-ui/t7.log 2>&1; echo $?`
Expected: exit 0；用 node 汇总 `app/build/test-results/testDebugUnitTest/*.xml` 的 tests/failures 记入验收记录。

- [ ] **Step 2: 模拟器终验**

覆盖安装 release；复核：① 下拉刷新成功提示与海报保留；② 死代理下刷新失败提示且旧内容不清空（Task 4 截图可复用，若代码有变动重拍）；③ 搜索中断重连：搜索过程中 `adb reverse --remove-all` 制造中断 → 应自动重连失败后提示「搜索连接中断，已保留现有结果」或重连成功，恢复 reverse 后重搜正常；④ 冷启动 24h 逻辑用单测覆盖，不在真机改时钟验证。

- [ ] **Step 3: 版本号与文档**

`app/build.gradle.kts`：`versionCode = 31`、`versionName = "0.25.0-M25"`。
设计文档里程碑表追加 `| M25 | 刷新真重同步、结果可见、截断防护、网络韧性、清单保鲜、明文源处置 | `feature/m25-refresh-resilience` |`；末尾追加验收记录（含单测总数、三态截图文件名、明文源实测结论表、站点侧待办清单）。

- [ ] **Step 4: 合并打 tag 推送**

```bash
git add app/build.gradle.kts docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md
git commit -m "chore: M25 版本号与验收记录"
git checkout dev && git merge --ff-only feature/m25-refresh-resilience
git checkout main && git merge --ff-only dev
git tag -a v0.25.0-M25 -m "0.25.0-M25：刷新与加载可靠性"
git push origin dev main v0.25.0-M25   # 失败重试最多 3 次
git ls-remote origin refs/heads/main refs/tags/v0.25.0-M25
```

- [ ] **Step 5: 发 Release**

写 `.debug-ui/release-body.json`（中文文案、不用 ASCII 双引号），`node .debug-ui/ghrelease.js .debug-ui/InTheSea-v0.25.0-M25.apk`（脚本已带 `-H "Expect:"`，代理会掐 100-continue）。失败先查 `releases?per_page=3` 确认是否已建再重试，不盲目重发。下载回附件比对 sha256 与本地一致。M24b 非坏版本，不置 prerelease。

- [ ] **Step 6: 汇报**

向用户给出 Release 链接、校验结果、三态截图、明文源结论与站点侧待办清单。