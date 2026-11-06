package com.hkiptv.lab.data

import com.hkiptv.lab.data.remote.ManifestUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestUrlResolverTest {
    @Test fun directManifestHasSingleCandidate() { assertEquals(listOf("https://provider.invalid/config/manifest.json?token=x"), ManifestUrlResolver.candidates("https://provider.invalid/config/manifest.json?token=x")) }
    @Test fun rootAddsManifestCandidate() { assertEquals("https://provider.invalid/manifest.json", ManifestUrlResolver.candidates("https://provider.invalid/")[1]) }
    @Test fun configuredRootPreservesQuery() { assertEquals("https://provider.invalid/user/config/manifest.json?token=x", ManifestUrlResolver.candidates("https://provider.invalid/user/config?token=x")[1]) }
    @Test fun routePreservesConfigurationQuery() { assertEquals("https://provider.invalid/user/catalog/movie/a.json?token=x", ManifestUrlResolver.route("https://provider.invalid/user/manifest.json?token=x", "catalog/movie/a.json")) }
    @Test fun routeDoesNotDoubleEncodeNamespacedIdentifier() { assertEquals("https://provider.invalid/meta/movie/tmdb%3A559.json", ManifestUrlResolver.route("https://provider.invalid/manifest.json", "meta/movie/tmdb%3A559.json")) }
}
