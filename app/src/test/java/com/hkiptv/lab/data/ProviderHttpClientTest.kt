package com.hkiptv.lab.data

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.*
import com.hkiptv.lab.data.repository.ProviderRouting
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class ProviderHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProviderHttpClient
    @Before fun setUp() { server = MockWebServer(); server.start(); client = ProviderHttpClient(allowHttpForTests = true, diagnostics = DiagnosticLogger { }) }
    @After fun tearDown() { server.shutdown() }

    @Test fun rootUrlFallsBackAfterHtmlAndPreservesQuery() = runTest {
        server.enqueue(response("<html>site</html>", "text/html")); server.enqueue(response(fixture("native-stream-only.json")))
        val result = client.fetchManifest(server.url("/?config=synthetic").toString())
        assertEquals(ProviderProtocol.NATIVE_ADDON, result.manifest.protocol)
        assertEquals("/manifest.json?config=synthetic", server.takeRequest().let { server.takeRequest().path })
    }
    @Test fun directManifestDoesNotNeedFallback() = runTest {
        server.enqueue(response(fixture("native-catalog.json")))
        val result = client.fetchManifest(server.url("/manifest.json").toString())
        assertEquals("Native Catalog", result.manifest.name); assertEquals(1, server.requestCount)
    }
    @Test(expected = AccessDeniedException::class) fun reportsHttp403() = runTest {
        server.enqueue(MockResponse().setResponseCode(403)); client.fetchManifest(server.url("/manifest.json").toString())
    }
    @Test(expected = MalformedJsonException::class) fun reportsMalformedJson() = runTest {
        server.enqueue(response("{broken")); client.fetchManifest(server.url("/manifest.json").toString())
    }
    @Test(expected = UnexpectedHtmlException::class) fun reportsUnexpectedHtmlForDirectUrl() = runTest {
        server.enqueue(response("<!doctype html><title>Site</title>", "text/html")); client.fetchManifest(server.url("/manifest.json").toString())
    }
    @Test fun parsesCatalogAndMovieMetadata() = runTest {
        val provider = provider(fixture("native-catalog.json")); server.enqueue(response(fixture("catalog-response.json"))); server.enqueue(response(fixture("movie-meta.json")))
        val catalog = client.catalog(provider); val meta = client.metadata(provider, catalog.items.single())
        assertEquals("Filme Sintético", catalog.items.single().title); assertEquals(2024, meta.year)
        assertEquals("/catalog/movie/popular.json", server.takeRequest().path); assertEquals("/meta/movie/tt0000001.json", server.takeRequest().path)
    }
    @Test fun parsesSeriesSeasonsAndEpisodes() = runTest {
        val provider = provider(fixture("native-catalog.json")); server.enqueue(response(fixture("series-meta.json")))
        val meta = client.metadata(provider, MediaItem("tt0000002", "Série Sintética", type = "series"))
        assertEquals(2, meta.seasons.size); assertEquals(2, meta.seasons.first().episodes.size); assertEquals("Piloto", meta.seasons.first().episodes.first().title)
    }
    @Test fun convertsStreamsAndPreservesHeadersAndQuality() = runTest {
        val provider = provider(fixture("native-stream-only.json")); server.enqueue(response(fixture("streams-response.json")))
        val sources = client.sources(provider, "movie", "tt0000001").sources
        assertEquals(2, sources.size); assertEquals(SourceFormat.HLS, sources.first().format); assertEquals("1080p", sources.first().quality); assertEquals("https://example.invalid/", sources.first().headers["Referer"]); assertFalse(sources.first().verified)
    }
    @Test fun preservesCatalogIdentityWhenWrappedMetadataIsPartial() = runTest {
        val provider = provider(fixture("native-catalog.json")); server.enqueue(response(fixture("wrapped-partial-meta.json")))
        val catalogItem = MediaItem("tmdb:559", "Spider Synthetic 3", type = "movie")
        val meta = client.metadata(provider, catalogItem)
        assertEquals("tmdb:559", meta.id); assertEquals("movie", meta.type); assertEquals("Spider Synthetic 3", meta.title)
        assertEquals("tt0413300", meta.identifiers["imdb"]); assertEquals("tmdb:559", meta.identifiers["tmdb"])
    }
    @Test fun reportsHttp400WithSanitizedContext() = runTest {
        val events = mutableListOf<DiagnosticEvent>()
        val loggingClient = ProviderHttpClient(allowHttpForTests = true, diagnostics = DiagnosticLogger(events::add))
        val provider = provider(fixture("native-stream-only.json")); server.enqueue(MockResponse().setResponseCode(400))
        val error = runCatching { loggingClient.sources(provider, "movie", "tt0000001") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("HTTP 400")); assertTrue(error?.message.orEmpty().contains("IMDb"))
        assertEquals(400, events.last().status); assertFalse(events.toString().contains("example.invalid"))
    }
    @Test fun emptyStreamsIsSuccessfulEmptyResult() = runTest {
        val provider = provider(fixture("native-stream-only.json")); server.enqueue(response("""{"streams":[]}"""))
        assertTrue(client.sources(provider, "movie", "tt0000001").sources.isEmpty())
    }
    @Test fun catalogMetadataAndStreamsInteroperateAcrossProviders() = runTest {
        val catalogProvider = StoredProvider(server.url("/catalog/manifest.json").toString(), ManifestParser().parse(fixture("native-catalog-meta-only.json")), primary = true)
        val streamProvider = StoredProvider(server.url("/streams/manifest.json").toString(), ManifestParser().parse(fixture("native-stream-only.json")))
        server.enqueue(response(fixture("wrapped-partial-meta.json"))); server.enqueue(response(fixture("streams-response.json")))
        val catalogItem = MediaItem("tmdb:559", "Spider Synthetic 3", type = "movie")
        val enriched = client.metadata(catalogProvider, catalogItem)
        val target = ProviderRouting.targets(listOf(catalogProvider, streamProvider), "stream", enriched).single()
        val sources = client.sources(target.provider, enriched.type, target.identifier).sources
        assertEquals("tt0413300", target.identifier); assertEquals(2, sources.size)
        assertEquals("/catalog/meta/movie/tmdb%3A559.json", server.takeRequest().path)
        assertEquals("/streams/stream/movie/tt0413300.json", server.takeRequest().path)
    }

    private fun provider(manifestJson: String) = StoredProvider(server.url("/manifest.json").toString(), ManifestParser().parse(manifestJson))
    private fun response(body: String, contentType: String = "application/json") = MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body)
}
