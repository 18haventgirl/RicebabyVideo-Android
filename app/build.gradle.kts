import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// 签名密钥放在仓库之外，只在本地 signing.properties 里给出路径与口令；
// 文件不存在时（他人 clone、CI）直接跳过，release 仍可构建为未签名包。
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// 内置入口口令的 SHA-256（小写十六进制），只存摘要不存原文。
// 换口令：先算 `printf '%s' '新口令' | sha256sum`，把结果填到这里重新打包。
val unlockDigest = "c7f0c543779e344b0da5c4ffb5850ca1abc1baf49a1abf7a4e79348ed82b99c0"

android {
    namespace = "com.ricebaby.video"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ricebaby.video"
        minSdk = 26
        targetSdk = 37
        versionCode = 22
        versionName = "0.19.0-M19"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 站点地址内置在安装包里，新用户不必再手输；口令只存摘要，原文不进仓库。
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://ricebaby-video.pages.dev\"")
        buildConfigField("String", "UNLOCK_SHA256", "\"$unlockDigest\"")
    }

    signingConfigs {
        val storePath = signingProps.getProperty("RELEASE_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = signingProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
