package com.ricebaby.video.net

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** OkHttp 每次请求都会询问 selector，因此可在运行期热切换代理而无需重建 client */
@Singleton
class AppProxySelector @Inject constructor() : ProxySelector() {
    @Volatile
    private var proxy: Proxy = Proxy.NO_PROXY

    fun update(host: String, port: Int) {
        proxy = if (host.isNotBlank() && port > 0) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        } else {
            Proxy.NO_PROXY
        }
    }

    override fun select(uri: URI): List<Proxy> = listOf(proxy)

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException?) {
        // 单代理无备选路径，交由 OkHttp 向调用方抛出连接错误
    }
}
