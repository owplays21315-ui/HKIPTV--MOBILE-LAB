package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

sealed class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MalformedJsonException(cause: Throwable? = null) : ManifestException("JSON malformado", cause)
class UnsupportedProtocolException : ManifestException("Manifest válido, mas protocolo não suportado")
class MissingManifestFieldException(field: String) : ManifestException("Campo obrigatório ausente: $field")

class ManifestParser(private val json: Json = protocolJson) {
    fun parse(raw: String): ProviderManifest {
        val root = try { json.parseToJsonElement(raw).jsonObject }
        catch (error: SerializationException) { throw MalformedJsonException(error) }
        catch (error: IllegalArgumentException) { throw MalformedJsonException(error) }
        requiredText(root, "id"); requiredText(root, "name"); requiredText(root, "version")
        return when {
            root["endpoints"] is JsonObject -> parseHk(root)
            root.containsKey("resources") && (root.containsKey("types") || root.containsKey("catalogs") || root.containsKey("idPrefixes")) -> parseNative(root)
            else -> throw UnsupportedProtocolException()
        }
    }

    private fun parseHk(root: JsonObject): ProviderManifest {
        val manifest = try { json.decodeFromJsonElement<ProviderManifest>(root) }
        catch (error: SerializationException) { throw MalformedJsonException(error) }
        if (listOf(manifest.endpoints.catalog, manifest.endpoints.search, manifest.endpoints.metadata, manifest.endpoints.sources).all { it == null }) throw MissingManifestFieldException("endpoints.catalog/search/metadata/sources")
        return manifest.copy(protocol = ProviderProtocol.HK)
    }

    private fun parseNative(root: JsonObject): ProviderManifest {
        val resources = root["resources"].stringOrObjectNames()
        if (resources.isEmpty()) throw MissingManifestFieldException("resources")
        val catalogs = root["catalogs"]?.arrayOrEmpty()?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val type = item.text("type") ?: return@mapNotNull null
            val id = item.text("id") ?: return@mapNotNull null
            ProviderCatalog(type, id, item.text("name"), item["extra"]?.arrayOrEmpty()?.mapNotNull { extraElement ->
                val extra = extraElement as? JsonObject ?: return@mapNotNull null
                extra.text("name")?.let { CatalogExtra(it, extra["isRequired"]?.jsonPrimitive?.booleanOrNull == true, extra["options"].strings()) }
            }.orEmpty())
        }.orEmpty()
        return ProviderManifest(requiredText(root, "id"), requiredText(root, "name"), requiredText(root, "version"), root.text("description"), resources, protocol = ProviderProtocol.NATIVE_ADDON, types = root["types"].strings(), catalogs = catalogs, idPrefixes = root["idPrefixes"].strings())
    }

    private fun requiredText(root: JsonObject, name: String) = root.text(name)?.takeIf { it.isNotBlank() } ?: throw MissingManifestFieldException(name)
}

private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonElement?.strings() = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonElement?.stringOrObjectNames() = (this as? JsonArray)?.mapNotNull { element -> when (element) { is JsonPrimitive -> element.contentOrNull; is JsonObject -> element.text("name"); else -> null } }.orEmpty()
private fun JsonElement.arrayOrEmpty() = this as? JsonArray ?: JsonArray(emptyList())

val protocolJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
