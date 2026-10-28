---
status: approved
date: 2026-10-26
supersedes: none
---

# RicebabyVideo Android App 设计文档

## 1. 背景与定位

RicebabyVideo（`18haventgirl/Ricebaby_Video`）是基于 Next.js 16 + React 19 的视频聚合播放平台，部署于 ricebaby-video.pages.dev。本文档设计其安卓客户端：把 Web 端的核心体验带到手机端，复用现有部署作为后端，不做功能重写式的分叉。

设计决策已与需求方确认：

| 决策点 | 结论 |
| --- | --- |
| 技术形态 | 混合：原生 UI + WebView 播放页 |
| 后端策略 | 复用现有部署实例（Web 仓库零改动） |
| 目标设备 | 仅手机（含折叠屏基础适配），不做 TV/平板专项 |
| 数据归属 | 方案 A：原生存储为唯一真相源，与 WebView localStorage 双向桥接同步 |

## 2. 目标与非目标

**目标（v1）**

- 搜索、详情、收藏、历史、源管理、设置全流程原生体验，Material 3 视觉标准
- HLS 播放、IPTV 直播通过 WebView 复用站点播放器全部能力（自动切源、跳片头尾、弹幕、代理模式）
- 播放行为（进度、收藏）通过同步桥回流到原生存储，App 与站点数据不裂开
- 支持站点密码鉴权、BASE_URL 多实例切换

**非目标（v1 明确不做）**

- Android TV / 车机 / 平板专属布局
- 原生 ExoPlayer 播放（播放器一律 WebView）
- 投屏（Chromecast）、离线缓存下载
- 多账户 profile 切换（v1 单 profile，同步桥按无 session 基础键处理，预留扩展）
- 修改 Web 仓库或依赖其新增接口

## 3. 技术栈

- Kotlin + Jetpack Compose + Material 3（动态取色）
- Navigation Compose；Hilt 依赖注入
- Room：收藏 / 历史 / 视频源 / 搜索历史；DataStore：设置项
- Retrofit + OkHttp（cookie jar 承载站点密码会话）+ kotlinx.serialization
- Coil 图片加载；AndroidViewModel + StateFlow 单向数据流
- Gradle 版本线沿用本机既有环境（AGP 9.3 pin，JBR/SDK 路径与既有项目一致），Android Studio 直接打开可编译

## 4. 界面架构

底部导航 5 栏，播放器为全屏独立 Activity：

| 界面 | 内容 | 数据来源 |
| --- | --- | --- |
| 首页 | 豆瓣标签流 + 「为你推荐」（基于观看历史的原生简化推荐：常看类型/演员/地区） | `/api/douban/*`、Room 历史 |
| 搜索 | SSE 实时聚合结果网格，清晰度/源/类型标签，源与类目筛选，搜索历史 | `/api/search-parallel` |
| 详情 | 豆瓣信息（演员可点跳转搜索）、线路列表（前 5 折叠、延迟排序）、剧集选择、观看进度 | `/api/detail`、`/api/douban` |
| 待看 | 收藏 + 历史两个 Tab，长按管理 | Room |
| 直播 | IPTV 频道分组网格，M3U/JSON 播放列表原生解析（对照 Web `m3u-parser` 逻辑移植）；频道流媒体请求一律走站点代理（`/api/iptv/stream`、`/api/proxy`），App 不直连含自定义 UA 的流 | Room 源表 + 站点代理 |
| 设置 | BASE_URL 多实例、站点密码、源/订阅管理、过滤关键词、代理模式、数据备份导入导出 | DataStore/Room |
| 播放器 | 横屏 WebView 加载 `{BASE}/player?url=…`（url 为已含代理前缀的站点播放地址），退出回详情页并恢复线路与进度 | WebView + 同步桥 |

## 5. 数据层与 WebView 同步桥（核心机制）

原生 Room/DataStore 是唯一真相源。WebView 内播放器页读写自身 localStorage，桥负责双向同步：

### 5.1 已核实的站点持久化键（Ricebaby_Video master 分支）

| 键 | 内容 | 实现位置 |
| --- | --- | --- |
| `ricebabyvideo-history-store` | 观看历史 | `lib/store/history-store.ts` |
| `ricebabyvideo-favorites-store` | 收藏 | `lib/store/favorites-store.ts` |
| `ricebabyvideo-search-history` | 搜索历史 | `lib/store/search-history-store.ts` |
| `ricebabyvideo-iptv-store` | IPTV 源 | `lib/store/iptv-store.ts` |
| `ricebabyvideo-premium-history-store` / `ricebabyvideo-premium-favorites-store` / `ricebabyvideo-premium-search-history` | 会员模式独立存储，v1 不同步 | 同上 |

