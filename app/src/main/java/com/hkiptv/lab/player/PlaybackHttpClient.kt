package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.MediaSource
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeUnit

data class PlaybackNetworkSnapshot(
    val initialStatus: Int? = null, val finalStatus: Int? = null,
    val initialProtocol: String? = null, val finalProtocol: String? = null,
    val redirectCount: Int = 0, val redirected: Boolean = false,
    val crossHostRedirect: Boolean = false, val downgradedToHttp: Boolean = false,
    val redirectCompleted: Boolean = false, val experimentalHttpEnabled: Boolean = false,
    val sensitiveHeadersWithheld: Boolean = false, val connectionFailed: Boolean = false,
    val redirectFailure: RedirectFailure? = null, val finalContentType: String? = null
) { val status: Int? get() = finalStatus ?: initialStatus }

enum class RedirectFailure { CLEARTEXT_DISABLED, PRIVATE_ADDRESS, UNSUPPORTED_SCHEME, MISSING_LOCATION, INVALID_LOCATION, TOO_MANY_REDIRECTS, DNS_REBINDING }
data class PlaybackRedirectPolicy(val experimentalHttpEnabled: Boolean = false, val allowPrivateForTests: Boolean = false, val privateHostsAllowedForTests: Set<String> = emptySet(), val maxRedirects: Int = 10, val dns: Dns = Dns.SYSTEM)
class UnsafePlaybackRedirectException(val reason: RedirectFailure) : IOException("Destino de reprodução bloqueado: $reason")

class PlaybackNetworkObserver {
    private val lock = Any()
    private var value = PlaybackNetworkSnapshot()
    internal fun start(protocol: String, experimental: Boolean) = synchronized(lock) { value = value.copy(initialProtocol = protocol, finalProtocol = protocol, experimentalHttpEnabled = experimental) }
    internal fun response(code: Int, protocol: String, contentType: String?) = synchronized(lock) { value = value.copy(initialStatus = value.initialStatus ?: code, finalStatus = code, finalProtocol = protocol, finalContentType = contentType ?: value.finalContentType, redirectCompleted = value.redirectCount > 0 && code !in 300..399) }
    internal fun redirect(crossHost: Boolean, downgrade: Boolean) = synchronized(lock) { value = value.copy(redirectCount = value.redirectCount + 1, redirected = true, crossHostRedirect = value.crossHostRedirect || crossHost, downgradedToHttp = value.downgradedToHttp || downgrade, redirectCompleted = false) }
    internal fun headersWithheld() = synchronized(lock) { value = value.copy(sensitiveHeadersWithheld = true) }
    internal fun redirectFailure(reason: RedirectFailure, status: Int?) = synchronized(lock) { value = value.copy(initialStatus = value.initialStatus ?: status, finalStatus = status ?: value.finalStatus, redirectFailure = reason, redirectCompleted = false) }
    internal fun connectionFailure(loop: Boolean = false) = synchronized(lock) { value = value.copy(connectionFailed = true, redirectFailure = if (loop) RedirectFailure.TOO_MANY_REDIRECTS else value.redirectFailure, redirectCompleted = false) }
    fun snapshot(): PlaybackNetworkSnapshot = synchronized(lock) { value }
}

