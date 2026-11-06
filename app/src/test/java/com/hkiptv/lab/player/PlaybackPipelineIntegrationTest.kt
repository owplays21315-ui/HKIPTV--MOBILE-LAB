package com.hkiptv.lab.player

import com.hkiptv.lab.data.local.LabSettingsStorage
import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.data.model.SourceFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackPipelineIntegrationTest {
    @Test fun persistedEnabledSettingBuildsFullPipelineAndSurvivesRecreationAndBackgroundReturn() = runTest {
        val settings = FakeSettingsStorage(false)
        settings.setExperimentalHttpPlayback(true)
        val tls = tlsSetup()
        val secure = secureServer(tls)
        val clear = MockWebServer().apply { start() }
        try {
            repeat(2) {
                secure.enqueue(redirect(clear.url("/movie.mp4").toString()))
                clear.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "video/mp4").setBody("mp4"))
                val observer = PlaybackNetworkObserver()
                val pipeline = pipeline(source(secure.url("/start").toString()), settings.snapshot(), observer, tls, testPolicy())
                assertNotNull(pipeline.dataSourceFactory)
                assertNotNull(pipeline.mediaSourceFactory)
                pipeline.httpClient.newCall(Request.Builder().url(secure.url("/start")).build()).execute().use { assertEquals(200, it.code) }
                assertTrue(observer.snapshot().experimentalHttpEnabled)
                assertTrue(observer.snapshot().downgradedToHttp)
            }
            assertTrue(settings.experimentalHttpPlayback.first())
        } finally { secure.shutdown(); clear.shutdown() }
    }

    @Test fun enabledSettingAllowsHttpsToHttpPartialMp4() = runTest {
        val settings = FakeSettingsStorage(true)
        val tls = tlsSetup(); val secure = secureServer(tls); val clear = MockWebServer().apply { start() }
        try {
            secure.enqueue(redirect(clear.url("/partial.mp4").toString()))
            clear.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Type", "video/mp4").setBody("partial"))
            val observer = PlaybackNetworkObserver()
            val pipeline = pipeline(source(secure.url("/start").toString()), settings.snapshot(), observer, tls, testPolicy())
            pipeline.httpClient.newCall(Request.Builder().url(secure.url("/start")).header("Range", "bytes=100-").build()).execute().use { assertEquals(206, it.code) }
            assertEquals("bytes=100-", clear.takeRequest().getHeader("Range"))
        } finally { secure.shutdown(); clear.shutdown() }
    }

    @Test fun disabledSettingBlocksHttpsToHttpAndReportsProtocolRule() = runTest {
        val settings = FakeSettingsStorage(false)
        val tls = tlsSetup(); val secure = secureServer(tls); val clear = MockWebServer().apply { start() }
        try {
            secure.enqueue(redirect(clear.url("/movie.mp4").toString()))
            val observer = PlaybackNetworkObserver()
            val pipeline = pipeline(source(secure.url("/start").toString()), settings.snapshot(), observer, tls, testPolicy())
            assertFails { pipeline.httpClient.newCall(Request.Builder().url(secure.url("/start")).build()).execute().use { } }
            assertEquals(RedirectFailure.CLEARTEXT_DISABLED, observer.snapshot().redirectFailure)
            val diagnostic = PlaybackDiagnostics.snapshot(source(secure.url("/start").toString()), observer.snapshot(), "abertura", failed = true)
            assertTrue(PlaybackDiagnostics.userMessage(diagnostic).contains("modo HTTP experimental"))
        } finally { secure.shutdown(); clear.shutdown() }
    }

    @Test fun httpsToHttpsMp4AndHlsRemainSupported() = runTest {
        val settings = FakeSettingsStorage(false)
        val tls = tlsSetup(); val secure = secureServer(tls)
        try {
            listOf(SourceFormat.MP4 to "video/mp4", SourceFormat.HLS to "application/vnd.apple.mpegurl").forEach { (format, mime) ->
                secure.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", mime).setBody("synthetic"))
                val media = source(secure.url("/media").toString(), format)
                val observer = PlaybackNetworkObserver()
                val pipeline = pipeline(media, settings.snapshot(), observer, tls, testPolicy())
                pipeline.httpClient.newCall(Request.Builder().url(secure.url("/media")).build()).execute().use { assertEquals(200, it.code) }
            }
        } finally { secure.shutdown() }
    }

    @Test fun playerPipelineWaitsUntilPersistedSettingIsLoaded() {
        assertNull(PlaybackPipelineFactory.create(source("https://example.invalid/media.mp4"), PlaybackSettingState(false, loaded = false), PlaybackNetworkObserver()))
    }

    @Test fun enabledModeStillBlocksPrivateRedirectDestination() = runTest {
        val settings = FakeSettingsStorage(true)
        val tls = tlsSetup(); val secure = secureServer(tls)
        try {
            secure.enqueue(redirect("https://10.0.0.1/private.mp4"))
            val observer = PlaybackNetworkObserver()
            val policy = PlaybackRedirectPolicy(privateHostsAllowedForTests = setOf("localhost"))
            val pipeline = pipeline(source(secure.url("/start").toString()), settings.snapshot(), observer, tls, policy)
            assertFails { pipeline.httpClient.newCall(Request.Builder().url(secure.url("/start")).build()).execute().use { } }
            assertEquals(RedirectFailure.PRIVATE_ADDRESS, observer.snapshot().redirectFailure)
            val diagnostic = PlaybackDiagnostics.snapshot(source(secure.url("/start").toString()), observer.snapshot(), "abertura", failed = true)
            assertTrue(PlaybackDiagnostics.userMessage(diagnostic).contains("privado ou local"))
        } finally { secure.shutdown() }
    }

    private suspend fun FakeSettingsStorage.snapshot() = PlaybackSettingState(experimentalHttpPlayback.first(), loaded = true)
    private fun pipeline(source: MediaSource, setting: PlaybackSettingState, observer: PlaybackNetworkObserver, tls: TlsSetup, policy: PlaybackRedirectPolicy) =
        requireNotNull(PlaybackPipelineFactory.create(source, setting, observer, policy, tls.configure))
    private fun source(url: String, format: SourceFormat = SourceFormat.MP4) = MediaSource("id", "Synthetic", url, format)
    private fun redirect(location: String) = MockResponse().setResponseCode(307).setHeader("Location", location)
    private fun testPolicy() = PlaybackRedirectPolicy(allowPrivateForTests = true)
    private fun assertFails(block: () -> Unit) { try { block(); fail("Era esperada uma falha") } catch (_: IOException) { } }

    private class FakeSettingsStorage(initial: Boolean) : LabSettingsStorage {
        private val value = MutableStateFlow(initial)
        override val experimentalHttpPlayback: Flow<Boolean> = value
        override suspend fun setExperimentalHttpPlayback(enabled: Boolean) { value.emit(enabled) }
    }

    private data class TlsSetup(val server: HandshakeCertificates, val configure: OkHttpClient.Builder.() -> Unit)
    private fun tlsSetup(): TlsSetup {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val client = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        return TlsSetup(serverCertificates) { sslSocketFactory(client.sslSocketFactory(), client.trustManager); hostnameVerifier { _, _ -> true } }
    }
    private fun secureServer(tls: TlsSetup) = MockWebServer().apply { useHttps(tls.server.sslSocketFactory(), false); start() }
}
