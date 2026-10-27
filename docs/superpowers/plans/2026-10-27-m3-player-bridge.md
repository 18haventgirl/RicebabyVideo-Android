# M3 WebView 播放器与存储同步桥 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 详情页点「播放」→ 全屏横屏 WebView 加载站点 `/player` 真实播放 HLS；播放产生的进度/收藏通过同步桥回写 Room；App 已有收藏/历史注入给播放器页保持状态一致。

**Architecture:** `BridgeCodec`（纯 Kotlin，TDD）负责两个方向：注入（Room → localStorage/sessionStorage 的 Web 端格式）与回读合并（Web JSON → Room DTO，收藏并集、历史按 timestamp 取最新进度，解析失败绝不改动本地数据）。`PlayerScreen` 用 androidx.webkit `addDocumentStartJavaScript` 在页面脚本执行前写入存储；周期性 + 退出时 `evaluateJavascript` 回读。全屏走 `WebChromeClient.onShowCustomView` overlay + Activity 横屏锁定。

**Tech Stack:** androidx.webkit:1.18.0；其余复用 M0-M2。

**Spec:** `docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md` §5（核心机制）

## 已核实的 Web 端存储契约（Ricebaby_Video master，2026-10-27）

| 键 | 格式 |
| --- | --- |
| `ricebabyvideo-settings` | 裸 JSON 对象：`{sources:[{id,name,baseUrl,searchPath,detailPath,enabled,priority,group}], subscriptions:[], sortBy:"default", searchHistory:true, watchHistory:true, autoNextEpisode:true, autoSkipIntro:false, skipIntroSeconds:0, autoSkipOutro:false, skipOutroSeconds:0, enableAdFilter:false, adKeywords:[], blockedCategories:[], ...}`（缺字段 Web 端有 fallback） |
| `ricebabyvideo-history-store` | zustand persist：`{"state":{"history":[VideoHistoryItem]},"version":2}`；`VideoHistoryItem={videoId,title,url,episodeIndex,source,timestamp,playbackPosition,duration,poster,episodes[{name,url,index}],showIdentifier:"title:<小写标题>",sourceMap?,vod_actor?,type_name?,vod_area?}` |
| `ricebabyvideo-favorites-store` | `{"state":{"favorites":[FavoriteItem]}}`（无 version）；`FavoriteItem={videoId,title,poster,source,sourceName,addedAt,type?,year?,remarks?}` |
| sessionStorage `gs:<key>` | `{"data":[SourceInfo],"ts":<ms>}`；`SourceInfo={id,source,sourceName,pic?,typeName?,remarks?}` |
| 播放器 URL | `{base}/player?id=<vodId>&source=<sourceId>&title=<t>&episode=<idx>&gs=<key>`；`useVideoPlayer` 从 settings.sources 解析源配置，**必须注入 settings** 否则详情请求失败 |

## Global Constraints

沿用 M0-M2。新增：注入脚本必须整体 try/catch（任何异常不得让页面白屏）；回读解析失败仅记日志；播放器页退出前必须完成一次回读。

---

### Task 1: BridgeCodec（TDD）

**Files:**
- Create: `player/BridgeCodec.kt`
- Test: `app/src/test/java/com/ricebaby/video/player/BridgeCodecTest.kt`

**Interfaces:**
- Produces:
  - `object BridgeCodec { fun settingsJson(sources: List<VideoSourceEntity>): String; fun historyJson(items: List<HistoryEntity>): String; fun favoritesJson(items: List<FavoriteEntity>): String; fun gsScript(candidates: List<VideoItem>, key: String): String; fun documentStartScript(settingsJson: String, historyJson: String, favoritesJson: String, gsScript: String): String; fun parseWebHistory(rawJson: String?): List<WebHistoryItem>; fun parseWebFavorites(rawJson: String?): List<WebFavoriteItem>; fun mergeHistory(web: List<WebHistoryItem>, local: List<HistoryEntity>): List<HistoryEntity>; fun mergeFavorites(web: List<WebFavoriteItem>, local: List<FavoriteEntity>): List<FavoriteEntity> }`
  - `data class WebHistoryItem(videoId: String, title: String, source: String, episodeIndex: Int, playbackPosition: Long, duration: Long, timestamp: Long, poster: String?)`
  - `data class WebFavoriteItem(videoId: String, title: String, source: String, sourceName: String?, poster: String?, addedAt: Long, typeName: String?, year: String?, remarks: String?)`

