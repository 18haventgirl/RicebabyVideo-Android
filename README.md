# RicebabyVideo Android

米宝影视安卓客户端：把 [Ricebaby_Video](https://github.com/18haventgirl/Ricebaby_Video)（Next.js 视频聚合站）带到手机端。

- **原生界面**（Jetpack Compose + Material 3）：首页豆瓣标签流、多源聚合搜索（SSE 流式）、详情与线路选集、收藏/历史待看、IPTV 频道列表
- **站点播放器 WebView 桥接**：点播播放复用站点播放器全部能力（HLS、自动切源、弹幕、跳片头尾），观看进度与收藏通过 localStorage 同步桥双向回写本地 Room
- **数据本地化**：收藏、历史、源配置、设置全部存设备本地，支持 JSON 备份导出/导入恢复
- 后端复用已部署站点实例，BASE_URL 可在设置页多实例切换，支持站点密码与网络代理

## 构建

要求：Android Studio（2026.1.3+，AGP 9.3.3 pin）、JBR 21+、Android SDK 37。

```bash
# local.properties 指向本机 SDK
echo "sdk.dir=<SDK 路径>" > local.properties
JAVA_HOME=<JBR 路径> ./gradlew :app:assembleDebug
```

## 版本与分支

- `main`：稳定发布；`dev`：日常集成；`feature/<里程碑>`：任务分支，完成后 `--ff-only` 合入
- 里程碑 tag：`v0.0.1-M0` 脚手架 → `v0.1.0-M1` 搜索 → `v0.2.0-M2` 详情 → `v0.3.0-M3` 播放器桥 → `v0.4.0-M4` 首页 → `v0.5.0-M5` IPTV → `v0.6.0-M6` 打磨
- 设计与计划文档见 `docs/superpowers/`

## 已知限制

- 站点当前 IPTV 源为区域专网源，公网取流 403（Web 端相同），更换公共 m3u 源即可播放
- Web 端取消收藏不回删 App 收藏（同步桥取并集，见 spec §5.3）
