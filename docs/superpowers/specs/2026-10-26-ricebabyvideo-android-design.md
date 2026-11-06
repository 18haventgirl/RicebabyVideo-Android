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
| M7 | 设计系统与六屏 UI 重做（大标题导航、分段控件、描边卡片、骨架屏） | `feature/m7-ui-redesign` |
| M8 | 原生播放器替代 WebView 播放页与同步桥 | `feature/m8-native-player` |
| M9 | 搜索并入首页；按用户决定移除 IPTV 直播 | `feature/m9-home-search` |
| M10 | 屏幕留白与边界 token 统一，抽公共分段控件 | `feature/m10-edge-polish` |
| M11 | 全屏返回语义、播放页线路/选集与详情页同构 | `feature/m11-*`（直接提交于 main） |
| M12 | 修复系统栏被重复避让导致的顶部大留白 | `feature/m12-inset-fix` |
| M13 | 播放器控制层收紧（标题行贴顶、图标统一 20dp） | `feature/m13-player-controls` |
| M14 | 签名 Release 发布 + 全新安装首轮回归修复 | `feature/m14-release-signing` |
| M15 | 首页标签体验与竞态、搜索筛选折叠、全屏边到边与滑动手势 | `feature/m15-feed-gestures` |
| M15b | 详情页顶栏双重避让修复 | `feature/m15b-detail-inset` |
| M16 | 全 App 胶囊统一与字阶收敛 | `feature/m16-chip-type` |
| M16b | 线路默认折叠、间距收紧、管理按钮对齐 | `feature/m16b-line-collapse` |
| M17 | 更名「海里影视」、自绘进度条、图标与安全区适配 | `feature/m17-brand-seekbar` |
| M18 | 移除应用内网络代理设置 | `feature/m18-drop-inapp-proxy` |
| M19 | 站点地址内置 + 口令门槛 | `feature/m19-builtin-site-unlock` |

> M0–M6 的计划文档见 `docs/superpowers/plans/`；M7 起按用户"不再逐项确认"的授权直接以里程碑分支推进，验收记录落在本文档末尾。
> M3/M5 交付物已在 M8、M9 分别删除（WebView 播放器与同步桥、IPTV 直播），表中保留仅作历史脉络。

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
- **2026-10-28 M6 完成**（tag `v0.6.0-M6`，全量 45 单测绿）：进度单位秒↔毫秒双向换算修复；页面转场与列表项动画；数据备份导出/导入（SAF + FileProvider，模拟器实测保存对话框正常）；设置页布局溢出修复（备份/代理卡移入滚动列表）；README 补齐
- **2026-10-29 M7 完成**（tag `v0.7.0-M7`）：固定品牌配色（弃用动态取色）、大标题导航、分段控件、描边卡片、骨架屏与统一海报组件；六屏（首页/搜索/待看/直播/设置/详情）按 Apple/Google 移动风格重做；模拟器逐屏截图复核 ✓
- **2026-10-29 M8 完成**（tag `v0.8.0-M8`，39 单测绿）：WebView 播放器与 localStorage 同步桥整体移除，改为 Media3 ExoPlayer 原生播放器
  - 交付：竖屏内嵌 16:9（不再强制横屏、不再重载网页）、自定义控制层（进度/倍速 0.5–2x/±10 秒/全屏）、手势（单击显隐、双击跳转、左亮度右音量）、线路与选集即时切换、播放失败与 25 秒卡顿自动顺延线路、PiP 小窗（小窗内隐藏控制层，点画面暂停/继续）、进度落库与自动续播
  - 取流：统一走站点 `/api/proxy?url=`（与 Web 端智能代理一致，绕开头端 DNS/UA/CORS），并按原始地址显式声明 `APPLICATION_M3U8`/`APPLICATION_MPD`——代理路径无扩展名，Media3 否则会把 HLS 当普通文件解封装（UnrecognizedInputFormat）
  - 网络层：播放器复用 App 的 OkHttpClient（`media3-datasource-okhttp`，继承应用内代理设置），连接 15s/读取 30s；IPTV 播放页同步接入
  - 顺带修复：详情页线路失败自动顺延（原来一条线路抽风就停在"无法连接站点"）、历史条目点击无响应（HistoryRow 漏接 onClick）、观看进度改记到点片来源（换线路不再重复出行、续播可命中）、简介 HTML 实体还原（`&amp;nbsp;`/`&#13;` 等，新增 stripHtml + 4 条单测）
  - 模拟器实测：搜索→详情→播放真实出画（帧截图确认）、进度 00:07→01:30（±10 秒 ×3 + 实时播放）、退出后待看历史"已看 2%"单条记录 ✓、再次进入自动续播到 02:34 ✓、全屏切换与返回竖屏 ✓、PiP 进入且控制层隐藏 ✓
  - 已知限制：① 弹幕/跳片头尾未实现（原生播放器 v1 范围外）；② 部分采集源经站点代理取流带宽偏低（实测 3s 分片约 2s），会周期性缓冲——直连可缓解但受 Referer 限制，暂以自动顺延线路兜底；③ 从历史记录进入播放时只有来源那一条线路，换线路需回搜索页
