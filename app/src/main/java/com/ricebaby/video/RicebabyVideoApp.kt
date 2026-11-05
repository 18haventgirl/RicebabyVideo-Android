package com.ricebaby.video

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ricebaby.video.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class RicebabyVideoApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var settingsRepository: SettingsRepository
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            // 筛选/翻页会整批换海报，内存缓存放大些，回到看过的条目直接命中
            .memoryCache {
                coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.30).build()
            }
            .build()
}
