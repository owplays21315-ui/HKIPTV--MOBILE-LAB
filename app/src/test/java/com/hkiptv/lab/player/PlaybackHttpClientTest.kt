package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.*
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class PlaybackHttpClientTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun mp4Http200() { assertSuccessful("/video.mp4", SourceFormat.MP4, fixture("sample-mp4.txt"), "video/mp4") }
    @Test fun hlsHttp200() { assertSuccessful("/master.m3u8", SourceFormat.HLS, fixture("sample.m3u8"), "application/vnd.apple.mpegurl") }
    @Test fun reportsHttp401() { assertStatus(401, PlaybackFailureCategory.AUTHENTICATION) }
    @Test fun reportsHttp403() { assertStatus(403, PlaybackFailureCategory.ACCESS_DENIED) }
    @Test fun reportsHttp404() { assertStatus(404, PlaybackFailureCategory.NOT_FOUND) }
    @Test fun reportsHttp429() { assertStatus(429, PlaybackFailureCategory.RATE_LIMIT) }
    @Test fun reportsHttp500() { assertStatus(500, PlaybackFailureCategory.SERVER) }

    @Test fun authorizedHeadersReachOriginalHost() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        execute(source(server.url("/media").toString(), headers = mapOf("Authorization" to "synthetic-secret", "Referer" to "https://example.invalid/")), PlaybackNetworkObserver())
        val request = server.takeRequest(); assertEquals("synthetic-secret", request.getHeader("Authorization")); assertEquals("https://example.invalid/", request.getHeader("Referer"))
    }

    @Test fun sameHostRedirectIsDetectedAndKeepsAuthorizedHeaders() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/final")))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val observer = PlaybackNetworkObserver(); execute(source(server.url("/start").toString(), headers = mapOf("Authorization" to "synthetic-secret")), observer)
        server.takeRequest(); assertEquals("synthetic-secret", server.takeRequest().getHeader("Authorization")); assertTrue(observer.snapshot().redirected); assertFalse(observer.snapshot().crossHostRedirect)
    }

    @Test fun crossHostRedirectDoesNotForwardProviderHeaders() {
        val second = MockWebServer(); second.start()
        try {
            val differentHostUrl = second.url("/final").toString().replace("localhost", "127.0.0.1")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", differentHostUrl)); second.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val observer = PlaybackNetworkObserver(); execute(source(server.url("/start").toString(), headers = mapOf("Authorization" to "synthetic-secret", "Cookie" to "session=synthetic")), observer)
            assertEquals("synthetic-secret", server.takeRequest().getHeader("Authorization")); val redirected = second.takeRequest(); assertNull(redirected.getHeader("Authorization")); assertNull(redirected.getHeader("Cookie")); assertTrue(observer.snapshot().crossHostRedirect)
        } finally { second.shutdown() }
    }

    @Test fun urlWithoutExtensionRemainsUnknownAndHasNoForcedMimeType() {
        assertEquals(SourceFormat.UNKNOWN, SourceFormatDetector.detect("https://example.invalid/watch/123?token=synthetic")); assertNull(PlaybackMimeType.forFormat(SourceFormat.UNKNOWN))
    }
    @Test fun extensionlessUrlUsesDeclaredHlsType() { assertEquals(SourceFormat.HLS, SourceFormatDetector.detect("https://example.invalid/watch/123", "application/vnd.apple.mpegurl")) }

    @Test fun connectionFailureIsClassifiedWithoutStatus() {
        val url = server.url("/offline").toString(); server.shutdown(); val observer = PlaybackNetworkObserver()
        try { execute(source(url), observer); fail("Era esperada falha de conexão") } catch (_: IOException) { }
        val diagnostic = PlaybackDiagnostics.snapshot(source(url), observer.snapshot(), "abertura")
        assertEquals(PlaybackFailureCategory.CONNECTION, diagnostic.category); assertNull(diagnostic.status)
    }

    private fun assertSuccessful(path: String, format: SourceFormat, body: String, contentType: String) {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body)); val observer = PlaybackNetworkObserver(); execute(source(server.url(path).toString(), format), observer); assertEquals(200, observer.snapshot().status)
    }
    private fun assertStatus(code: Int, category: PlaybackFailureCategory) {
        server.enqueue(MockResponse().setResponseCode(code)); val observer = PlaybackNetworkObserver(); execute(source(server.url("/media").toString()), observer); assertEquals(code, observer.snapshot().status); assertEquals(category, PlaybackDiagnostics.category(code))
    }
    private fun execute(source: MediaSource, observer: PlaybackNetworkObserver) { PlaybackHttpClient.create(source, observer).newCall(Request.Builder().url(source.url).build()).execute().use { } }
    private fun source(url: String, format: SourceFormat = SourceFormat.UNKNOWN, headers: Map<String, String> = emptyMap()) = MediaSource("id", "Synthetic", url, format, headers = headers, providerName = "Synthetic Provider")
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResource("fixtures/playback/$name")).readText()
}
