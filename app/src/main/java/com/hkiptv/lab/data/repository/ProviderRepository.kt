package com.hkiptv.lab.data.repository

import com.hkiptv.lab.data.local.ProviderStorage
import com.hkiptv.lab.data.model.StoredProvider
import com.hkiptv.lab.data.remote.ProviderHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProviderRepository(private val storage: ProviderStorage, private val http: ProviderHttpClient) {
    val providers: Flow<List<StoredProvider>> = storage.providers

    suspend fun add(url: String) {
        val normalized = url.trim()
        val current = providers.first()
        require(current.none { it.manifestUrl == normalized }) { "Este manifest já foi adicionado" }
        val fetched = http.fetchManifest(normalized)
        val first = current.isEmpty()
        storage.save(current + StoredProvider(fetched.resolvedUrl, fetched.manifest, enabled = true, primary = first))
    }
    suspend fun refreshManifests() {
        val current = providers.first()
        val refreshed = current.map { stored ->
            runCatching { http.fetchManifest(stored.manifestUrl) }.getOrNull()?.let { fetched -> stored.copy(manifestUrl = fetched.resolvedUrl, manifest = fetched.manifest) } ?: stored
        }
        if (refreshed != current) storage.save(refreshed)
    }
    suspend fun edit(oldUrl: String, newUrl: String) {
        val current = providers.first()
        val old = current.firstOrNull { it.manifestUrl == oldUrl } ?: error("Provedor não encontrado")
        val fetched = http.fetchManifest(newUrl.trim())
        storage.save(current.map { if (it.manifestUrl == oldUrl) old.copy(manifestUrl = fetched.resolvedUrl, manifest = fetched.manifest) else it })
    }
    suspend fun remove(url: String) {
        storage.save(ProviderRules.remove(providers.first(), url))
    }
    suspend fun setEnabled(url: String, enabled: Boolean) = update(url) { it.copy(enabled = enabled, primary = it.primary && enabled) }
    suspend fun setPrimary(url: String) {
        storage.save(ProviderRules.setPrimary(providers.first(), url))
    }
    private suspend fun update(url: String, transform: (StoredProvider) -> StoredProvider) = storage.save(providers.first().map { if (it.manifestUrl == url) transform(it) else it })
}
