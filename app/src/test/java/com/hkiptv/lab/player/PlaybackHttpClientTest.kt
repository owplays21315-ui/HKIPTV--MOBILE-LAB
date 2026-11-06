package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class PlaybackHttpClientTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun mp4DirectHttp200() { assertSuccessful("/video.mp4", SourceFormat.MP4, fixture("sample-mp4.txt"), "video/mp4") }
    @Test fun hlsHttp200WithoutRegression() { assertSuccessful("/master.m3u8", SourceFormat.HLS, fixture("sample.m3u8"), "application/vnd.apple.mpegurl") }

    @Test fun mp4Http307Then200() {
        server.enqueue(redirect(server.url("/final.mp4").toString())); server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody("mp4"))
        val observer = PlaybackNetworkObserver(); execute(source(server.url("/start.mp4").toString(), SourceFormat.MP4), observer, testPolicy())
        assertRedirect(observer.snapshot(), 307, 200, 1, completed = true)
    }

    @Test fun mp4Http307Then206PreservesGetAndRange() {
        server.enqueue(redirect(server.url("/partial.mp4").toString())); server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Type", "video/mp4").setBody("partial"))
        val observer = PlaybackNetworkObserver(); execute(source(server.url("/start.mp4").toString(), SourceFormat.MP4), observer, testPolicy(), Request.Builder().url(server.url("/start.mp4")).header("Range", "bytes=100-").get().build())
        val first = server.takeRequest(); val second = server.takeRequest(); assertEquals("GET", first.method); assertEquals("GET", second.method); assertEquals("bytes=100-", second.getHeader("Range")); assertRedirect(observer.snapshot(), 307, 206, 1, true)
    }

    @Test fun crossHostHttpsRedirectIsFollowedWithoutSensitiveHeaders() {
        val tls = tlsSetup(); val second = MockWebServer(); server.shutdown(); server = MockWebServer().apply { useHttps(tls.server.sslSocketFactory(), false); start() }; second.useHttps(tls.server.sslSocketFactory(), false); second.start()
        try {
            val target = second.url("/final.mp4").toString().replace("localhost", "127.0.0.1")
            server.enqueue(redirect(target)); second.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody("ok"))
            val media = source(server.url("/start.mp4").toString(), SourceFormat.MP4, mapOf("Authorization" to "synthetic-secret", "Cookie" to "synthetic-cookie", "Referer" to "https://example.invalid/"))
            val observer = PlaybackNetworkObserver(); execute(media, observer, PlaybackRedirectPolicy(allowPrivateForTests = true), configure = tls.configure)
            val initial = server.takeRequest(); val redirected = second.takeRequest(); assertEquals("synthetic-secret", initial.getHeader("Authorization")); assertNull(redirected.getHeader("Authorization")); assertNull(redirected.getHeader("Cookie")); assertEquals("https://example.invalid/", redirected.getHeader("Referer")); assertTrue(observer.snapshot().crossHostRedirect); assertTrue(observer.snapshot().redirectCompleted)
        } finally { second.shutdown() }
    }

    @Test fun circularRedirectIsLimited() {
        repeat(8) { server.enqueue(redirect(server.url("/loop").toString())) }
        val observer = PlaybackNetworkObserver(); assertFails { execute(source(server.url("/loop").toString()), observer, testPolicy(maxRedirects = 3)) }
        assertEquals(RedirectFailure.TOO_MANY_REDIRECTS, observer.snapshot().redirectFailure); assertEquals(4, observer.snapshot().redirectCount)
    }

    @Test fun redirectWithoutLocationIsDiagnosed() {
        server.enqueue(MockResponse().setResponseCode(307)); val observer = PlaybackNetworkObserver(); execute(source(server.url("/media").toString()), observer, testPolicy())
        assertEquals(RedirectFailure.MISSING_LOCATION, observer.snapshot().redirectFailure); assertEquals(307, observer.snapshot().finalStatus); assertFalse(observer.snapshot().redirectCompleted)
    }

    @Test fun insecureHttpRedirectIsBlocked() {
        server.enqueue(redirect("http://example.invalid/media.mp4")); val observer = PlaybackNetworkObserver(); assertFails { execute(source(server.url("/start").toString()), observer) }
        assertEquals(RedirectFailure.INSECURE_PROTOCOL, observer.snapshot().redirectFailure)
    }

    @Test fun privateAddressRedirectIsBlocked() {
        server.enqueue(redirect("https://127.0.0.1/private.mp4")); val observer = PlaybackNetworkObserver(); assertFails { execute(source(server.url("/start").toString()), observer) }
        assertEquals(RedirectFailure.PRIVATE_ADDRESS, observer.snapshot().redirectFailure)
    }

    @Test fun signedRedirectUrlIsPreserved() {
        val signed = server.url("/final.mp4").newBuilder().encodedQuery("token=a%2Bb%3D&expires=123").build()
        server.enqueue(redirect(signed.toString())); server.enqueue(MockResponse().setResponseCode(200).setBody("ok")); execute(source(server.url("/start").toString()), PlaybackNetworkObserver(), testPolicy())
        server.takeRequest(); assertEquals("/final.mp4?token=a%2Bb%3D&expires=123", server.takeRequest().path)
    }

    @Test fun http401AfterRedirect() { assertStatusAfterRedirect(401, PlaybackFailureCategory.AUTHENTICATION) }
    @Test fun http403AfterRedirect() { assertStatusAfterRedirect(403, PlaybackFailureCategory.ACCESS_DENIED) }
    @Test fun http404AfterRedirect() { assertStatusAfterRedirect(404, PlaybackFailureCategory.NOT_FOUND) }
    @Test fun urlWithoutExtensionIsNotForcedToMp4() { assertEquals(SourceFormat.UNKNOWN, SourceFormatDetector.detect("https://example.invalid/watch/123?token=synthetic")); assertNull(PlaybackMimeType.forFormat(SourceFormat.UNKNOWN)) }
    @Test fun extensionlessUrlUsesDeclaredHlsType() { assertEquals(SourceFormat.HLS, SourceFormatDetector.detect("https://example.invalid/watch/123", "application/vnd.apple.mpegurl")) }

    @Test fun connectionFailureHasNoStatus() {
        val url = server.url("/offline").toString(); server.shutdown(); val observer = PlaybackNetworkObserver(); assertFails { execute(source(url), observer) }
        val diagnostic = PlaybackDiagnostics.snapshot(source(url), observer.snapshot(), "abertura", failed = true); assertEquals(PlaybackFailureCategory.CONNECTION, diagnostic.category); assertNull(diagnostic.status)
    }

    private fun assertSuccessful(path: String, format: SourceFormat, body: String, contentType: String) { server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body)); val observer = PlaybackNetworkObserver(); execute(source(server.url(path).toString(), format), observer); assertEquals(200, observer.snapshot().finalStatus) }
    private fun assertStatusAfterRedirect(code: Int, category: PlaybackFailureCategory) { server.enqueue(redirect(server.url("/final").toString())); server.enqueue(MockResponse().setResponseCode(code)); val observer = PlaybackNetworkObserver(); execute(source(server.url("/start").toString()), observer, testPolicy()); val snapshot = observer.snapshot(); assertEquals(307, snapshot.initialStatus); assertEquals(code, snapshot.finalStatus); assertEquals(category, PlaybackDiagnostics.category(code)) }
    private fun assertRedirect(value: PlaybackNetworkSnapshot, initial: Int, final: Int, count: Int, completed: Boolean) { assertEquals(initial, value.initialStatus); assertEquals(final, value.finalStatus); assertEquals(count, value.redirectCount); assertEquals(completed, value.redirectCompleted) }
    private fun redirect(location: String) = MockResponse().setResponseCode(307).setHeader("Location", location)
    private fun testPolicy(maxRedirects: Int = 20) = PlaybackRedirectPolicy(allowHttpForTests = true, allowPrivateForTests = true, maxRedirects = maxRedirects)
    private fun execute(source: MediaSource, observer: PlaybackNetworkObserver, policy: PlaybackRedirectPolicy = PlaybackRedirectPolicy(), request: Request = Request.Builder().url(source.url).get().build(), configure: (OkHttpClient.Builder.() -> Unit)? = null) { PlaybackHttpClient.create(source, observer, policy, configure).newCall(request).execute().use { } }
    private fun source(url: String, format: SourceFormat = SourceFormat.UNKNOWN, headers: Map<String, String> = emptyMap()) = MediaSource("id", "Synthetic", url, format, headers = headers, providerName = "Synthetic Provider")
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResource("fixtures/playback/$name")).readText()
    private fun assertFails(block: () -> Unit) { try { block(); fail("Era esperada uma falha") } catch (_: IOException) { } }
    private data class TlsSetup(val server: HandshakeCertificates, val configure: OkHttpClient.Builder.() -> Unit)
    private fun tlsSetup(): TlsSetup { val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build(); val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build(); val client = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build(); return TlsSetup(serverCertificates) { sslSocketFactory(client.sslSocketFactory(), client.trustManager); hostnameVerifier { _, _ -> true } } }
}
