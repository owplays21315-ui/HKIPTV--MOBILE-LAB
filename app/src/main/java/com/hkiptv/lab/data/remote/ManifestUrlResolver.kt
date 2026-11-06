package com.hkiptv.lab.data.remote

import java.net.URI

object ManifestUrlResolver {
    fun candidates(input: String, allowHttp: Boolean = false): List<String> {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: throw ProviderNetworkException("URL do manifest inválida")
        if (uri.scheme != "https" && !(allowHttp && uri.scheme == "http")) throw ProviderNetworkException("Somente URLs HTTPS são aceitas")
        if (uri.host.isNullOrBlank()) throw ProviderNetworkException("URL do manifest inválida")
        if (uri.path.orEmpty().endsWith("/manifest.json") || uri.path == "manifest.json") return listOf(uri.toString())
        val path = uri.path.orEmpty().let { if (it.endsWith('/')) it else "$it/" } + "manifest.json"
        val fallback = URI(uri.scheme, uri.rawAuthority, path, uri.rawQuery, uri.rawFragment).toString()
        return listOf(uri.toString(), fallback).distinct()
    }

    fun route(manifestUrl: String, relativeRoute: String): String {
        val base = URI(manifestUrl)
        val resolved = base.resolve(relativeRoute)
        val query = listOfNotNull(base.rawQuery, resolved.rawQuery).filter { it.isNotBlank() }.distinct().joinToString("&").ifBlank { null }
        return URI(resolved.scheme, resolved.rawAuthority, resolved.rawPath, query, null).toString()
    }
}