object PlaybackHttpClient {
    fun create(source: MediaSource, observer: PlaybackNetworkObserver, policy: PlaybackRedirectPolicy = PlaybackRedirectPolicy(), configure: (OkHttpClient.Builder.() -> Unit)? = null): OkHttpClient {
        val original = source.url.toHttpUrl()
        observer.start(original.scheme, policy.experimentalHttpEnabled)
        val safeDns = SafePlaybackDns(policy, observer)
        val builder = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true).dns(safeDns)
            .addInterceptor { chain ->
                val scheme = chain.request().url.scheme
                val failure = when {
                    scheme !in setOf("http", "https") -> RedirectFailure.UNSUPPORTED_SCHEME
                    scheme == "http" && !policy.experimentalHttpEnabled -> RedirectFailure.CLEARTEXT_DISABLED
                    else -> null
                }
                if (failure != null) block(failure, null, observer)
                chain.proceed(chain.request())
            }
            .addNetworkInterceptor { chain ->
                val requestUrl = chain.request().url
                val crossHost = !requestUrl.host.equals(original.host, true)
                val downgrade = original.isHttps && !requestUrl.isHttps
                val requestBuilder = chain.request().newBuilder()
                source.headers.forEach { (name, value) ->
                    if ((crossHost || downgrade) && name.isSensitiveHeader()) { requestBuilder.removeHeader(name); observer.headersWithheld() }
                    else requestBuilder.header(name, value)
                }
                if (crossHost || downgrade) chain.request().headers.names().filter(String::isSensitiveHeader).forEach { requestBuilder.removeHeader(it); observer.headersWithheld() }
                val response = chain.proceed(requestBuilder.build())
                observer.response(response.code, requestUrl.scheme, response.header("Content-Type")?.substringBefore(';')?.trim())
                if (response.code in redirectCodes) validateRedirect(response, safeDns, observer, policy)
                response
            }
            .addInterceptor { chain ->
                try { chain.proceed(chain.request()) }
                catch (error: ProtocolException) { observer.connectionFailure(error.message?.contains("follow-up", true) == true); throw error }
                catch (error: Exception) { if (error !is UnsafePlaybackRedirectException) observer.connectionFailure(); throw error }
            }
        configure?.invoke(builder)
        return builder.build()
    }

    private fun validateRedirect(response: Response, dns: SafePlaybackDns, observer: PlaybackNetworkObserver, policy: PlaybackRedirectPolicy) {
        val location = response.header("Location")
        if (location.isNullOrBlank()) { observer.redirectFailure(RedirectFailure.MISSING_LOCATION, response.code); return }
        val target = response.request.url.resolve(location) ?: block(RedirectFailure.INVALID_LOCATION, response.code, observer)
        if (target.scheme !in setOf("http", "https")) block(RedirectFailure.UNSUPPORTED_SCHEME, response.code, observer)
        if (target.scheme == "http" && !policy.experimentalHttpEnabled) block(RedirectFailure.CLEARTEXT_DISABLED, response.code, observer)
        dns.validateRedirectTarget(target.host, response.code)
        observer.redirect(!target.host.equals(response.request.url.host, true), response.request.url.isHttps && !target.isHttps)
        if (observer.snapshot().redirectCount > policy.maxRedirects) block(RedirectFailure.TOO_MANY_REDIRECTS, response.code, observer)
    }

    private fun block(reason: RedirectFailure, status: Int?, observer: PlaybackNetworkObserver): Nothing {
        observer.redirectFailure(reason, status)
        throw UnsafePlaybackRedirectException(reason)
    }
    private val redirectCodes = setOf(301, 302, 303, 307, 308)
}

private class SafePlaybackDns(private val policy: PlaybackRedirectPolicy, private val observer: PlaybackNetworkObserver) : Dns {
    private val approved = mutableMapOf<String, Set<String>>()
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = policy.dns.lookup(hostname)
        recordAndCheck(hostname, addresses, observer.snapshot().finalStatus)
        validate(hostname, addresses, observer.snapshot().finalStatus)
        return addresses
    }
    fun validateRedirectTarget(hostname: String, status: Int?) {
        val addresses = policy.dns.lookup(hostname)
        recordAndCheck(hostname, addresses, status)
        validate(hostname, addresses, status)
    }
    private fun recordAndCheck(hostname: String, addresses: List<InetAddress>, status: Int?) = synchronized(approved) {
        val previous = approved[hostname]
        val current = addresses.map { it.hostAddress.orEmpty() }.toSet()
        if (!policy.allowPrivateForTests && previous != null && current.any { it !in previous }) {
            observer.redirectFailure(RedirectFailure.DNS_REBINDING, status)
            throw UnknownHostException("Mudança de resolução DNS bloqueada")
        }
        approved[hostname] = previous?.plus(current) ?: current
    }
    private fun validate(hostname: String, addresses: List<InetAddress>, status: Int?) {
        if (policy.allowPrivateForTests || policy.privateHostsAllowedForTests.any { it.equals(hostname, true) }) return
        if (hostname.isBlockedHostname() || addresses.isEmpty() || addresses.any(InetAddress::isBlockedPlaybackAddress)) {
            observer.redirectFailure(RedirectFailure.PRIVATE_ADDRESS, status)
            throw UnknownHostException("Destino local ou privado bloqueado")
        }
    }
}

private fun String.isSensitiveHeader(): Boolean {
    val value = lowercase()
    return value in setOf("authorization", "cookie", "cookie2", "proxy-authorization") || value.contains("token") ||
        value.contains("secret") || value.contains("credential") || value.contains("api-key") ||
        value.contains("apikey") || value.contains("session") || value.startsWith("x-auth-")
}
private fun String.isBlockedHostname() = equals("localhost", true) || endsWith(".localhost", true) || endsWith(".local", true) || equals("metadata.google.internal", true) || equals("metadata", true)
private fun InetAddress.isBlockedPlaybackAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) return true
    val bytes = address.map { it.toInt() and 0xff }
    if (this is Inet4Address) {
        if (bytes[0] == 100 && bytes[1] in 64..127) return true
        if (bytes[0] == 198 && bytes[1] in 18..19) return true
        if (bytes[0] == 192 && bytes[1] == 0 && bytes[2] == 0) return true
        if (bytes[0] >= 224 || bytes[0] == 0) return true
        if (hostAddress == "169.254.169.254" || hostAddress == "100.100.100.200") return true
    }
    if (this is Inet6Address && bytes.firstOrNull() in listOf(0xfc, 0xfd)) return true
    return false
}
