package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.ProviderManifest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ManifestValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ManifestParser(private val json: Json = protocolJson) {
    fun parse(raw: String): ProviderManifest {
        val manifest = try { json.decodeFromString<ProviderManifest>(raw) }
        catch (error: SerializationException) { throw ManifestValidationException("JSON do manifest inválido", error) }
        if (manifest.id.isBlank()) throw ManifestValidationException("O campo id é obrigatório")
        if (manifest.name.isBlank()) throw ManifestValidationException("O campo name é obrigatório")
        if (manifest.version.isBlank()) throw ManifestValidationException("O campo version é obrigatório")
        if (listOf(manifest.endpoints.catalog, manifest.endpoints.search, manifest.endpoints.metadata, manifest.endpoints.sources).all { it == null })
            throw ManifestValidationException("O manifest deve declarar pelo menos um endpoint")
        return manifest
    }
}

val protocolJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
