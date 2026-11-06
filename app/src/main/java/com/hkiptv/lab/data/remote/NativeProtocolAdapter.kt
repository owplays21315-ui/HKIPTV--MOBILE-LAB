package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.*
import kotlinx.serialization.json.*
import java.net.URLEncoder

class NativeProtocolAdapter(private val get: suspend (String, Map<String, String>) -> String) {
    suspend fun catalog(provider: StoredProvider, catalog: ProviderCatalog? = null, extra: Map<String, String> = emptyMap()): CatalogResponse {
        val selected = catalog ?: provider.manifest.catalogs.firstOrNull() ?: throw ProviderNetworkException("O provedor oferece apenas fontes; não possui catálogo")
        val missing = selected.extra.filter { it.required && extra[it.name].isNullOrBlank() }.map { it.name }
        if (missing.isNotEmpty()) throw ProviderNetworkException("Parâmetros obrigatórios do catálogo ausentes: ${missing.joinToString()}")
        val suffix = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val raw = get(ManifestUrlResolver.route(provider.manifestUrl, "catalog/${encode(selected.type)}/${encode(selected.id)}$suffix.json"), emptyMap())
        val root = decodeObject(raw)
        return CatalogResponse(root["metas"].objects().mapNotNull(::mediaFromMeta))
    }

    suspend fun search(provider: StoredProvider, query: String): CatalogResponse {
        val catalog = provider.manifest.catalogs.firstOrNull { catalog -> catalog.extra.any { it.name == "search" } }
            ?: throw ProviderNetworkException("O provedor não oferece busca")
        return catalog(provider, catalog, mapOf("search" to query))
    }

    suspend fun metadata(provider: StoredProvider, type: String, id: String): MediaItem {
        if (!provider.manifest.offersMetadata) throw ProviderNetworkException("O provedor não oferece metadados")
        val raw = get(ManifestUrlResolver.route(provider.manifestUrl, "meta/${encode(type)}/${encode(id)}.json"), emptyMap())
        val meta = decodeObject(raw)["meta"] as? JsonObject ?: throw ProviderResponseException("Resposta de metadados sem o campo meta")
        return mediaFromMeta(meta, includeEpisodes = true) ?: throw ProviderResponseException("Metadados sem id ou nome")
    }

    suspend fun sources(provider: StoredProvider, type: String, id: String): SourcesResponse {
        if (!provider.manifest.offersSources) throw ProviderNetworkException("O provedor não oferece fontes")
        val raw = get(ManifestUrlResolver.route(provider.manifestUrl, "stream/${encode(type)}/${encode(id)}.json"), emptyMap())
        val streams = decodeObject(raw)["streams"].objects()
        return SourcesResponse(streams.mapIndexedNotNull { index, stream -> streamToSource(index, stream) })
    }

    private fun mediaFromMeta(meta: JsonObject, includeEpisodes: Boolean = false): MediaItem? {
        val id = meta.text("id") ?: return null
        val title = meta.text("name") ?: meta.text("title") ?: return null
        val videos = if (includeEpisodes) meta["videos"].objects() else emptyList()
        val seasons = videos.mapNotNull { it.int("season") }.distinct().sorted().map { seasonNumber ->
            val episodes = videos.filter { it.int("season") == seasonNumber }.mapIndexed { index, video ->
                Episode(video.text("id") ?: "$id:$seasonNumber:${video.int("episode") ?: index + 1}", video.int("episode") ?: video.int("number") ?: index + 1, video.text("name") ?: video.text("title") ?: "Episódio ${index + 1}", video.text("overview") ?: video.text("description"), seasonNumber)
            }
            Season(seasonNumber, "Temporada $seasonNumber", episodes)
        }
        return MediaItem(id, title, meta.text("description"), meta.text("poster"), meta.text("type") ?: "movie", meta.int("year"), seasons)
    }

    private fun streamToSource(index: Int, stream: JsonObject): MediaSource? {
        val url = stream.text("url") ?: return null
        val name = stream.text("name") ?: "Fonte ${index + 1}"
        val description = stream.text("description") ?: stream.text("title")
        val quality = qualityRegex.find(listOfNotNull(name, description).joinToString(" "))?.value
        val requestHeaders = (((stream["behaviorHints"] as? JsonObject)?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject).stringMap()
        val directHeaders = (stream["headers"] as? JsonObject).stringMap()
        val cleanPath = url.substringBefore('?').lowercase()
        val format = if (cleanPath.endsWith(".m3u8") || stream.text("type")?.contains("hls", true) == true) SourceFormat.HLS else SourceFormat.MP4
        return MediaSource("stream-$index", name, url, format, description, quality, requestHeaders + directHeaders, verified = false)
    }

    private fun decodeObject(raw: String) = try { protocolJson.parseToJsonElement(raw).jsonObject } catch (error: Exception) { throw ProviderResponseException("Resposta JSON do provedor inválida", error) }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private companion object { val qualityRegex = Regex("(?i)\\b(?:2160|1440|1080|720|480|360)p\\b|\\b4k\\b") }
}

private fun JsonElement?.objects() = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: text(name)?.take(4)?.toIntOrNull()
private fun JsonObject?.stringMap() = this?.mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }?.toMap().orEmpty()
