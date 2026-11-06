package com.hkiptv.lab.data.remote

import com.hkiptv.lab.data.model.*
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

open class ProviderNetworkException(message: String, cause: Throwable? = null, open val statusCode: Int? = null) : IOException(message, cause)
class AuthenticationRequiredException : ProviderNetworkException("Autenticação necessária (HTTP 401)", statusCode = 401)
class AccessDeniedException : ProviderNetworkException("Acesso negado (HTTP 403)", statusCode = 403)
class ManifestNotFoundException : ProviderNetworkException("Manifest não encontrado (HTTP 404)", statusCode = 404)
class ProviderTimeoutException(cause: Throwable? = null) : ProviderNetworkException("Tempo limite excedido ao contatar o provedor", cause)
class ProviderConnectionException(cause: Throwable? = null) : ProviderNetworkException("Falha de conexão com o provedor", cause)
class UnexpectedHtmlException : ProviderNetworkException("O servidor retornou HTML em vez de JSON")
class ProviderResponseException(message: String, cause: Throwable? = null) : ProviderNetworkException(message, cause)
class ProviderHttpStatusException(override val statusCode: Int) : ProviderNetworkException(when (statusCode) {
    400 -> "Requisição rejeitada pelo provedor (HTTP 400)"
    else -> "Falha HTTP $statusCode"
}, statusCode = statusCode)
data class FetchedManifest(val manifest: ProviderManifest, val resolvedUrl: String)

class ProviderHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).followRedirects(true).build(),
    private val parser: ManifestParser = ManifestParser(),
    private val allowHttpForTests: Boolean = false,
    private val diagnostics: DiagnosticLogger = AndroidDiagnosticLogger
) {
    private val native = NativeProtocolAdapter(::get, diagnostics)

    suspend fun fetchManifest(inputUrl: String): FetchedManifest {
        val candidates = ManifestUrlResolver.candidates(inputUrl, allowHttpForTests)
        var fallbackReason: Exception? = null
        for ((index, url) in candidates.withIndex()) {
            try { return FetchedManifest(parser.parse(get(url, emptyMap())), url) }
            catch (error: UnexpectedHtmlException) { if (index == candidates.lastIndex) throw error else fallbackReason = error }
            catch (error: ManifestNotFoundException) { if (index == candidates.lastIndex) throw error else fallbackReason = error }
        }
        throw fallbackReason ?: ManifestNotFoundException()
    }

    suspend fun catalog(provider: StoredProvider): CatalogResponse = when (provider.manifest.protocol) {
        ProviderProtocol.HK -> decodeHk(provider, provider.manifest.endpoints.catalog, "catálogo")
        ProviderProtocol.NATIVE_ADDON -> native.catalog(provider)
    }

    suspend fun search(provider: StoredProvider, query: String): CatalogResponse = when (provider.manifest.protocol) {
        ProviderProtocol.HK -> {
            val template = provider.manifest.endpoints.search ?: throw ProviderNetworkException("O provedor não oferece busca")
            protocolJson.decodeFromString(get(resolveHk(provider.manifestUrl, template.replace("{query}", encode(query))), provider.manifest.headers))
        }
        ProviderProtocol.NATIVE_ADDON -> native.search(provider, query)
    }

    suspend fun metadata(provider: StoredProvider, item: MediaItem, identifier: String = item.id): MediaItem = when (provider.manifest.protocol) {
        ProviderProtocol.HK -> decodeHk(provider, provider.manifest.endpoints.metadata?.replace("{type}", encode(item.type))?.replace("{id}", encode(identifier)), "metadados")
        ProviderProtocol.NATIVE_ADDON -> native.metadata(provider, item, identifier)
    }

    suspend fun sources(provider: StoredProvider, type: String, id: String): SourcesResponse = when (provider.manifest.protocol) {
        ProviderProtocol.HK -> decodeHk(provider, provider.manifest.endpoints.sources?.replace("{type}", encode(type))?.replace("{id}", encode(id)), "fontes")
        ProviderProtocol.NATIVE_ADDON -> native.sources(provider, type, id)
    }

    private suspend inline fun <reified T> decodeHk(provider: StoredProvider, endpoint: String?, resource: String): T {
        endpoint ?: throw ProviderNetworkException("O provedor não oferece $resource")
        return try { protocolJson.decodeFromString(get(resolveHk(provider.manifestUrl, endpoint), provider.manifest.headers)) }
        catch (error: ProviderNetworkException) { throw error }
        catch (error: Exception) { throw ProviderResponseException("Resposta JSON de $resource inválida", error) }
    }

    @PublishedApi internal suspend fun get(url: String, headers: Map<String, String>): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("User-Agent", "HK-IPTV-Mobile-Lab/0.1.0")
        headers.forEach { (name, value) -> builder.header(name, value) }
        try { client.newCall(builder.build()).execute().use { response ->
            when (response.code) { 401 -> throw AuthenticationRequiredException(); 403 -> throw AccessDeniedException(); 404 -> throw ManifestNotFoundException() }
            if (!response.isSuccessful) throw ProviderHttpStatusException(response.code)
            val body = response.body?.string() ?: throw ProviderResponseException("Resposta vazia do provedor")
            val type = response.header("Content-Type").orEmpty().lowercase()
            if ("text/html" in type || body.trimStart().startsWith("<html", true) || body.trimStart().startsWith("<!doctype html", true)) throw UnexpectedHtmlException()
            body
        } } catch (error: ProviderNetworkException) { throw error }
        catch (error: SocketTimeoutException) { throw ProviderTimeoutException(error) }
        catch (error: IOException) { throw ProviderConnectionException(error) }
    }

    @PublishedApi internal fun resolveHk(manifestUrl: String, endpoint: String) = ManifestUrlResolver.route(manifestUrl, endpoint)
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
