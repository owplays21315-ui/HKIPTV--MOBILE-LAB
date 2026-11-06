package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.repository.ProviderRouting
import kotlinx.serialization.json.*
import java.net.URLEncoder

class NativeProtocolAdapter(private val get: suspend (String, Map<String, String>) -> String, private val diagnostics: DiagnosticLogger) {
    suspend fun catalog(provider: StoredProvider, catalog: ProviderCatalog? = null, extra: Map<String, String> = emptyMap()): CatalogResponse {
        val selected = catalog ?: provider.manifest.catalogs.firstOrNull() ?: throw ProviderNetworkException("O provedor oferece apenas fontes; não possui catálogo")
        val missing = selected.extra.filter { it.required && extra[it.name].isNullOrBlank() }.map { it.name }
        if (missing.isNotEmpty()) throw ProviderNetworkException("Parâmetros obrigatórios do catálogo ausentes: ${missing.joinToString()}")
        val suffix = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val raw = request(provider, "catalog", selected.type, selected.id, ManifestUrlResolver.route(provider.manifestUrl, "catalog/${encode(selected.type)}/${encode(selected.id)}$suffix.json"))
        val root = decodeObject(raw)
        return CatalogResponse(root["metas"].objects().mapNotNull(::mediaFromMeta))
    }

    suspend fun search(provider: StoredProvider, query: String): CatalogResponse {
        val catalog = provider.manifest.catalogs.firstOrNull { catalog -> catalog.extra.any { it.name == "search" } }
            ?: throw ProviderNetworkException("O provedor não oferece busca")
        return catalog(provider, catalog, mapOf("search" to query))
    }

    suspend fun metadata(provider: StoredProvider, fallback: MediaItem, id: String = fallback.id): MediaItem {
        if (!provider.manifest.offersMetadata) throw ProviderNetworkException("O provedor não oferece metadados")
        val raw = request(provider, "meta", fallback.type, id, ManifestUrlResolver.route(provider.manifestUrl, "meta/${encode(fallback.type)}/${encode(id)}.json"))
        val root = decodeObject(raw)
        val data = root["data"] as? JsonObject
        val meta = listOfNotNull(root["meta"] as? JsonObject, data?.get("meta") as? JsonObject, data?.takeIf(::looksLikeMeta), root.takeIf(::looksLikeMeta))
            .filter { it.isNotEmpty() }.maxByOrNull(::metaScore)
            ?: throw ProviderResponseException("Resposta de metadados sem objeto meta reconhecível")
        return mediaFromMeta(meta, includeEpisodes = true, fallback = fallback) ?: throw ProviderResponseException("Metadados sem id ou nome e sem dados do catálogo")
    }

    suspend fun sources(provider: StoredProvider, type: String, id: String): SourcesResponse {
        if (!provider.manifest.offersSources) throw ProviderNetworkException("O provedor não oferece fontes")
        val raw = request(provider, "stream", type, id, ManifestUrlResolver.route(provider.manifestUrl, "stream/${encode(type)}/${encode(id)}.json"))
        val streams = decodeObject(raw)["streams"].objects()
        return SourcesResponse(streams.mapIndexedNotNull { index, stream -> streamToSource(index, stream, provider.manifest.name) })
    }

    private fun mediaFromMeta(meta: JsonObject, includeEpisodes: Boolean = false, fallback: MediaItem? = null): MediaItem? {
        val responseId = meta.text("id")
        val id = fallback?.id ?: responseId ?: return null
        val title = meta.text("name") ?: meta.text("title") ?: fallback?.title ?: return null
        val videos = if (includeEpisodes) meta["videos"].objects() else emptyList()
        val seasons = videos.mapNotNull { it.int("season") }.distinct().sorted().map { seasonNumber ->
            val episodes = videos.filter { it.int("season") == seasonNumber }.mapIndexedNotNull { index, video ->
                val episodeId = video.text("id") ?: return@mapIndexedNotNull null
                Episode(episodeId, video.int("episode") ?: video.int("number") ?: index + 1, video.text("name") ?: video.text("title") ?: "Episódio ${index + 1}", video.text("overview") ?: video.text("description"), seasonNumber)
            }
            Season(seasonNumber, "Temporada $seasonNumber", episodes)
        }
        val responseIdentifier = responseId?.takeIf { it != id }?.let { ProviderRouting.identifierKind(it).name.lowercase() to it }
        val identifiers = fallback?.identifiers.orEmpty() + extractIdentifiers(meta) + listOfNotNull(responseIdentifier).toMap()
        return MediaItem(id, title, meta.text("description") ?: fallback?.description, meta.text("poster") ?: fallback?.poster, fallback?.type ?: meta.text("type") ?: "movie", meta.int("year") ?: fallback?.year, seasons.ifEmpty { fallback?.seasons.orEmpty() }, identifiers)
    }

