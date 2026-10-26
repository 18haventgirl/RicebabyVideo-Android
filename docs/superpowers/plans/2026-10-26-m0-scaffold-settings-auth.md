# M0 脚手架 + 设置 + 鉴权 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `RicebabyVideo-Android` 达到可编译、可安装、可导航的骨架状态，并交付设置页：多站点实例管理 + 站点密码 + 连通/鉴权状态检测（供后续里程碑消费）。

**Architecture:** Compose + Navigation 单 Activity，底部 5 栏骨架；DataStore 持久化实例与密码；OkHttp 直连已部署站点 `/api/auth`（GET=探测元信息，POST=密码校验，契约已从 Ricebaby_Video master 实测核实）；Hilt 注入。Web 端登录态存 localStorage 而非 Cookie，所以 App 把 `profileId/role` 存原生 DataStore，播放器桥接归 M3。

**Tech Stack:** AGP 9.3.3（本机 Studio 上限）· AGP 内置 Kotlin（禁 `org.jetbrains.kotlin.android` 插件）· `org.jetbrains.kotlin.plugin.compose` 必加 · KSP 2.3.12 · Hilt 2.60.1 · Compose BOM 2026.09.00 · Navigation 2.10.1 · DataStore 1.3.0 · OkHttp 4.12.0 + mockwebserver · kotlinx-serialization-json 1.12.0

**Spec:** `docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md`

## Global Constraints

- AGP 固定 `9.3.3`、Kotlin `2.3.21`、KSP `2.3.12`；**不写 `kotlinOptions`/`jvmTarget`**（AGP 9 内置 Kotlin 跟随 `compileOptions` 的 VERSION_17）
- `namespace/applicationId = com.ricebaby.video`，minSdk 26 / compileSdk 37 / targetSdk 37，`versionName = "0.0.1-M0"`
- 构建命令模式：`JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat <task> > ../rb-<name>.log 2>&1; echo EXIT:$?`（禁止把 gradlew 输出管道接 tail——会吞退出码）
- 仓库只存源码：`.gitignore` 已排除 keystore/token/APK；`local.properties` 不入库
- Gradle wrapper 与仓库源复用本机已验证配置（MyTokens 工程）：wrapper 指向 `file:/D:/Files/gradle/gradle-9.7.1-bin.zip`，镜像链 腾讯→华为→阿里→中央仓库，`gradle.properties` 带 `systemProp.*.proxy 127.0.0.1:7890`
- 模拟器只允许 Studio 体系 AVD：CLI 启动用 `emulator -avd Pixel_9 -no-snapshot-load`（冷启动；**严禁 `-no-snapshot-save`**，它会丢弃磁盘写入造成数据回滚）；Git Bash 下操作设备文件前先 `export MSYS_NO_PATHCONV=1`；UI 断言用 `uiautomator dump` 读 bounds，不许盲点坐标；density 2.625（px/2.625=dp）
- UI 文案一律中文、不暴露架构术语；提交前缀 `feat:/fix:/docs:/chore:` + 中文描述
- 分支：本计划全部工作在 `feature/m0-scaffold`（自 `dev` 切出）；Task 5 负责合回
- 若模拟器网络到不了 pages.dev（域名被拦/代理仅 host 层），Task 4 的实机连通测试按 spec §7 标注为「未验证」，不阻塞合并——SiteClient 行为已由 MockWebServer 单测覆盖

---

### Task 1: 工程脚手架与可运行骨架

**Files:**
- Create: `settings.gradle.kts`、`gradle.properties`、`build.gradle.kts`（根）、`gradle/libs.versions.toml`、`local.properties`
- Copy: `gradlew`、`gradlew.bat`、`gradle/wrapper/*`（从 `D:/Workplace/AndroidDev/MyTokens`）
- Create: `app/build.gradle.kts`、`app/proguard-rules.pro`、`app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/{strings.xml,colors.xml,themes.xml}`、`res/drawable/ic_launcher_foreground.xml`、`res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/java/com/ricebaby/video/{RicebabyVideoApp.kt,MainActivity.kt}`、`ui/theme/Theme.kt`、`ui/AppRoot.kt`、`ui/common/PlaceholderScreen.kt`
- Test: 无单测（本任务交付物=能编译能启动能切 5 个页签）

