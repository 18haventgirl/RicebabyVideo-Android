package com.ricebaby.video.player

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 播放器网络层复用 App 的 OkHttpClient，继承其代理设置；
 * 流媒体首包常经站点代理转发到采集站，超时需比 API 请求更宽松。
 */
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    fun create(): ExoPlayer {
        val streamingClient = okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val dataSource = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(streamingClient),
        )
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
    }
}
