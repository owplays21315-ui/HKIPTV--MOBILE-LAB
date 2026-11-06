package com.hkiptv.lab.data

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.protocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolStorageTest {
    @Test fun storedNativeProviderRoundTrips() {
        val original = listOf(StoredProvider("https://example.invalid/manifest.json", ProviderManifest("id", "Test", "1", resources = listOf("stream"), protocol = ProviderProtocol.NATIVE_ADDON, types = listOf("movie")), primary = true))
        assertEquals(original, protocolJson.decodeFromString<List<StoredProvider>>(protocolJson.encodeToString(original)))
    }
}