- **2026-10-29 M9 完成**（tag `v0.9.0-M9`，35 单测绿）：按用户反馈收敛导航
  - 搜索并入首页：首页大标题下常驻搜索栏，点击进入就地搜索面板（返回箭头收起、自动聚焦弹键盘、历史 chip、来源/类型筛选、SSE 流式结果与取消）；点推荐海报同样直接就地出结果，不再跳独立页
  - 移除 IPTV 直播模块（用户判定源不稳定、价值不足）：删除 `ui/live/*`、`data/iptv/*` 与 M3uParser 单测、`live`/`liveplayer` 路由与页签、`ProbeOutcome.Reachable.iptvSources` 字段（站点仍返回该键，由 `ignoreUnknownKeys` 忽略）
  - 底部导航由 5 项收敛为 3 项（首页 / 待看 / 设置）；顺带删除已无引用的 `PlaceholderScreen`，设置页文案去掉"直播"
  - 数据面：IPTV 频道一直是运行时经站点代理现取现解析，Room 无相关表，备份格式也不含，故无需数据库迁移，老数据不受影响
  - 模拟器实测：三页签渲染 ✓、首页搜索栏→面板（键盘自动弹出、历史 chip 可点）✓、就地搜索流式进度与筛选徽章 ✓、结果进详情→播放链路不变 ✓、返回箭头与系统返回均回到浏览态 ✓、点首页海报直接出结果 ✓
- **2026-10-29 M10 完成**（tag `v0.10.0-M10`）：界面边界走查与统一
  - 实测发现五屏内容左边缘存在**三套留白**（首页/搜索/待看收藏 24dp、设置与详情 16dp、待看历史行 12dp），且 75 处裸 dp 绕过 token（占全部间距引用的 41%）
  - `Dimens` 补语义 token：`screen`（屏幕左右留白 24dp）、`scrollEnd`（列表收尾留白）、`fabEnd`（FAB 避让 88dp）、`gridMin`（海报列宽 116dp）、`segmentCorner/Inset/ItemCorner`；删除零引用的 `sectionGap`、`listItemGap`
  - 五屏留白统一到 24dp（改后逐屏 uiautomator 实测：首页/搜索面板/待看收藏/待看历史/设置/详情 内容左边缘均 24dp；顶栏返回图标保持 M3 规范 16dp，不计入内容留白）
  - 抽出 `SegmentedToggle` 公共组件：首页与待看原本各写一遍分段控件（含 3dp/9dp/7dp 硬编码），现统一为同一实现，并加选中块 220ms 滑动动画
  - 首页搜索面板边界对齐：返回箭头由字段外的独立按钮改为字段 `leadingIcon`，输入框容器左边缘与「搜索历史」标签、历史 chip、结果网格同轴（此前被箭头推到 56dp）
  - 设置页 LazyColumn 补 `contentPadding(bottom = fabEnd)`，修复扩展 FAB 遮挡最后一张卡片
  - 播放器控制层左右留白统一为 12dp（此前顶栏 8dp、底栏 12dp 两套）
  - 收敛后特性代码剩余约 29 处裸 dp，均为组件自身几何（海报 44×62、详情海报 120、播放按钮 52 高、描边 1dp 等），按设计债规则记为可接受例外
