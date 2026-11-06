package com.hkiptv.lab.data

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class ProviderHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProviderHttpClient
    @Before fun setUp() { server = MockWebServer(); server.start(); client = ProviderHttpClient(allowHttpForTests = true) }
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
        val catalog = client.catalog(provider); val meta = client.metadata(provider, "movie", "tt0000001")
        assertEquals("Filme Sintético", catalog.items.single().title); assertEquals(2024, meta.year)
        assertEquals("/catalog/movie/popular.json", server.takeRequest().path); assertEquals("/meta/movie/tt0000001.json", server.takeRequest().path)
    }
    @Test fun parsesSeriesSeasonsAndEpisodes() = runTest {
        val provider = provider(fixture("native-catalog.json")); server.enqueue(response(fixture("series-meta.json")))
        val meta = client.metadata(provider, "series", "tt0000002")
        assertEquals(2, meta.seasons.size); assertEquals(2, meta.seasons.first().episodes.size); assertEquals("Piloto", meta.seasons.first().episodes.first().title)
    }
    @Test fun convertsStreamsAndPreservesHeadersAndQuality() = runTest {
        val provider = provider(fixture("native-stream-only.json")); server.enqueue(response(fixture("streams-response.json")))
        val sources = client.sources(provider, "movie", "tt0000001").sources
        assertEquals(2, sources.size); assertEquals(SourceFormat.HLS, sources.first().format); assertEquals("1080p", sources.first().quality); assertEquals("https://example.invalid/", sources.first().headers["Referer"]); assertFalse(sources.first().verified)
    }

    private fun provider(manifestJson: String) = StoredProvider(server.url("/manifest.json").toString(), ManifestParser().parse(manifestJson))
    private fun response(body: String, contentType: String = "application/json") = MockResponse().setResponseCode(200).setHeader("Content-Type", contentType).setBody(body)
}