- [ ] Step 1: 写失败测试——注入格式（键名/version 包裹/中文转义无所谓）、回读解析（正常/`null`/坏 JSON→空列表）、合并规则（收藏并集按 `source:vodId` 去重、历史同片按 timestamp 取最新、Web 有本地无→新增、本地有 Web 无→保留）
- [ ] Step 2: 跑测试确认失败
- [ ] Step 3: 实现（kotlinx.serialization + JsonObject 手工拼装；`mergeHistory` 输出 Room DTO）
- [ ] Step 4: 跑测试全绿 → 提交 `feat: 存储桥编解码（注入格式与回读合并，脏数据零破坏）`

### Task 2: PlayerScreen 与导航

**Files:**
- Modify: `gradle/libs.versions.toml`、`app/build.gradle.kts`（androidx.webkit 1.18.0）
- Create: `player/PlayerWebView.kt`（AndroidView 封装 + chrome client）、`ui/player/PlayerScreen.kt`、`ui/player/PlayerViewModel.kt`
- Modify: `ui/detail/DetailScreen.kt`（播放按钮跳播放器）、`ui/AppRoot.kt`（注册路由）、`AndroidManifest.xml`（`android:usesCleartextTraffic` 保持 false；无新权限）

**Interfaces:**
- Consumes: `BridgeCodec`、`DetailRegistry`（payload）、`SettingsRepository`（activeInstance）、Room DAOs、`SourceDao.enabledOnce()`
- Produces: 路由 `"player?vid={vid}&ep={ep}"`；`PlayerViewModel.handoff(key, episodeIndex)` 由详情页 `registry.put` 后导航

- [ ] Step 1: 依赖与 `PlayerViewModel`：组装注入脚本（settings/history/favorites 来自 Room 快照 + gs 来自 payload.candidates），暴露 `playerUrl`（`{base}/player?...&gs=appkey`）与 `syncSnapshot(jsResult)`（解析 evaluateJavascript 返回的 `{"h":...,"f":...}` 字符串 → merge → Room upsert/delete diff）
- [ ] Step 2: `PlayerWebView.kt`：`AndroidView(factory=WebViewFactory, update)`；`WebSettings`：javaScriptEnabled、domStorageEnabled、mediaPlaybackRequiresUserGesture=false、useWideViewPort、loadWithOverviewMode、UA 保持默认（站点按 UA 判断移动端 UI）；`WebViewChromeClient` 保存/恢复 FullscreenHolder overlay；`WebViewClient.onPageStarted` 空实现（注入靠 document-start）；`WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(origin))`
- [ ] Step 3: `PlayerScreen`：Box{ 全屏 WebView + 悬浮返回按钮（左上角半透明，3s 自动隐藏，点击出/隐） }；`DisposableEffect` 锁定 `requestedOrientation=LANDSCAPE`、销毁恢复 UNSPECIFIED 并触发最后一次 `syncNow()`；`LaunchedEffect` 每 10s `evaluateJavascript("JSON.stringify({h:localStorage.getItem('ricebabyvideo-history-store'),f:localStorage.getItem('ricebabyvideo-favorites-store')})")` → `syncSnapshot`
- [ ] Step 4: DetailScreen 播放按钮 → `viewModel.handOffToPlayer(selectedEpisode)`（registry 已在）→ `navController.navigate("player?vid=${encode(key)}&ep=$idx")`；AppRoot 注册路由（vid/ep 参数）
- [ ] Step 5: 构建 + 单测回归 → 提交 `feat: WebView 播放器页（document-start 注入、周期回读、横屏全屏）`

### Task 3: 实机验收与合并

- [ ] Step 1: 全量回归（unit + debug + release）
- [ ] Step 2: 模拟器黄金路径：搜索→详情→播放 → **视频真实起播**（截图/录像帧比对）→ 播放 30s 退出 → 待看页历史记录出现且进度>0 → 重进播放器（Web 端应显示续播进度，验证注入方向）→ 在播放器内点收藏（Web UI）→ 退出 → App 收藏页出现该条目（验证回读方向）
- [ ] Step 3: 合并链、tag `v0.3.0-M3`、spec 验收记录（含 Web 全屏/画中画等未覆盖项）

---

## Self-Review 结论

1. **Spec 覆盖**：§5 注入/回读/合并/键名红线全部有任务承载；§5.2 的 document-start 时机用 androidx.webkit 达成。
2. **占位符**：无 TBD；Step 3 的悬浮返回按钮给出完整行为定义。
3. **类型一致性**：`BridgeCodec` 签名在 T1 Produces 与 T2 Consumes 一致；`WebSyncCodec` 概念并入 `BridgeCodec` 避免双命名。
