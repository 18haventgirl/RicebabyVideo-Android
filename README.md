# RicebabyVideo Android

米宝影视安卓客户端：把 [Ricebaby_Video](https://github.com/18haventgirl/Ricebaby_Video)（Next.js 视频聚合站）带到手机端。

- **原生界面**（Jetpack Compose + Material 3）：首页大标题 + 搜索栏 + 豆瓣标签流，多源聚合搜索（SSE 流式、来源/类型筛选）就地展开，详情与线路选集，收藏/历史待看
- **原生播放器**（Media3 ExoPlayer）：竖屏内嵌播放不强制横屏，倍速/±10 秒/全屏/小窗（PiP），手势调亮度音量，线路与选集即时切换，失败或卡顿自动顺延线路，进度落库自动续播；取流走站点智能代理，兼容受 Referer/DNS 限制的采集源
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
- 里程碑 tag：`v0.0.1-M0` 脚手架 → `v0.1.0-M1` 搜索 → `v0.2.0-M2` 详情 → `v0.3.0-M3` 播放器桥 → `v0.4.0-M4` 首页 → `v0.5.0-M5` IPTV → `v0.6.0-M6` 打磨 → `v0.7.0-M7` UI 重做 → `v0.8.0-M8` 原生播放器 → `v0.9.0-M9` 搜索并入首页（同时移除 IPTV）
- 设计与计划文档见 `docs/superpowers/`

## 已知限制

- 弹幕、跳片头尾未实现（原生播放器 v1 范围外）
- 收藏与观看进度只存设备本地，不再与 Web 端 localStorage 互通（M8 移除同步桥）