- **2026-11-03 播放器横屏泄漏修复**（M10 后补丁，35 单测绿）：用户报「横屏之后再退出横屏，应用变成横着的」
  - 根因一：全屏的 `DisposableEffect(fullscreen)` 里 `onDispose { }` 是空的 —— 在全屏状态直接返回上层页面时，Activity 的 `requestedOrientation` 永远留在横屏，整个应用跟着横。改为进入全屏前记录原值，`onDispose` 同时还原方向与系统栏（离开播放页也会触发）
  - 根因二（顺带发现）：请求的是 `SENSOR_LANDSCAPE`，用户**关闭自动旋转**时系统会把它降级为竖屏，点全屏毫无反应。改为 `SCREEN_ORIENTATION_LANDSCAPE` 硬性请求
  - 模拟器实测（自动旋转关闭状态）：进全屏 `cur=2424x1080` ✓；全屏中按系统返回 `cur=1080x2424` ✓；点「退出全屏」`cur=1080x2424` ✓
  - 环境记录：当日从模拟器与宿主机**直连 pages.dev 均不可达**（仅经 127.0.0.1:7890 可达），应用内代理为空时会全线报「无法连接站点」；已在测试机上填入 `127.0.0.1:7890` 并确认站点探测回到「可连接」
- **2026-11-04 M11 完成**（tag `v0.11.0-M11`，35 单测绿）：按用户三条反馈收敛播放页与边界
  - **全屏返回语义**：此前在全屏按返回（或点左上角）会直接退出播放页。现改为全屏时左上角按钮显示"退出全屏"图标并回到竖屏继续播放，系统返回同样只退全屏（`BackHandler(enabled = fullscreen)`），再按一次才离开播放页。实测：全屏 `cur=2424x1080` → 返回 `cur=1080x2424` 且仍在播放页（选集可见）→ 再返回才回详情页
  - **切换顺滑度**：media3 1.11 的 `PlayerView` 没有公开的 `surfaceType` setter（常量包内可见，已查证 aar 字节码），改用官方为 Compose 内嵌场景提供的 `setEnableComposeSurfaceSyncWorkaround(true)`，消除 AndroidView 里 SurfaceView 与合成层不同步的黑帧
  - **线路/选集呈现统一**：抽出 `ui/components/ChipRows.kt`（`LineChipRow` = FlowRow + M3 FilterChip 带选中态，`EpisodeChipRow` = FlowRow + 圆角药丸），详情页与播放页同源；播放页原来是单行 LazyRow 自定义 chip（只能看到前几条、无勾选态），现与详情页完全一致并改为纵向可滚动。实测两页线路标签左边缘同为 24dp、首个 chip 同为 40dp、换行位置一致
  - **逐页边界复核**：详情页 loading 竖衬 24.dp→`Dimens.xl`、设置页 32.dp→`Dimens.xxl`、搜索筛选药丸竖衬 6.dp→`Dimens.sm`（与新的选集 chip 同高）、海报卡标题上间距 6.dp→`Dimens.sm`、底部角标圆角 8.dp→`Dimens.sm`；特性代码裸 dp 从 29 降到 22，剩余均为图标/海报/描边等组件自身几何
  - README 播放页截图重拍（旧图仍是单行 chip，已换成换行标签版），版本号对齐 0.11.0-M11
- **2026-11-04 M12 完成**（tag `v0.12.0-M12`，35 单测绿）：修用户指出的两处"看着不对"
  - **顶部大留白**：根因是系统栏被避让了两次 —— 外层 AppRoot 的 Scaffold 已用 innerPadding 处理状态栏与底部导航，首页/待看/设置/详情四个页面又各自套了一层 Scaffold，M3 的 Scaffold 只 pad 不 consume，于是状态栏 54dp 被叠加成 108dp。实测「米宝影视」标题 top 由 120dp 降到 66dp（54 状态栏 + 12 自身留白），待看与设置同样从 120dp → 66dp。修法：四处内层 Scaffold 统一 `contentWindowInsets = WindowInsets(0,0,0,0)`
  - **分段控件白色块**：抽组件时把选中块写成 `matchParentSize()`（宽度=整个控件，靠父级 clip 裁切），切换时表现为"白色从一侧擦过来"而不是一格在滑动；改成固定控件高度 `Dimens.segmentHeight = 36dp` + 滑块宽度 = 内缩后均分的一格，并与文字格共用同一几何。中途我把滑块写成 `fillMaxHeight()`，Column 把剩余全高传进来导致白色拉到屏幕底部，已由截图抓出并修正
  - README 首页/搜索/详情/待看/设置五张截图重拍（旧图仍带修复前的顶部大留白）
