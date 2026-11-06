package com.hkiptv.lab.data.repository

import com.hkiptv.lab.data.model.StoredProvider

object ProviderRules {
    fun setPrimary(providers: List<StoredProvider>, url: String): List<StoredProvider> {
        require(providers.any { it.manifestUrl == url && it.enabled }) { "Somente um provedor ativo pode ser principal" }
        return providers.map { it.copy(primary = it.manifestUrl == url) }
    }
    fun remove(providers: List<StoredProvider>, url: String): List<StoredProvider> {
        val remaining = providers.filterNot { it.manifestUrl == url }
        return if (remaining.none { it.primary } && remaining.isNotEmpty()) remaining.mapIndexed { index, item -> item.copy(primary = index == 0) } else remaining
    }
}
