package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.*
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

class ProviderNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ProviderHttpClient(private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(true).build(), private val parser: ManifestParser = ManifestParser()) {
    suspend fun fetchManifest(url: String): ProviderManifest = parser.parse(get(url, emptyMap()))
    suspend fun catalog(provider: StoredProvider): CatalogResponse = decode(provider, provider.manifest.endpoints.catalog, "catálogo")
    suspend fun search(provider: StoredProvider, query: String): CatalogResponse {
        val template = provider.manifest.endpoints.search ?: throw ProviderNetworkException("O provedor não oferece busca")
        return protocolJson.decodeFromString(get(resolve(provider.manifestUrl, template.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))), provider.manifest.headers))
    }
    suspend fun metadata(provider: StoredProvider, id: String): MediaItem = decode(provider, provider.manifest.endpoints.metadata?.replace("{id}", id), "metadados")
    suspend fun sources(provider: StoredProvider, id: String): SourcesResponse = decode(provider, provider.manifest.endpoints.sources?.replace("{id}", id), "fontes")

    private suspend inline fun <reified T> decode(provider: StoredProvider, endpoint: String?, resource: String): T {
        endpoint ?: throw ProviderNetworkException("O provedor não oferece $resource")
        return protocolJson.decodeFromString(get(resolve(provider.manifestUrl, endpoint), provider.manifest.headers))
    }
    @PublishedApi internal suspend fun get(url: String, headers: Map<String, String>): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val parsed = runCatching { URI(url) }.getOrNull()
        if (parsed?.scheme != "https") throw ProviderNetworkException("Somente URLs HTTPS são aceitas")
        val builder = Request.Builder().url(url).header("User-Agent", "HK-IPTV-Mobile-Lab/0.1.0")
        headers.forEach { (name, value) -> builder.header(name, value) }
        try { client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw ProviderNetworkException("Falha HTTP ${response.code}")
            response.body?.string() ?: throw ProviderNetworkException("Resposta vazia")
        } } catch (error: ProviderNetworkException) { throw error }
        catch (error: IOException) { throw ProviderNetworkException("Não foi possível contatar o provedor", error) }
    }
    @PublishedApi internal fun resolve(manifestUrl: String, endpoint: String): String = URI(manifestUrl).resolve(endpoint).toString()
}