**Interfaces:**
- Consumes: 无
- Produces: `RicebabyVideoTheme(darkTheme,isSystemInDarkTheme 默认, dynamicColor=true, content)`；`AppRoot(navController)`；`PlaceholderScreen(title: String, milestone: String)`；路由常量字符串 `"home"|"search"|"watchlist"|"live"|"settings"`（Task 4 消费 `"settings"`）

- [ ] **Step 1: 切出工作分支**

```bash
cd /d/Workplace/qoder_use/RicebabyVideo-Android
git switch dev && git pull --ff-only && git switch -c feature/m0-scaffold
```

- [ ] **Step 2: 从 MyTokens 复制 wrapper**

```bash
cp -r /d/Workplace/AndroidDev/MyTokens/gradle/wrapper gradle/
cp /d/Workplace/AndroidDev/MyTokens/gradlew /d/Workplace/AndroidDev/MyTokens/gradlew.bat .
```

- [ ] **Step 3: 写 `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/repository/maven/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://repo1.maven.org/maven2")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/repository/maven/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://repo1.maven.org/maven2")
        google()
        mavenCentral()
    }
}

rootProject.name = "RicebabyVideo"
include(":app")
```

- [ ] **Step 4: 写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
systemProp.https.nonProxyHosts=localhost|127.0.0.1
```

写 `local.properties`（不入库）：

```properties
sdk.dir=D\:\\Files\\AndroidSDK
```

- [ ] **Step 5: 写 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.3.3"
kotlin = "2.3.21"
ksp = "2.3.12"
hilt = "2.60.1"
coreKtx = "1.19.0"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigationCompose = "2.10.1"
composeBom = "2026.09.00"
datastore = "1.3.0"
okhttp = "4.12.0"
serializationJson = "1.12.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.4.0" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

注意：**没有** `kotlin-android` 条目——AGP 9 内置 Kotlin，加它直接构建失败（本机实测结论）。

- [ ] **Step 6: 写根 `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
```

- [ ] **Step 7: 写 `app/build.gradle.kts` 与 `app/proguard-rules.pro`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.ricebaby.video"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ricebaby.video"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-M0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

`app/proguard-rules.pro` 先写一行注释占位：`# 规则将在后续里程碑按依赖需要补充（当前 release 未开混淆）`

- [ ] **Step 8: 写 `AndroidManifest.xml` 与资源文件**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".RicebabyVideoApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.RicebabyVideo">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">米宝影视</string>
</resources>
```

`res/values/colors.xml`:

```xml
<resources>
    <color name="ic_launcher_background">#006960</color>
</resources>
```

`res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.RicebabyVideo" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`res/drawable/ic_launcher_foreground.xml`（108dp 自适应前景，中心播放三角）:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M42,34 L42,74 L76,54 Z" />
</vector>
```

`res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 9: 写 Kotlin 骨架四个文件**

`RicebabyVideoApp.kt`:

```kotlin
package com.ricebaby.video

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RicebabyVideoApp : Application()
```

`MainActivity.kt`:

```kotlin
package com.ricebaby.video

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ricebaby.video.ui.AppRoot
import com.ricebaby.video.ui.theme.RicebabyVideoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RicebabyVideoTheme {
                AppRoot()
            }
        }
    }
}
```

`ui/theme/Theme.kt`:

```kotlin
package com.ricebaby.video.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun RicebabyVideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

`ui/AppRoot.kt`:

```kotlin
package com.ricebaby.video.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ricebaby.video.ui.common.PlaceholderScreen

data class TopTab(val route: String, val label: String, val icon: ImageVector)

