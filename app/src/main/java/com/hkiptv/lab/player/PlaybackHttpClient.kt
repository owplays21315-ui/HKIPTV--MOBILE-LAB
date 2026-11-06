package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.MediaSource
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.InetAddress
import java.net.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class PlaybackNetworkSnapshot(
    val initialStatus: Int? = null, val finalStatus: Int? = null, val redirectCount: Int = 0,
    val redirected: Boolean = false, val crossHostRedirect: Boolean = false, val redirectCompleted: Boolean = false,
    val connectionFailed: Boolean = false, val redirectFailure: RedirectFailure? = null, val finalContentType: String? = null
) { val status: Int? get() = finalStatus ?: initialStatus }

enum class RedirectFailure { INSECURE_PROTOCOL, PRIVATE_ADDRESS, MISSING_LOCATION, TOO_MANY_REDIRECTS }
data class PlaybackRedirectPolicy(val allowHttpForTests: Boolean = false, val allowPrivateForTests: Boolean = false, val maxRedirects: Int = 20)
class UnsafePlaybackRedirectException(val reason: RedirectFailure) : IOException("Redirecionamento bloqueado: $reason")

class PlaybackNetworkObserver {
    private val lock = Any()
    private var value = PlaybackNetworkSnapshot()
    internal fun response(code: Int, contentType: String?) = synchronized(lock) { value = value.copy(initialStatus = value.initialStatus ?: code, finalStatus = code, finalContentType = contentType ?: value.finalContentType, redirectCompleted = value.redirectCount > 0 && code !in 300..399) }
    internal fun redirect(crossHost: Boolean) = synchronized(lock) { value = value.copy(redirectCount = value.redirectCount + 1, redirected = true, crossHostRedirect = value.crossHostRedirect || crossHost, redirectCompleted = false) }
    internal fun redirectFailure(reason: RedirectFailure, status: Int?) = synchronized(lock) { value = value.copy(initialStatus = value.initialStatus ?: status, finalStatus = status ?: value.finalStatus, redirectFailure = reason, redirectCompleted = false) }
    internal fun connectionFailure(loop: Boolean = false) = synchronized(lock) { value = value.copy(connectionFailed = true, redirectFailure = if (loop) RedirectFailure.TOO_MANY_REDIRECTS else value.redirectFailure, redirectCompleted = false) }
    fun snapshot(): PlaybackNetworkSnapshot = synchronized(lock) { value }
}

object PlaybackHttpClient {
    private val sensitiveHeaders = setOf("authorization", "cookie", "proxy-authorization")

    fun create(source: MediaSource, observer: PlaybackNetworkObserver, policy: PlaybackRedirectPolicy = PlaybackRedirectPolicy(), configure: (OkHttpClient.Builder.() -> Unit)? = null): OkHttpClient {
        val original = source.url.toHttpUrl()
        val builder = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true).dns(SafePlaybackDns(original.host, policy, observer))
            .addNetworkInterceptor { chain ->
                val differentHost = !chain.request().url.host.equals(original.host, true)
                val requestBuilder = chain.request().newBuilder()
                source.headers.forEach { (name, value) -> if (!differentHost || name.lowercase() !in sensitiveHeaders) requestBuilder.header(name, value) else requestBuilder.removeHeader(name) }
                val response = chain.proceed(requestBuilder.build())
                observer.response(response.code, response.header("Content-Type")?.substringBefore(';')?.trim())
                if (response.code in 300..399) validateRedirect(response, observer, policy)
                response
            }
            .addInterceptor { chain ->
                try { chain.proceed(chain.request()) }
                catch (error: ProtocolException) { observer.connectionFailure(error.message?.contains("follow-up", true) == true); throw error }
                catch (error: Exception) { observer.connectionFailure(); throw error }
            }
        configure?.invoke(builder)
        return builder.build()
    }

    private fun validateRedirect(response: Response, observer: PlaybackNetworkObserver, policy: PlaybackRedirectPolicy) {
        val location = response.header("Location")
        if (location.isNullOrBlank()) { observer.redirectFailure(RedirectFailure.MISSING_LOCATION, response.code); return }
        val target = response.request.url.resolve(location)
        if (target == null) { observer.redirectFailure(RedirectFailure.MISSING_LOCATION, response.code); throw UnsafePlaybackRedirectException(RedirectFailure.MISSING_LOCATION) }
        if (target.scheme != "https" && !policy.allowHttpForTests) { observer.redirectFailure(RedirectFailure.INSECURE_PROTOCOL, response.code); throw UnsafePlaybackRedirectException(RedirectFailure.INSECURE_PROTOCOL) }
        if (!policy.allowPrivateForTests && target.host.isLiteralPrivateAddress()) { observer.redirectFailure(RedirectFailure.PRIVATE_ADDRESS, response.code); throw UnsafePlaybackRedirectException(RedirectFailure.PRIVATE_ADDRESS) }
        observer.redirect(!target.host.equals(response.request.url.host, true))
        if (observer.snapshot().redirectCount > policy.maxRedirects) { observer.redirectFailure(RedirectFailure.TOO_MANY_REDIRECTS, response.code); throw UnsafePlaybackRedirectException(RedirectFailure.TOO_MANY_REDIRECTS) }
    }
}

private class SafePlaybackDns(private val originalHost: String, private val policy: PlaybackRedirectPolicy, private val observer: PlaybackNetworkObserver) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (!policy.allowPrivateForTests && !hostname.equals(originalHost, true) && addresses.any(InetAddress::isPrivateOrLocal)) { observer.redirectFailure(RedirectFailure.PRIVATE_ADDRESS, observer.snapshot().finalStatus); throw UnknownHostException("Destino de redirecionamento privado bloqueado") }
        return addresses
    }
}

private fun String.isLiteralPrivateAddress(): Boolean = runCatching { if (any(Char::isLetter) && !contains(':')) false else InetAddress.getByName(this).isPrivateOrLocal() }.getOrDefault(false) || equals("localhost", true) || endsWith(".local", true)
private fun InetAddress.isPrivateOrLocal() = isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress
