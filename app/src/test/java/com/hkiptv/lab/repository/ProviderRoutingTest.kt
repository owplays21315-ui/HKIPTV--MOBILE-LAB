package com.hkiptv.lab.repository

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.ManifestParser
import com.hkiptv.lab.data.fixture
import com.hkiptv.lab.data.repository.ProviderRouting
import org.junit.Assert.*
import org.junit.Test

class ProviderRoutingTest {
    @Test fun catalogAndStreamsCanUseSeparateProviders() {
        val catalog = provider("native-catalog-meta-only.json", primary = true)
        val streams = provider("native-stream-only.json")
        val item = MediaItem("tmdb:559", "Filme", type = "movie", identifiers = mapOf("imdb" to "tt0413300"))
        val target = ProviderRouting.targets(listOf(catalog, streams), "stream", item).single()
        assertEquals("Native Streams", target.provider.manifest.name); assertEquals("tt0413300", target.identifier)
    }
    @Test fun recognizesImdbIdentifier() { assertEquals(IdentifierKind.IMDB, ProviderRouting.identifierKind("tt0413300")) }
    @Test fun recognizesTmdbIdentifier() { assertEquals(IdentifierKind.TMDB, ProviderRouting.identifierKind("tmdb:559")) }
    @Test fun rejectsIdentifierIncompatibleWithProvider() {
        val streams = provider("native-stream-only.json")
        assertTrue(ProviderRouting.targets(listOf(streams), "stream", MediaItem("tmdb:559", "Filme", type = "movie")).isEmpty())
    }
    @Test fun providerSpecificResourceRulesOverrideTopLevelDefaults() {
        val manifest = ManifestParser().parse("""{"id":"rules","name":"Rules","version":"1","resources":[{"name":"stream","types":["series"],"idPrefixes":["tmdb:"]}],"types":["movie"],"idPrefixes":["tt"]}""")
        val provider = StoredProvider("https://example.invalid/manifest.json", manifest)
        assertTrue(ProviderRouting.supports(provider, "stream", "series", "tmdb:20")); assertFalse(ProviderRouting.supports(provider, "stream", "movie", "tt20"))
    }
    @Test fun primaryProviderWithoutStreamDoesNotBlockStreamProvider() {
        val targets = ProviderRouting.targets(listOf(provider("native-catalog-meta-only.json", true), provider("native-stream-only.json")), "stream", MediaItem("tt0413300", "Filme", type = "movie"))
        assertEquals("Native Streams", targets.single().provider.manifest.name)
    }
    @Test fun episodeIdentifierIsPreservedExactly() {
        val streams = provider("native-stream-only.json")
        assertEquals("tt1234567:2:3", ProviderRouting.targets(listOf(streams), "stream", MediaItem("tt1234567:2:3", "Episódio", type = "series")).single().identifier)
    }
    private fun provider(fixtureName: String, primary: Boolean = false) = StoredProvider("https://example.invalid/manifest.json", ManifestParser().parse(fixture(fixtureName)), primary = primary)
}
