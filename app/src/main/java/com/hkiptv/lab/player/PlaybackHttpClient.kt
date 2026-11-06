package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.MediaSource
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class PlaybackNetworkSnapshot(val status: Int?, val redirected: Boolean, val crossHostRedirect: Boolean, val connectionFailed: Boolean)

class PlaybackNetworkObserver {
    private val status = AtomicInteger(-1)
    private val redirected = AtomicBoolean(false)
    private val crossHost = AtomicBoolean(false)
    private val connectionFailed = AtomicBoolean(false)
    internal fun response(code: Int, didRedirect: Boolean, didCrossHost: Boolean) { status.set(code); redirected.set(didRedirect); crossHost.set(didCrossHost) }
    internal fun connectionFailure() { connectionFailed.set(true) }
    fun snapshot() = PlaybackNetworkSnapshot(status.get().takeIf { it >= 0 }, redirected.get(), crossHost.get(), connectionFailed.get())
}

object PlaybackHttpClient {
    fun create(source: MediaSource, observer: PlaybackNetworkObserver): OkHttpClient {
        val originalHost = source.url.toHttpUrl().host
        val providerHeaderNames = source.headers.keys.toList()
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val differentHost = !chain.request().url.host.equals(originalHost, true)
                val builder = chain.request().newBuilder()
                if (differentHost) providerHeaderNames.forEach(builder::removeHeader)
                else source.headers.forEach { (name, value) -> builder.header(name, value) }
                if (builder.build().header("User-Agent") == null) builder.header("User-Agent", "HK-IPTV-Mobile-Lab/0.1.0")
                chain.proceed(builder.build())
            }
            .addInterceptor { chain ->
                try {
                    val response = chain.proceed(chain.request())
                    var prior = response.priorResponse
                    var redirected = false
                    var crossHost = false
                    while (prior != null) {
                        redirected = true
                        if (!prior.request.url.host.equals(response.request.url.host, true)) crossHost = true
                        prior = prior.priorResponse
                    }
                    observer.response(response.code, redirected, crossHost)
                    response
                } catch (error: Exception) {
                    observer.connectionFailure(); throw error
                }
            }.build()
    }
}