- **2026-11-05 M13 完成**（tag `v0.13.0-M13`，35 单测绿）：播放器控制层布局收紧（用户指出标题行靠下、图标偏大）
  - 根因：播放页顶栏与底栏各自又加了一次 `systemBarsPadding()`，而外层 AppRoot 的 Scaffold 已避让状态栏 → 竖屏下顶栏被推到画面正中（图标 y=337，画面区 142–740）。去掉这两处重复避让后顶栏回到 y=180（贴画面顶边），底栏 y=661（距画面底边 26px）
  - 全屏态复核：顶栏 y=38、底栏距屏幕底 36px，退出全屏正常回竖屏（`cur=1080x2424`）
  - 图标统一：抽出 `ControlButton`（40dp 触摸区 + 20dp 图标），替换原先 10 处各写一遍的 IconButton；图标视觉尺寸 24dp→20dp，触摸区仍 ≥40dp 不影响可达性；中央播放键 64dp→56dp
  - 顶栏/底栏留白改用 `Dimens.sm/xs` 统一节奏；README 播放页截图重拍
- **2026-11-05 M14 完成**（tag `v0.14.0-M14`，35 单测绿）：签名 Release 发布，并用全新安装跑首轮回归
  - **签名链路**：keystore 落在仓库外（PKCS12 / RSA 2048 / SHA256withRSA / 10950 天），仓库根目录 `signing.properties` 只存路径与口令且已被 `.gitignore` 覆盖；`app/build.gradle.kts` 读不到该文件就跳过签名，clone 与 CI 仍可构建未签名 release。产物 `app-release.apk` 17.6 MB（比 debug 小 7.7 MB），`apksigner verify` 通过 v2 方案，证书 DN `CN=RicebabyVideo, OU=Mobile, O=18haventgirl, C=CN`
  - **首页不再卡在空态**：`HomeViewModel` 原来只在 `init` 里读一次 `activeInstance`，用户「先进首页→再去设置加站点→回首页」时永远停在「先在设置里添加并选择观影站点」。改为跟随站点选择变化（`map{baseUrl}.distinctUntilChanged()`）重新 bootstrap，并保留当前电影/剧集档位。实测：切到一个不可达站点首页转空，切回可用站点后无需重启即出标签流与海报
  - **观看进度不再随进程丢失**：进度原先只在离开播放页时落库，播放中被安装更新/系统杀进程就整条历史不留（实测复现：播到 00:44 后 force-stop，历史为空）。轮询循环补「播放中每 10 秒持久化一次」。实测：播到 00:44 强杀重启，待看·历史出现该条且从 00:48 继续
  - **退出全屏必回竖屏（复发根因）**：`onDispose` 还原的是进入前的 `requestedOrientation`，即 `UNSPECIFIED`；关闭自动旋转时系统保持当前横屏不转回。改为显式还原 `SCREEN_ORIENTATION_PORTRAIT`，并在清单里把 Activity 默认方向锁成竖屏（浏览界面本就按竖屏设计，播放全屏是唯一例外）。实测：进全屏 `2424x1080` → 点「退出全屏」/按返回均回到 `1080x2424`
  - **搜索空态落到页面上**：无结果与未同步资源时原来只有一条一闪而过的 Snackbar，正文整块空白（用户视角＝坏了）。改为在结果网格内直接渲染 `EmptyState`（「没有找到相关影片」等资源侧文案）。实测：搜 `zzqqxx` 页面居中显示空态文案
  - 其余回归项通过：站点探测「可连接 · 无需密码」、资源同步 78 个、搜索流式结果与来源/类型筛选、详情页线路/选集、线路切换、小窗（PiP 过渡帧已确认渲染真实画面）、倍速、收藏
