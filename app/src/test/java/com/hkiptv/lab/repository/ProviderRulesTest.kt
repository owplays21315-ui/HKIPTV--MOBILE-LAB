package com.hkiptv.lab.repository

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.repository.ProviderRules
import org.junit.Assert.*
import org.junit.Test

class ProviderRulesTest {
    private fun provider(id: String, enabled: Boolean = true, primary: Boolean = false) = StoredProvider("https://example.invalid/$id/manifest.json", ProviderManifest(id, id, "1", endpoints = ProviderEndpoints(catalog = "catalog.json")), enabled, primary)
    @Test fun selectsOnlyRequestedPrimary() {
        val result = ProviderRules.setPrimary(listOf(provider("a", primary = true), provider("b")), "https://example.invalid/b/manifest.json")
        assertEquals("b", result.single { it.primary }.manifest.id)
    }
    @Test(expected = IllegalArgumentException::class) fun disabledProviderCannotBePrimary() { ProviderRules.setPrimary(listOf(provider("a", enabled = false)), "https://example.invalid/a/manifest.json") }
    @Test fun removingPrimaryPromotesFirstRemaining() {
        val result = ProviderRules.remove(listOf(provider("a", primary = true), provider("b")), "https://example.invalid/a/manifest.json")
        assertTrue(result.single().primary)
    }
}
