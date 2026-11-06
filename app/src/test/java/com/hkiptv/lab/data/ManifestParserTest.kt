package com.hkiptv.lab.data

import com.hkiptv.lab.data.remote.ManifestParser
import com.hkiptv.lab.data.remote.ManifestValidationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestParserTest {
    private val parser = ManifestParser()
    @Test fun parsesSyntheticManifest() {
        val result = parser.parse("""{"id":"synthetic","name":"Synthetic Provider","version":"1.0","resources":["catalog","sources"],"endpoints":{"catalog":"catalog.json","sources":"sources/{id}.json"}}""")
        assertEquals("Synthetic Provider", result.name)
        assertEquals(listOf("catalog", "sources"), result.resources)
    }
    @Test(expected = ManifestValidationException::class) fun rejectsMalformedJson() { parser.parse("{not-json}") }
    @Test(expected = ManifestValidationException::class) fun rejectsManifestWithoutEndpoints() { parser.parse("""{"id":"x","name":"X","version":"1","endpoints":{}}""") }
}