val topTabs = listOf(
    TopTab("home", "首页", Icons.Outlined.Home),
    TopTab("search", "搜索", Icons.Outlined.Search),
    TopTab("watchlist", "待看", Icons.Outlined.Bookmarks),
    TopTab("live", "直播", Icons.Outlined.LiveTv),
    TopTab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                topTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") { PlaceholderScreen("首页", "即将上线：豆瓣标签流与为你推荐") }
            composable("search") { PlaceholderScreen("搜索", "即将上线：多源聚合搜索") }
            composable("watchlist") { PlaceholderScreen("待看", "即将上线：收藏与历史记录") }
            composable("live") { PlaceholderScreen("直播", "即将上线：IPTV 频道") }
            composable("settings") { PlaceholderScreen("设置", "即将上线：站点管理") }
        }
    }
}
```

`ui/common/PlaceholderScreen.kt`:

```kotlin
package com.ricebaby.video.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(title: String, milestone: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            milestone,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
```

- [ ] **Step 10: 首次构建**

```bash
cd /d/Workplace/qoder_use/RicebabyVideo-Android
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:assembleDebug > ../rb-m0-t1.log 2>&1; echo EXIT:$?
```

预期 `EXIT:0`，`app/build/outputs/apk/debug/app-debug.apk` 存在。若报错：AGP 内置 Kotlin 相关（如提示 kotlin.android 冲突/缺 compose 插件）按 Global Constraints 修正插件组合后重跑，不许回退到旧插件。

- [ ] **Step 11: 安装启动，UI 断言 5 页签**

```bash
"/d/Files/AndroidSDK/emulator/emulator.exe" -list-avds   # 确认 AVD 名（预期含 Pixel_9）
"/d/Files/AndroidSDK/emulator/emulator.exe" -avd Pixel_9 -no-snapshot-load > /dev/null 2>&1 &
export MSYS_NO_PATHCONV=1
"/d/Files/AndroidSDK/platform-tools/adb.exe" wait-for-device
"/d/Files/AndroidSDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
"/d/Files/AndroidSDK/platform-tools/adb.exe" shell am start -n com.ricebaby.video/.MainActivity
"/d/Files/AndroidSDK/platform-tools/adb.exe" shell uiautomator dump /sdcard/ui.xml
"/d/Files/AndroidSDK/platform-tools/adb.exe" exec-out cat /sdcard/ui.xml | grep -oE '首页|搜索|待看|直播|设置' | sort -u
```

预期恰好输出 5 个页签文本各一次。截图留档：`adb exec-out screencap -p > .debug-ui/m0-t1.png`（看完删除 `.debug-ui/`）。

- [ ] **Step 12: 提交**

```bash
git add -A
git commit -m "feat: M0 工程脚手架与五页签导航骨架（AGP 9.3.3 内置 Kotlin）"
git push -u origin feature/m0-scaffold
```

---

### Task 2: 站点实例模型与持久化（TDD）

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/data/model/SiteInstance.kt`、`data/settings/SiteInstanceCodec.kt`、`data/settings/AppSettings.kt`、`data/settings/SettingsRepository.kt`
- Test: `app/src/test/java/com/ricebaby/video/data/settings/SiteInstanceCodecTest.kt`

**Interfaces:**
- Consumes: Task 1 工程与依赖（okhttp、serialization）
- Produces:
  - `data class SiteInstance(val id: String, val name: String, val baseUrl: String)`（`@Serializable`）
  - `object SiteInstanceCodec { fun normalizeBaseUrl(raw: String): String?; fun encodeInstances(List<SiteInstance>): String; fun decodeInstances(String): List<SiteInstance>; fun encodePasswords(Map<String,String>): String; fun decodePasswords(String): Map<String,String> }`
  - `data class AppSettings(val instances: List<SiteInstance>, val activeInstanceId: String?, val passwords: Map<String,String>)`
  - `class SettingsRepository @Inject constructor(@ApplicationContext context) { val state: Flow<AppSettings>; suspend fun addInstance(name, baseUrlRaw): String?; suspend fun removeInstance(id); suspend fun setActive(id); suspend fun setPassword(id, password) }`（Task 4 消费）

- [ ] **Step 1: 写失败测试** `SiteInstanceCodecTest.kt`

```kotlin
package com.ricebaby.video.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteInstanceCodecTest {

    @Test
    fun `normalizeBaseUrl 补全协议并去掉尾部斜杠`() {
        assertEquals("https://a.com", SiteInstanceCodec.normalizeBaseUrl("a.com"))
        assertEquals("https://a.com/deploy", SiteInstanceCodec.normalizeBaseUrl("  https://a.com/deploy/  "))
        assertEquals("https://a.com", SiteInstanceCodec.normalizeBaseUrl("https://a.com/"))
    }

    @Test
    fun `normalizeBaseUrl 拒绝非法输入`() {
        assertNull(SiteInstanceCodec.normalizeBaseUrl(""))
        assertNull(SiteInstanceCodec.normalizeBaseUrl("ftp://a.com"))
        assertNull(SiteInstanceCodec.normalizeBaseUrl("not a url"))
    }

    @Test
    fun `实例列表编解码 round-trip`() {
        val list = listOf(SiteInstance("id-1", "主站", "https://a.com"), SiteInstance("id-2", "备用", "https://b.com"))
        assertEquals(list, SiteInstanceCodec.decodeInstances(SiteInstanceCodec.encodeInstances(list)))
    }

    @Test
    fun `解码脏数据回落空集合，绝不清空语义由调用方保留`() {
        assertEquals(emptyList<SiteInstance>(), SiteInstanceCodec.decodeInstances("not json"))
        assertEquals(emptyMap<String, String>(), SiteInstanceCodec.decodePasswords(""))
        val pw = mapOf("id-1" to "secret")
        assertEquals(pw, SiteInstanceCodec.decodePasswords(SiteInstanceCodec.encodePasswords(pw)))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest > ../rb-m0-t2fail.log 2>&1; echo EXIT:$?
```

预期 EXIT 非 0，日志含 `unresolved reference: SiteInstanceCodec`。

- [ ] **Step 3: 写 SiteInstance + Codec 实现**

`data/model/SiteInstance.kt`:

```kotlin
package com.ricebaby.video.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SiteInstance(val id: String, val name: String, val baseUrl: String)
```

`data/settings/SiteInstanceCodec.kt`:

```kotlin
package com.ricebaby.video.data.settings

import com.ricebaby.video.data.model.SiteInstance
import java.util.UUID
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SiteInstanceCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (
            trimmed.startsWith("http://") || trimmed.startsWith("https://")
        ) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return url.toString().removeSuffix("/")
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun encodeInstances(instances: List<SiteInstance>): String = json.encodeToString(instances)

    fun decodeInstances(raw: String): List<SiteInstance> = try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyList()
    }

    fun encodePasswords(passwords: Map<String, String>): String = json.encodeToString(passwords)

    fun decodePasswords(raw: String): Map<String, String> = try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyMap()
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest > ../rb-m0-t2pass.log 2>&1; echo EXIT:$?
```

预期 EXIT:0。（`normalizeBaseUrl("not a url")`：带 `https://` 前缀后含空格，toHttpUrlOrNull 返回 null。）

- [ ] **Step 5: 写 AppSettings + SettingsRepository**

`data/settings/AppSettings.kt`:

```kotlin
package com.ricebaby.video.data.settings

import com.ricebaby.video.data.model.SiteInstance

data class AppSettings(
    val instances: List<SiteInstance> = emptyList(),
    val activeInstanceId: String? = null,
    val passwords: Map<String, String> = emptyMap(),
) {
    val activeInstance: SiteInstance?
        get() = instances.firstOrNull { it.id == activeInstanceId }
}
```

`data/settings/SettingsRepository.kt`:

```kotlin
package com.ricebaby.video.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ricebaby.video.data.model.SiteInstance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ricebaby_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val state: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            instances = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty()),
            activeInstanceId = prefs[KEY_ACTIVE],
            passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()),
        )
    }

    suspend fun addInstance(name: String, baseUrlRaw: String): String? {
        val normalized = SiteInstanceCodec.normalizeBaseUrl(baseUrlRaw) ?: return null
        val id = SiteInstanceCodec.newId()
        val instance = SiteInstance(id, name.trim(), normalized)
        editSettings { prefs ->
            val current = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty())
            prefs[KEY_INSTANCES] = SiteInstanceCodec.encodeInstances(current + instance)
            if (prefs[KEY_ACTIVE] == null) prefs[KEY_ACTIVE] = id
        }
        return id
    }

    suspend fun removeInstance(id: String) {
        editSettings { prefs ->
            val current = SiteInstanceCodec.decodeInstances(prefs[KEY_INSTANCES].orEmpty())
            prefs[KEY_INSTANCES] = SiteInstanceCodec.encodeInstances(current.filterNot { it.id == id })
            val passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()).toMutableMap()
            passwords.remove(id)
            prefs[KEY_PASSWORDS] = SiteInstanceCodec.encodePasswords(passwords)
            if (prefs[KEY_ACTIVE] == id) prefs[KEY_ACTIVE] = current.filterNot { it.id == id }.firstOrNull()?.id
        }
    }

    suspend fun setActive(id: String) = editSettings { prefs -> prefs[KEY_ACTIVE] = id }

    suspend fun setPassword(id: String, password: String) {
        editSettings { prefs ->
            val passwords = SiteInstanceCodec.decodePasswords(prefs[KEY_PASSWORDS].orEmpty()).toMutableMap()
            if (password.isEmpty()) passwords.remove(id) else passwords[id] = password
            prefs[KEY_PASSWORDS] = SiteInstanceCodec.encodePasswords(passwords)
        }
    }

    private suspend fun editSettings(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    companion object {
        private val KEY_INSTANCES = stringPreferencesKey("instances")
        private val KEY_ACTIVE = stringPreferencesKey("active_instance_id")
        private val KEY_PASSWORDS = stringPreferencesKey("passwords")
    }
}
```

`@Singleton` 需补 import `javax.inject.Singleton`（与 `javax.inject.Inject` 并列）。

- [ ] **Step 6: 编译验证**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest > ../rb-m0-t2b.log 2>&1; echo EXIT:$?
```

预期 EXIT:0。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/ricebaby/video/data app/src/test/java/com/ricebaby/video/data
git commit -m "feat: 站点实例模型与 DataStore 持久化（含 URL 规范化与脏数据容错）"
```

---

### Task 3: 网络层 SiteClient（TDD + MockWebServer）

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/net/SiteClient.kt`
- Test: `app/src/test/java/com/ricebaby/video/net/SiteClientTest.kt`

**Interfaces:**
- Consumes: Task 1 的 okhttp/mockwebserver 依赖
- Produces:
  - `sealed interface ProbeOutcome { data class Reachable(val hasAuth: Boolean, val hasPremiumAuth: Boolean) : ProbeOutcome; data class Unreachable(val reason: String) : ProbeOutcome }`
  - `sealed interface LoginOutcome { data class Success(val name: String, val role: String, val profileId: String) : LoginOutcome; data object Rejected : LoginOutcome; data class Unreachable(val reason: String) : LoginOutcome }`
  - `class SiteClient @Inject constructor(http: OkHttpClient) { suspend fun probe(baseUrl: String): ProbeOutcome; suspend fun login(baseUrl: String, password: String): LoginOutcome }`（Task 4 消费）

**已核实的站点契约**（Ricebaby_Video `app/api/auth/route.ts`）：`GET /api/auth` → `{hasAuth, hasPremiumAuth, persistSession, ...}`；`POST /api/auth` body `{password}` → 成功 `{valid:true, name, role, profileId}` / 失败 `{valid:false}`（HTTP 200）。登录态在 Web 端存 localStorage 而非 Cookie——App 侧因此把校验结果视为「密码正确性 + profileId」，存原生（Task 4）。

- [ ] **Step 1: 写失败测试** `SiteClientTest.kt`

```kotlin
package com.ricebaby.video.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SiteClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SiteClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SiteClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `probe 读取 hasAuth 与 premium 标志`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"hasAuth":true,"hasPremiumAuth":false,"persistSession":true,"subscriptionSources":""}"""),
        )
        val outcome = client.probe(server.url("/").toString().removeSuffix("/"))
        assertEquals(ProbeOutcome.Reachable(hasAuth = true, hasPremiumAuth = false), outcome)
        assertEquals("/api/auth", server.takeRequest().path)
    }

    @Test
    fun `probe HTTP 错误判为不可达`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val outcome = client.probe(server.url("/").toString().removeSuffix("/"))
        assertTrue(outcome is ProbeOutcome.Unreachable)
    }

    @Test
    fun `login 成功返回身份与 profileId`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"valid":true,"name":"管理员","role":"super_admin","profileId":"abcd0123456789ef"}"""),
        )
        val outcome = client.login(server.url("/").toString().removeSuffix("/"), "hunter2")
        assertEquals(LoginOutcome.Success("管理员", "super_admin", "abcd0123456789ef"), outcome)
        val recorded = server.takeRequest()
        assertEquals("/api/auth", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("hunter2"))
    }

    @Test
    fun `login 密码错误判为 Rejected 而非异常`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"valid":false}"""))
        val outcome = client.login(server.url("/").toString().removeSuffix("/"), "wrong")
        assertEquals(LoginOutcome.Rejected, outcome)
    }

    @Test
    fun `服务器不可达时 probe 返回 Unreachable 不抛异常`() = runBlocking {
        val deadPort = server.port
        server.shutdown()
        val outcome = client.probe("http://127.0.0.1:$deadPort")
        assertTrue(outcome is ProbeOutcome.Unreachable)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest --tests "com.ricebaby.video.net.SiteClientTest" > ../rb-m0-t3fail.log 2>&1; echo EXIT:$?
```

预期 EXIT 非 0，`unresolved reference: SiteClient`。

- [ ] **Step 3: 写 `net/SiteClient.kt`**

```kotlin
package com.ricebaby.video.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

sealed interface ProbeOutcome {
    data class Reachable(val hasAuth: Boolean, val hasPremiumAuth: Boolean) : ProbeOutcome
    data class Unreachable(val reason: String) : ProbeOutcome
}

sealed interface LoginOutcome {
    data class Success(val name: String, val role: String, val profileId: String) : LoginOutcome
    data object Rejected : LoginOutcome
    data class Unreachable(val reason: String) : LoginOutcome
}

@Serializable
private data class AuthGetResponse(
    val hasAuth: Boolean = false,
    val hasPremiumAuth: Boolean = false,
)

@Serializable
private data class AuthPostRequest(val password: String)

@Serializable
private data class AuthPostResponse(
    val valid: Boolean = false,
    val name: String? = null,
    val role: String? = null,
    val profileId: String? = null,
)

class SiteClient @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun probe(baseUrl: String): ProbeOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/auth").get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ProbeOutcome.Unreachable("服务返回 HTTP ${response.code}")
                } else {
                    val body = json.decodeFromString<AuthGetResponse>(response.body?.string().orEmpty().ifBlank { "{}" })
                    ProbeOutcome.Reachable(hasAuth = body.hasAuth, hasPremiumAuth = body.hasPremiumAuth)
                }
            }
        } catch (_: IOException) {
            ProbeOutcome.Unreachable("无法连接站点")
        } catch (_: SerializationException) {
            ProbeOutcome.Unreachable("站点返回格式不符合预期")
        }
    }

    suspend fun login(baseUrl: String, password: String): LoginOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/auth")
            .post(json.encodeToString(AuthPostRequest(password)).toRequestBody(jsonMedia))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LoginOutcome.Unreachable("服务返回 HTTP ${response.code}")
                } else {
                    val body = json.decodeFromString<AuthPostResponse>(response.body?.string().orEmpty().ifBlank { "{}" })
                    when {
                        body.valid -> LoginOutcome.Success(
                            name = body.name.orEmpty(),
                            role = body.role.orEmpty(),
                            profileId = body.profileId.orEmpty(),
                        )
                        else -> LoginOutcome.Rejected
                    }
                }
            }
        } catch (_: IOException) {
            LoginOutcome.Unreachable("无法连接站点")
        } catch (_: SerializationException) {
            LoginOutcome.Unreachable("站点返回格式不符合预期")
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest > ../rb-m0-t3pass.log 2>&1; echo EXIT:$?
```

预期 EXIT:0，Task 2 与 Task 3 全部用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/ricebaby/video/net app/src/test/java/com/ricebaby/video/net
git commit -m "feat: 站点探活与密码校验客户端（契约对齐 /api/auth，异常全收敛为结果类型）"
```

---

### Task 4: Hilt 模块 + 设置页（实机验收）

**Files:**
- Create: `app/src/main/java/com/ricebaby/video/di/NetworkModule.kt`、`ui/settings/SettingsViewModel.kt`、`ui/settings/SettingsScreen.kt`
- Modify: `ui/AppRoot.kt`（settings 路由替换占位）

**Interfaces:**
- Consumes: Task 2 `SettingsRepository.state/addInstance/removeInstance/setActive/setPassword`；Task 3 `SiteClient.probe/login`
- Produces:
  - `@Provides @Singleton fun okHttpClient(): OkHttpClient`（后续里程碑 SSE 用独立 client，此处仅共享基础配置）
  - `enum class TestStatus { Idle, Testing, ReachableNoAuth, LoginOk, Rejected, NeedPassword, Unreachable }`（仅本任务 UI 消费）

- [ ] **Step 1: 写 `di/NetworkModule.kt`**

```kotlin
package com.ricebaby.video.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
```

- [ ] **Step 2: 写 `ui/settings/SettingsViewModel.kt`**

```kotlin
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
            t.copy(settings = settings, lastAddInvalidUrl = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addInstance(name: String, baseUrl: String) = viewModelScope.launch {
        val id = settingsRepository.addInstance(name, baseUrl)
        if (id == null) {
            transient.updateSelf { copy(lastAddInvalidUrl = true) }
        }
    }

    fun removeInstance(id: String) = viewModelScope.launch { settingsRepository.removeInstance(id) }

    fun selectInstance(id: String) = viewModelScope.launch { settingsRepository.setActive(id) }

    fun savePassword(id: String, password: String) = viewModelScope.launch {
        settingsRepository.setPassword(id, password)
    }

    fun testConnection(id: String) = viewModelScope.launch {
        val instance = settingsRepository.state.first().instances.firstOrNull { it.id == id } ?: return@launch
        setStatus(id, TestStatus.Testing, null)
        when (val probe = siteClient.probe(instance.baseUrl)) {
            is ProbeOutcome.Unreachable -> setStatus(id, TestStatus.Unreachable, probe.reason)
            is ProbeOutcome.Reachable -> {
                if (!probe.hasAuth) {
                    setStatus(id, TestStatus.ReachableNoAuth, null)
                } else {
                    val password = settingsRepository.state.first().passwords[id]
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
        transient.updateSelf {
            copy(
                testStatuses = testStatuses + (id to status),
                testDetails = if (detail != null) testDetails + (id to detail) else testDetails - id,
            )
        }
    }
}

private inline fun MutableStateFlow<SettingsUiState>.updateSelf(block: SettingsUiState.() -> SettingsUiState) {
    value = value.block()
}
```

- [ ] **Step 3: 写 `ui/settings/SettingsScreen.kt`**

```kotlin
package com.ricebaby.video.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextEditorDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricebaby.video.data.model.SiteInstance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var passwordTarget by remember { mutableStateOf<SiteInstance?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastAddInvalidUrl) {
        if (uiState.lastAddInvalidUrl) snackbarHostState.showSnackbar("地址无法识别，示例：video.example.com 或 https://video.example.com")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("添加站点") },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            Text("观影站点", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "连接后，搜索、播放与直播都会经由该站点完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.settings.instances.isEmpty()) {
                    item {
                        Text(
                            "还没有站点，点右下角「添加站点」开始。",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(uiState.settings.instances, key = { it.id }) { instance ->
                    InstanceCard(
                        instance = instance,
                        selected = uiState.settings.activeInstanceId == instance.id,
                        passwordSaved = uiState.settings.passwords.containsKey(instance.id),
                        status = uiState.testStatuses[instance.id] ?: TestStatus.Idle,
                        statusDetail = uiState.testDetails[instance.id],
                        onSelect = { viewModel.selectInstance(instance.id) },
                        onTest = { viewModel.testConnection(instance.id) },
                        onPassword = { passwordTarget = instance },
                        onDelete = { viewModel.removeInstance(instance.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddInstanceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addInstance(name, url)
                showAddDialog = false
            },
        )
    }

    passwordTarget?.let { instance ->
        PasswordDialog(
            instance = instance,
            onDismiss = { passwordTarget = null },
            onSave = { password ->
                viewModel.savePassword(instance.id, password)
                passwordTarget = null
            },
        )
    }
}

@Composable
private fun statusText(status: TestStatus, detail: String?): String = when (status) {
    TestStatus.Idle -> "未测试"
    TestStatus.Testing -> "测试中…"
    TestStatus.ReachableNoAuth -> "可连接 · 无需密码"
    TestStatus.LoginOk -> "密码有效" + if (!detail.isNullOrBlank()) "（$detail）" else ""
    TestStatus.Rejected -> "密码不正确"
    TestStatus.NeedPassword -> detail ?: "请先填写密码"
    TestStatus.Unreachable -> detail ?: "无法连接"
}

@Composable
private fun InstanceCard(
    instance: SiteInstance,
    selected: Boolean,
    passwordSaved: Boolean,
    status: TestStatus,
    statusDetail: String?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    instance.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    statusText(status, statusDetail) + if (passwordSaved) " · 已存密码" else " · 未存密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        TestStatus.LoginOk, TestStatus.ReachableNoAuth -> MaterialTheme.colorScheme.primary
                        TestStatus.Rejected, TestStatus.Unreachable -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column {
                FilledTonalButton(onClick = onTest, modifier = Modifier.padding(bottom = 4.dp)) { Text("测试") }
                TextButton(onClick = onPassword) { Text(if (passwordSaved) "改密码" else "存密码") }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("删除", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AddInstanceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("名称，如：主站") }, singleLine = true)
                TextField(value = url, onValueChange = { url = it }, label = { Text("站点地址") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "站点" }, url) },
                enabled = name.isNotBlank() || url.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PasswordDialog(instance: SiteInstance, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${instance.name} 的访问密码") },
        text = {
            TextField(value = password, onValueChange = { password = it }, label = { Text("留空表示清除") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(password) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

删掉多余的 `import androidx.compose.material3.TextEditorDefaults`（写作残留，不属于本文件功能）。

- [ ] **Step 4: 替换 AppRoot settings 路由**

`ui/AppRoot.kt` 中：

```kotlin
// 旧
composable("settings") { PlaceholderScreen("设置", "即将上线：站点管理") }
// 新
composable("settings") { SettingsScreen() }
```

并在 import 区加 `import com.ricebaby.video.ui.settings.SettingsScreen`。

- [ ] **Step 5: 构建 + 单测回归**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest > ../rb-m0-t4.log 2>&1; echo EXIT:$?
```

预期 EXIT:0。

- [ ] **Step 6: 实机验收设置页**

```bash
export MSYS_NO_PATHCONV=1
"/d/Files/AndroidSDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
"/d/Files/AndroidSDK/platform-tools/adb.exe" shell am start -n com.ricebaby.video/.MainActivity
```

验收路径（每步用 uiautomator dump 取 bounds 后点中心，禁止盲点坐标）：切「设置」→「添加站点」→ 填名称「主站」+ 地址 `ricebaby-video.pages.dev` → 保存 → 卡片出现且显示「未测试 · 未存密码」→ 点「测试」→ 等状态变化。截图存 `.debug-ui/m0-t4.png` 逐一 Read 检视排版（间距/溢出/对齐），看完删除目录。

判定标准：卡片与对话框文字不截断、不重叠；状态变化在 15s 内离开「测试中…」。模拟器到 pages.dev 不通则记录为「未验证（模拟器网络限制）」写入验收清单，不阻塞合并。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/ricebaby/video/di app/src/main/java/com/ricebaby/video/ui app/src/test
git commit -m "feat: 设置页站点管理——实例增删选、密码保存、连通与鉴权状态反馈"
```

---

### Task 5: M0 合并、推送与 tag

**Files:** 无新增（git 操作 + 验收记录）

**Interfaces:** 无

- [ ] **Step 1: 全量回归**

```bash
JAVA_HOME="D:/Apps/AndroidStudio/jbr" ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease > ../rb-m0-final.log 2>&1; echo EXIT:$?
```

预期 EXIT:0。release 构建验证签名无关配置可用（未配签名，产出 unsigned）。

- [ ] **Step 2: 清理临时物**

确认 `.debug-ui/`、日志文件（仓库外 `../rb-m0-*.log`）不留档在仓库内；`git status` 干净。

- [ ] **Step 3: ff-only 合入 dev 并推送**

```bash
git switch dev
git merge --ff-only feature/m0-scaffold
git push origin dev
```

- [ ] **Step 4: 合入 main、打 tag、推送**

```bash
git switch main
git merge --ff-only dev
git tag v0.0.1-M0
git push origin main
git push origin :dev 2>/dev/null; git push origin dev
git push origin v0.0.1-M0
```

（pages.dev 部署类远端不存在，`push :dev` 行仅为无副作用保险，可忽略输出。）

- [ ] **Step 5: 在 spec 追加验收记录**

向 `docs/superpowers/specs/2026-10-26-ricebabyvideo-android-design.md` 末尾追加：

```markdown
## 验收记录

- 2026-10-26 M0 完成（tag v0.0.1-M0）：脚手架/五页签/站点实例持久化/SiteClient 单测全绿；
  模拟器实测 <填入 Task4 Step6 实际结果，未验证项须显式写明原因>。
```

```bash
git add docs && git commit -m "docs: M0 验收记录" && git push origin main && git checkout dev && git merge --ff-only main && git push origin dev
```

---

## Self-Review 结论（已执行）

1. **Spec 覆盖**：M0 范围内四块（脚手架、实例+密码持久化、连通鉴权、五页签骨架）全部有任务承载；播放桥/搜索/豆瓣/IPTV 属 M1–M5，不在本计划——与 spec §10 切分一致。
2. **占位符扫描**：无 TBD；实机「未验证」分支有明确判定路径与记录去向（spec 验收节）。
3. **类型一致性**：`TestStatus/LoginOutcome/ProbeOutcome/AppSettings/SiteInstance` 在 Produces/Consumes 两端签名一致；Task 4 引用的 `addInstance→String?`、`state→Flow<AppSettings>` 与 Task 2 定义逐字对齐。
