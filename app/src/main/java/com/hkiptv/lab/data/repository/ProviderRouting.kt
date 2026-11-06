package com.hkiptv.lab.data.repository

import com.hkiptv.lab.data.model.*

data class ProviderTarget(val provider: StoredProvider, val identifier: String, val identifierKind: IdentifierKind)

object ProviderRouting {
    fun identifierKind(id: String): IdentifierKind = when {
        id.matches(Regex("(?i)^tt\\d+(?::.*)?$")) -> IdentifierKind.IMDB
        id.startsWith("tmdb:", true) -> IdentifierKind.TMDB
        else -> IdentifierKind.OTHER
    }

    fun supports(provider: StoredProvider, resource: String, type: String, id: String): Boolean {
        if (!provider.enabled) return false
        val details = provider.manifest.resourceDetails.filter { it.name == resource }
        if (details.isNotEmpty()) return details.any { rule -> matches(rule.types, rule.idPrefixes, type, id) }
        if (resource !in provider.manifest.resources) return false
        return matches(provider.manifest.types, provider.manifest.idPrefixes, type, id)
    }

    fun targets(providers: List<StoredProvider>, resource: String, item: MediaItem): List<ProviderTarget> {
        val candidates = buildList { add(item.id); addAll(item.identifiers.values) }.filter(String::isNotBlank).distinct()
        return providers.mapNotNull { provider ->
            candidates.firstOrNull { id -> supports(provider, resource, item.type, id) }?.let { id -> ProviderTarget(provider, id, identifierKind(id)) }
        }
    }

    private fun matches(types: List<String>, prefixes: List<String>, type: String, id: String): Boolean {
        val typeMatches = types.isEmpty() || types.any { it.equals(type, true) }
        val prefixMatches = prefixes.isEmpty() || prefixes.any { id.startsWith(it, true) }
        return typeMatches && prefixMatches
    }
}
