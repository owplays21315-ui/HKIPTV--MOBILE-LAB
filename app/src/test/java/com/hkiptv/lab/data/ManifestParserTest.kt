package com.hkiptv.lab.data

import com.hkiptv.lab.data.model.ProviderProtocol
import com.hkiptv.lab.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class ManifestParserTest {
    private val parser = ManifestParser()
    @Test fun parsesOriginalProtocol() { assertEquals(ProviderProtocol.HK, parser.parse(fixture("hk-manifest.json")).protocol) }
    @Test fun parsesNativeCatalogManifest() {
        val manifest = parser.parse(fixture("native-catalog.json"))
        assertEquals(ProviderProtocol.NATIVE_ADDON, manifest.protocol)
        assertEquals(listOf("movie", "series"), manifest.types)
        assertEquals(2, manifest.catalogs.size)
        assertTrue(manifest.offersCatalog); assertTrue(manifest.offersMetadata); assertTrue(manifest.offersSources)
    }
    @Test fun parsesStreamOnlyProvider() {
        val manifest = parser.parse(fixture("native-stream-only.json"))
        assertFalse(manifest.offersCatalog); assertFalse(manifest.offersMetadata); assertTrue(manifest.offersSources)
    }
    @Test fun acceptsObjectResources() {
        val manifest = parser.parse("""{"id":"object.resources","name":"Objects","version":"1","resources":[{"name":"stream","types":["movie"]}],"types":["movie"]}""")
        assertEquals(listOf("stream"), manifest.resources)
    }
    @Test(expected = MalformedJsonException::class) fun rejectsMalformedJsonSpecifically() { parser.parse("{not-json}") }
    @Test(expected = UnsupportedProtocolException::class) fun rejectsUnsupportedProtocolSpecifically() { parser.parse("""{"id":"x","name":"X","version":"1"}""") }
    @Test(expected = MissingManifestFieldException::class) fun reportsMissingRequiredField() { parser.parse("""{"name":"X","version":"1","resources":["stream"],"types":["movie"]}""") }
}

internal fun fixture(name: String): String = checkNotNull(object {}.javaClass.classLoader?.getResource("fixtures/$name")) { "Fixture ausente: $name" }.readText()