站点键名经 `profiledKey()` 包装，存在 `-p-<id>` 多账户变体；v1 按无 session 基础键同步，带 profile 后缀的情况在 M3 实测核实。`settings-store` 为自定义持久化（非 zustand persist 键名），其实际存储格式与键**必须在 M3 实施时逐字节核实**，见 §9 风险。

### 5.2 注入（原生 → WebView）

在 `WebViewClient.onPageStarted` 之前，用 `document-start` 脚本将本次播放相关的 history/favorites/源/设置条目序列化为 JSON 写入对应 localStorage 键，保证站点播放器打开时状态与 App 一致（例如已看进度、收藏标记、代理模式）。

### 5.3 回读（WebView → 原生）

播放中周期性（约 10s）与退出时通过 `evaluateJavascript` 回读 localStorage 键，解析合并进 Room：

- 收藏：取并集
- 历史：同一影片按时间戳取最新进度，冲突以播放器侧为准
- 解析失败或键缺失：跳过本条并记录日志，绝不清空原生数据（稳健零丢数据红线）

### 5.4 备份

设置页提供全量数据导出/导入（单 JSON 文件），覆盖卸载重装场景；v1 不做云同步。

## 6. 错误处理

- OkHttp 统一 retry 拦截器；本机 GitHub 流量走代理的经验同样适用于 App 设置中的可选代理开关
- 所有列表界面：加载中骨架 / 空态 / 可重试错误态三态齐全，禁止白屏
- WebView：15s 加载超时提示；站点不可达时引导至设置页切换 BASE_URL；播放页渲染进程崩溃后回退详情页
- 鉴权失败（401/密码错误）：跳转设置页密码输入并保留错误原因文案

## 7. 测试与验收

- 单元测试（JUnit5）：同步桥合并逻辑（含畸形 JSON、键缺失、时间戳冲突）、M3U/JSON 解析器、repository 层
- **桥键名红线测试**：对 5.1 表中每个键构造注入→回读 round-trip，字段级断言，防止静默丢数据
- UI 全部在 Android Studio 模拟器实测黄金路径（搜索→详情→播放→回流→收藏→历史）后才视为完成；无法验证的项在验收清单中显式标注，不默认通过

## 8. 版本控制与分支

- 本地目录：`D:\Workplace\qoder_use\RicebabyVideo-Android`；远端：`18haventgirl/RicebabyVideo-Android`（GitHub API 创建，创建前向需求方确认）
- `main`：稳定可发布；`dev`：日常集成；`feature/<任务名>`：每个分配任务一条分支
- 流程：feature 完成自测 → `--ff-only` 合入 dev 并推送（不开 PR）；阶段性里程碑 dev → main 合并并打 `v0.x.0` tag
- 提交信息：`feat: / fix: / docs: / chore:` 前缀 + 中文描述
- 仓库仅存源码与文档：不含 keystore、token、APK 产物（`.gitignore` 覆盖）

## 9. 风险与缓解

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| Web 端 store 结构升级导致桥解析失配 | 高 | 解析全部容错、失败不清数据；§5.1 键名核实记录在文档；M6 后考虑版本协商 |
| settings-store 自定义持久化格式误判 | 高 | M3 开工第一步用模拟器 dump WebView localStorage 实测核实，不允许凭猜测实现 |
| profiledKey 多账户后缀导致键对不上 | 中 | M3 实测确认站点是否写入 profile 键；v1 只同步基础键 |
| 站点实例改版/下线 | 中 | BASE_URL 多实例切换能力；错误态引导 |
| HLS 片段在部分机型 WebView 兼容问题 | 中 | 播放器页保留站点自带降级逻辑；实测覆盖 Studio 模拟器与主流机型行为 |

## 10. 里程碑

| 里程碑 | 内容 | 分支建议 |
| --- | --- | --- |
| M0 | 工程脚手架、CI 本地校验、导航骨架、设置页 + BASE_URL + 鉴权 | `feature/m0-scaffold` |
| M1 | 搜索链路（SSE）+ 结果网格 + Room 存储层落地 | `feature/m1-search` |
| M2 | 详情页 + 线路/剧集 + 豆瓣接口 | `feature/m2-detail` |
| M3 | WebView 播放器 + 同步桥（含键名实测与红线测试） | `feature/m3-player-bridge` |
| M4 | 首页豆瓣标签流 + 推荐 | `feature/m4-home` |
| M5 | IPTV 频道列表 + 播放接入 | `feature/m5-iptv` |
| M6 | M3 动效打磨、空态/错误态全覆盖、导出导入 | `feature/m6-polish` |

每个里程碑交付后从 dev 合入 main 打 tag，作为用户"分配任务"的落点单位。

## 验收记录

