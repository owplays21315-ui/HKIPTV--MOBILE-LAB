package com.hkiptv.lab.data

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.ProviderHttpClient
import com.hkiptv.lab.data.remote.ProviderNetworkException
import com.hkiptv.lab.data.remote.protocolJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolStorageTest {
    @Test fun storedProvidersRoundTrip() {
        val original = listOf(StoredProvider("https://example.invalid/manifest.json", ProviderManifest("id", "Test", "1", resources = listOf("catalog"), endpoints = ProviderEndpoints(catalog = "catalog.json")), primary = true))
        val decoded = protocolJson.decodeFromString<List<StoredProvider>>(protocolJson.encodeToString(original))
        assertEquals(original, decoded)
    }
    @Test(expected = ProviderNetworkException::class) fun rejectsInsecureManifestUrl() = runTest { ProviderHttpClient().fetchManifest("http://example.invalid/manifest.json") }
}