    private fun streamToSource(index: Int, stream: JsonObject, providerName: String): MediaSource? {
        val url = stream.text("url") ?: return null
        val name = stream.text("name") ?: "Fonte ${index + 1}"
        val description = stream.text("description") ?: stream.text("title")
        val quality = qualityRegex.find(listOfNotNull(name, description).joinToString(" "))?.value
        val requestHeaders = (((stream["behaviorHints"] as? JsonObject)?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject).stringMap()
        val directHeaders = (stream["headers"] as? JsonObject).stringMap()
        val format = SourceFormatDetector.detect(url, stream.text("type"))
        return MediaSource("stream-$index", name, url, format, description, quality, requestHeaders + directHeaders, verified = false, providerName = providerName)
    }

    private fun decodeObject(raw: String) = try { protocolJson.parseToJsonElement(raw).jsonObject } catch (error: Exception) { throw ProviderResponseException("Resposta JSON do provedor inválida", error) }
    private fun looksLikeMeta(value: JsonObject) = value["id"] is JsonPrimitive || value["name"] is JsonPrimitive || value["title"] is JsonPrimitive
    private fun metaScore(value: JsonObject) = listOf("id", "type", "name", "title", "description", "videos", "imdb_id", "external_ids").count(value::containsKey)
    private fun extractIdentifiers(meta: JsonObject): Map<String, String> = buildMap {
        fun add(key: String, value: String?) { value?.takeIf(String::isNotBlank)?.let { put(key, it) } }
        add("imdb", meta.text("imdb_id") ?: meta.text("imdbId") ?: meta.text("imdb"))
        val tmdb = meta.text("tmdb_id") ?: meta.text("tmdbId")
        add("tmdb", tmdb?.let { if (it.startsWith("tmdb:", true)) it else "tmdb:$it" })
        val external = meta["external_ids"] as? JsonObject
        add("imdb", external?.text("imdb_id")); add("tmdb", external?.text("tmdb_id")?.let { if (it.startsWith("tmdb:", true)) it else "tmdb:$it" })
    }

    private suspend fun request(provider: StoredProvider, resource: String, type: String, id: String, url: String): String {
        val kind = ProviderRouting.identifierKind(id)
        diagnostics.log(DiagnosticEvent(provider.manifest.name, resource, type, kind, "request"))
        return try {
            get(url, emptyMap()).also { diagnostics.log(DiagnosticEvent(provider.manifest.name, resource, type, kind, "response", 200)) }
        } catch (error: ProviderHttpStatusException) {
            diagnostics.log(DiagnosticEvent(provider.manifest.name, resource, type, kind, "http_error", error.statusCode))
            throw ProviderNetworkException("${provider.manifest.name} rejeitou $resource (HTTP ${error.statusCode}; tipo $type; identificador ${kind.label})", error)
        } catch (error: Exception) {
            diagnostics.log(DiagnosticEvent(provider.manifest.name, resource, type, kind, "failed", (error as? ProviderNetworkException)?.statusCode))
            throw error
        }
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private companion object { val qualityRegex = Regex("(?i)\\b(?:2160|1440|1080|720|480|360)p\\b|\\b4k\\b") }
}

private fun JsonElement?.objects() = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: text(name)?.take(4)?.toIntOrNull()
private fun JsonObject?.stringMap() = this?.mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }?.toMap().orEmpty()