- **2026-10-26 M0 完成**（tag `v0.0.1-M0`）：
  - 单元测试 9/9 绿（SiteInstanceCodec 4、SiteClient 5，MockWebServer 覆盖 probe/login/拒绝/不可达）
  - `assembleDebug`/`assembleRelease` 均 BUILD SUCCESSFUL（AGP 9.3.3 内置 Kotlin，未配置签名，release 为 unsigned）
  - 模拟器（Pixel_9）实测：五页签渲染与切换 ✓；设置页添加站点 `ricebaby-video.pages.dev` 自动规范为 https ✓；点「测试」→ 真实部署实例返回「可连接 · 无需密码」✓；杀进程重启后站点数据持久化 ✓
  - **未验证项**：密码鉴权 UI 路径（该部署实例 `hasAuth=false`，`Rejected/NeedPassword` 分支仅由单测覆盖，待有密码实例或 M3 桥接时实测）
  - 过程修正：`normalizeBaseUrl` 需显式协议白名单（OkHttp HttpUrl 会把 `ftp://a.com` 宽松归一化为合法 URL，绕过检查）；M0 计划中「`/api/ping` 作健康检查」不成立（它是源延迟测量接口），已改用 `GET /api/auth`
- **2026-10-27 M1 完成**（tag `v0.1.0-M1`）：
  - 单测 21/21 绿（codec 4、SiteClient 5、订阅同步 4、评分 5、SSE 流 3）；debug/release 构建通过
  - 模拟器实测：订阅同步 **78 个资源** ✓；SSE 搜索多源流式返回、源/类型徽章、海报网格 ✓；收藏→待看页→杀进程重启持久化 ✓；搜索历史 chip ✓
  - 过程修正：① 新增**应用内网络代理设置**（动态 ProxySelector；模拟器直连 raw.githubusercontent 超时，spec §6 的可选代理提前落地）；② 搜索框改单一数据源（本地状态与 VM 不同步导致按钮长期禁用）；③ MainActivity 扩充 `configChanges`（Gboard 崩溃引发配置变更会整页重建）
  - 环境教训：模拟器 Gboard 崩溃后 InputConnection 失效，`am force-stop com.google.android.inputmethod.latin` 可恢复；中文输入受阻时用 ASCII 关键词验证链路
- **2026-10-27 M2 完成**（tag `v0.2.0-M2`）：DetailClient 单测 4/4 绿（成功/缺 episodes/success:false/HTTP 异常）；模拟器实测：搜索→点结果进详情（海报/元信息/简介）、同名 3+ 线路 chip 切换后剧集按源重载（含"第1集/第01集"命名差异）、选集与播放按钮联动、返回保留搜索结果 ✓
- **2026-10-28 M3 完成**（tag `v0.3.0-M3`）：BridgeCodec 单测 9/9 绿（注入格式/回读解析/合并规则/脏数据）；模拟器实测：播放器真实起播（CDP 确认 video 元素 currentTime 90s、非暂停）、周期回读命中（webHistory=1→merged→upserted）、退出后待看历史持久化 ✓
  - 关键根因修复：① WebView 默认 UA 含 "; wv" 被 Cloudflare 反爬拦截返回假页面（白屏真凶）；② Web 端 history store 的 state 字段名是 `viewingHistory`（CDP 实测），非直觉的 `history`——注入与回读两端已对齐
  - **已知问题（M6 处理）**：① 历史条目进度值显示为 0（Web 端 playbackPosition/duration 单位为秒，且首条写入与进度更新存在时序差）；② Web 端取消收藏不会回删 App 收藏（并集策略，spec §5.3 有意为之）；③ 系统级全屏按钮依赖 onShowCustomView，模拟器未实测
- **2026-10-28 M4 完成**（tag `v0.4.0-M4`）：DoubanRepository/RecommendTagMapper 单测 5/5 绿；模拟器实测：首页标签流加载（评分角标、电影/剧集切换）✓、点卡片直达搜索并自动出结果 ✓；「为你推荐」按 ≥2 部历史规则正确隐藏（当前仅 1 条历史）
- **2026-10-28 M5 完成**（tag `v0.5.0-M5`）：M3uParser 单测 4/4；模拟器实测：站点代理拉取 m3u（76KB）→ 分组列表（卫视频道 37 个等）✓、搜索框 ✓、点频道进 ExoPlayer 播放页（PlayerView 挂载、频道名标题、错误提示）✓
  - 已知限制：站点当前 IPTV 源（live.zhi35.com 央视频道）为区域专网源，站点代理取流返回 Upstream 403——Web 端同路径同样不可播，非 App 缺陷；换公共 m3u 源即可播放
  - 架构偏差记录：IPTV 播放采用 Media3 ExoPlayer（spec §2 原计划播放器一律 WebView，但站点 /player 仅支持 CMS id+source 流程，无法承载直连流；点播播放器不受影响）