- **2026-11-05 M15 完成**（tag `v0.15.0-M15`，41 单测绿）：用户手机实测反馈的四组问题
  - **标签切换像卡顿**：根因是 `setTag` 只置 loading 不清 items，而骨架屏的条件是 `items.isEmpty() && loading` —— 旧数据一直挂着，界面什么都不显示直到新数据到达。改为切标签立即清空并出骨架；实测点「欧美」后立刻是 8 格流光骨架
  - **电影/剧集来回切换很慢**：每次切换都重新请求 `api/douban/tags`（走代理一个来回 1-3 秒）。加 `tagsCache`（按 `base|type`，失败与空结果不落缓存），二次切换零等待；顺带修掉「上一个类型的当前标签在新类型里不存在仍照旧请求」的问题（现在回落到首个可用标签）
  - **「暂无内容，稍后再试」误报**：`DoubanRepository` 把 HTTP 失败和空结果都返回空列表，且并发请求没有取消 —— 慢响应会覆盖新响应、翻页串标签。改为 `tags/recommend` 失败返回 null（新增两条单测锁住该契约），请求加代次号 + 取消，文案区分「加载失败，点击重试」与「这个标签下暂时没有内容」，空态旁给「重试」按钮
  - **标签管理缺失**：新增 `FeedTagStore`（DataStore，按 movie/tv 分别存隐藏与自定义）与 `FeedTagRules.merge`（推荐位 + 站点标签 + 自定义，去掉隐藏，5 条单测）；标签行末尾加「管理」入口，BottomSheet 内 FilterChip 切换隐藏、InputChip 删除自定义、可恢复默认。实测隐藏「冷门佳片」后首页立即消失且重启仍生效；首轮实现时把 `setTagHidden(tag, hidden)` 的取反写反导致点了没反应，已由复现抓到并修正
  - **搜索点来源闪白**：连续截图抓到证据 —— 结果网格里出现纯亮灰块。三重成因：条目挂 `animateItem()` 在筛选时整批淡入淡出、Coil 内存缓存偏小导致重新绑定、占位就是亮色 surfaceVariant。修法：结果网格去掉入场动画、内存缓存提到 30%、`PosterItem` 加载期覆盖既有 `shimmer()` 骨架（与整页骨架语言一致），筛选不再出现白块
  - **筛选区太长**：来源/类型徽章默认折叠为两行，下方「展开全部筛选 / 收起」切换；实测折叠 6 枚、展开 16 枚
  - **全屏摄像头一侧白边**：142px 亮带并非刘海，而是外层 Scaffold 的内衬背景（横屏时落在左侧），窗口背景与 decor 都盖不住。改为播放页上报全屏状态，AppRoot 在全屏时不给 NavHost 加内衬；同时补 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`、全屏期间窗口背景置黑、控制层加 `displayCutoutPadding()`。实测全屏左边缘逐行采样已全黑，退出全屏回竖屏正常
  - **手势**：原来只有纵向亮度/音量且无任何反馈，横向调进度根本没实现。现按起手方向区分横竖（横=按屏宽比例 seek，抬手落位；纵=左半亮度 / 右半音量），统一 HUD（图标 + 数值 + 进度条，抬手 0.7 秒消失），离开播放页把 `screenBrightness` 还原为跟随系统。实测横滑 300px 进度 +24 秒、左半上滑 HUD「亮度 100%」、右半 HUD 跟手
  - 已知限制补记：`Compose` 的 `padding()` 不接受负值，曾试图用负 padding 抵消内衬直接抛 `IllegalArgumentException: Padding must be non-negative` 崩溃，此类"抵消父级内衬"只能自上而下解决
- **2026-11-05 M15b 完成**（tag `v0.15.1-M15b`，41 单测绿）：用户手机截图指出详情页顶部留白仍未修
  - 根因：M12 只把内层 `Scaffold` 的 `contentWindowInsets` 归零，漏了 `TopAppBar` 自己默认的 `windowInsets`（状态栏 + 顶栏内衬）—— 外层已避让 54dp，顶栏又加 54dp，标题行落到 126dp。全站只有详情页用 TopAppBar，所以此前逐页量「标题 top」时没覆盖到它
  - 修法：`TopAppBar(windowInsets = WindowInsets(0,0,0,0))`。实测返回键 bounds 由 `[43,337]` 上移到 `[43,195]`（195px ≈ 54dp 状态栏 + 顶栏自身内边距）
  - 发布处理：v0.15.0-M15 置为预发布并在说明里指向新版，代码 tag 保留不删
- **2026-11-05 M16 完成**（tag `v0.16.0-M16`，41 单测绿）：用户要求详情页/播放页的线路展示向搜索结果的样式统一，并顺带收敛字体层级
  - **胶囊统一**：新增 `ui/components/SelectChip.kt` —— 官方 M3 `FilterChip` 去掉描边（`border = null`）与阴影（`elevation = null`），圆角 `pillCorner`，配色 surfaceVariant@0.5 / 选中 primaryContainer，文案固定 labelMedium。线路（`LineChipRow`）、选集（`EpisodeChipRow`）、搜索来源与类型筛选（原手写 `FilterPill` 改为薄封装）、搜索历史、首页标签、标签管理面板全部换成它；管理面板里带删除钮的自定义标签用官方 `InputChip` 并配同一套 shape/colors。material3 1.4.0 的 `FilterChip` 已无 `checkmark` 参数（选中态靠配色），已查证源码
  - **字阶补齐**：`Type.kt` 原来漏定义 `titleSmall` 与 `labelLarge`，它们悄悄回落到 M3 默认值（14 Medium / 14 Medium），与自定义层混排。现补 `titleSmall = 14 SemiBold`、`labelLarge = 14 Medium`，并把 `bodySmall` 从 13sp 收到 12sp/17lh 对齐标准 caption；文件头写明分工：页名 headlineMedium、分区标题 titleMedium、卡片内小标题 titleSmall、条目正文 bodyLarge/bodyMedium、辅助信息 bodySmall、胶囊 labelMedium、按钮 labelLarge
  - **按分工归位**：设置页三张卡片标题（视频资源 / 数据备份 / 网络代理）titleSmall→titleMedium；详情页「播放 · 集名」titleMedium→labelLarge（按钮文案）；其余分区标题统一 titleMedium
  - 实测：首页标签、搜索筛选、详情线路与选集的胶囊同高同圆角同配色，设置页卡片标题层级一致
- **2026-11-05 M16b 完成**（tag `v0.16.1-M16b`，41 单测绿）：用户指出 M16 理解偏了 —— 要的是搜索筛选那种**默认折叠**，不只是样式统一
  - **线路折叠**：`LineChipRow` 默认 `maxLines = 2`，下方「展开全部 N 条线路 / 收起」由用户主动展开；是否显示开关用 FlowRow 实测高度换算行数（`IntSize` + 32dp 胶囊高 + 4dp 间距），只占一行时不出现多余按钮。foundation 1.12.1 的 FlowRow 有 `maxLines`/`overflow` 但**没有** `onLinesCount`，所以走高度换算
  - **间距收紧**：胶囊之间左右与上下由 8dp 收到 4dp（线路、选集、首页标签、搜索筛选同步），此前 8dp + 胶囊自带 12dp 内边距让整组看起来很散
  - **首页「管理」对齐**：原来放在 FlowRow 旁的 `Row(verticalAlignment = Top)` 里，与 32dp 高的胶囊根本不在一条线上。改为把「⚙ 管理」作为最后一个元素排进同一条 FlowRow（新增 `TagChipRow`），高度与胶囊一致、随行换行
  - 实测：详情页 29 条线路折叠成两行，「展开全部 29 条线路」与选集同屏可见；播放页同样；展开后 29 条全部铺开
  - 撤版：v0.16.0-M16 置为预发布并指向 v0.16.1-M16b
- **2026-11-05 M17 完成**（tag `v0.17.0-M17`，41 单测绿）：进度条重做、去掉首页页名、更名与图标
  - **进度条**：确认是设计缺陷而非有意为之 —— M3 `Slider` 轨道仅 4dp、滑块 10dp、触摸区 48dp，横屏下按不准；且 `onValueChange` 每帧 seek 一次，拖动时画面反复跳。改为自绘 `SeekBar`：轨道常态 5dp / 拖动 8dp，滑块 6→9dp，触摸区 30dp 高，拖动只更新预览并复用中间 HUD 显示目标时间，松手才真正 seek；时间读数改为跟随拖动位置，并夹到 [0,1]（此前会出现「24:16 / 24:15」）。实测向右拖动进度由 00:22 走到 10:38
  - **首页页名去掉**：删除 `米宝影视` 大标题，搜索框直接顶到状态栏下方 lg，首屏多出一行海报
  - **更名**：`app_name` 改为「海里影视」，README 主标题同步；`applicationId` 与包名保持 `com.ricebaby.video` 不变（改了会断掉覆盖升级），GitHub 仓库名暂未改
  - **图标**：用桌面的 `海里海浪播放图标.png`（1254²，navy #041C3D + 米色 #FEF2DA）。踩到的坑：① 源图圆角外是纯白，按 GraphicsPath 圆角裁剪时半径只要小于原图的 18.8% 就会带进一圈白弧，最终改为「圆角裁剪 + 近白像素换成 navy」双保险；② 自适应图标的可见圆只有画布的 66%，画面按 84%/88% 摆放都会切掉「海里」，按公式解到 52% 并上移 8% 才完整；③ PowerShell 里 `Add-Type` 定义的类与 `GraphicsPath.AddArc` 参数绑定不稳，改用 JBR 的 `java File.java` 单文件模式跑 Java2D。产出 `mipmap-{m,h,2h,3h,4}dpi/ic_launcher_foreground.png` + 纯色背景层 + 单色（三角形）蒙版层
  - 实测：模拟器启动器图标为 navy 圆面 + 海浪播放标 + 完整「海里」二字，无白角、无切字
- **2026-11-05 M18 完成**（tag `v0.18.0-M18`，41 单测绿）：按用户要求删掉应用内网络代理设置
  - 删除范围（不留死代码）：设置页 `ProxyCard`、`SettingsViewModel.saveProxy` 与 `proxyMessage`、`AppSettings.proxyHost/proxyPort/proxyEnabled`、`SettingsRepository.setProxy` 与两个 DataStore 键、`net/AppProxySelector`、`NetworkModule` 的 `proxySelector`、`RicebabyVideoApp.onCreate` 里的代理热更新、备份负载里的两个字段
  - 保留：站点侧 `/api/proxy` 取流（`StreamUrls.proxyStreamUrl`）与「站点访问密码」—— 与被删功能无关
  - 兼容：旧备份 JSON 里残留的 proxy 字段靠 `ignoreUnknownKeys` 吸收；旧 DataStore 里的键不再读取，无需迁移
  - 验证：测试机原本靠应用内代理访问站点，改走系统级代理（`settings put global http_proxy 127.0.0.1:7890` + `adb reverse`）后首页标签流、搜索、设置页均正常；设置页只剩观影站点 / 数据备份 / 站点列表三块，截图重拍
- **2026-11-06 M19 完成**（tag `v0.19.0-M19`，46 单测绿）：站点地址内置，新用户由「输入网站」改为「输入口令」
  - 前置调研：站点没有服务端鉴权可依赖（`/api/auth/register|login|session|request-code|verify` 全部 404），因此门槛只能做在 App 侧 —— 口令原文不进仓库，`app/build.gradle.kts` 里只留 SHA-256 摘要，`AppUnlock.matches` 用 `MessageDigest.isEqual` 比对；已明确告知用户：拆包即可拿到地址与摘要，这是便利门槛而非安全边界
  - 内置地址：`buildFeatures.buildConfig = true` + `DEFAULT_BASE_URL`；`SettingsRepository.ensureDefaultInstance` 只在实例列表为空时写入并激活，返回是否新建，老用户升级不会被塞进第二个站点
  - 解锁状态：`UnlockRepository`（独立 DataStore `ricebaby_unlock`）只记一个布尔值，口令过一次就不再问；`AppRoot` 拆成门槛 + `AppShell`，状态未就绪时先站住品牌位（`BrandSplash`），避免闪一下口令页
  - 首启补齐资源订阅：解锁当次顺带 `probe + sync` 一次（原来新用户点首页海报会走到「还没有可搜索的资源，请在设置页同步资源订阅」，与"输入口令就能用"矛盾）；仅在真正新建站点时同步，失败不打扰，设置页仍可手动同步
  - 保留进阶入口：口令页底部「使用自己的站点地址」写入自定义实例并放行；设置页站点列表原样保留，多实例、密码、测试、备份都不动
  - 实测（Pixel_9）：全新安装 → 口令页无任何地址输入 → 错口令出红框与「口令不正确，请再试一次」（输入框抖动 + 清空）→ 正确口令进首页，标签流与海报正常，设置页出现「海里影视 / https://ricebaby-video.pages.dev」且资源自动同步到 78 个 → 杀进程重启不再问口令 → 删除解锁状态文件模拟老用户升级，口令页出现但实例仍只有 1 个，不产生重复站点
  - 过程修正：自带站点对话框原本在点击保存后同步读 `selfHostedError` 判空关窗，协程还没跑必然为空，非法地址被静默吞掉；改为由 `unlocked` 驱动关窗，错误留在对话框里
