package com.hkiptv.lab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class ProviderProtocol { @SerialName("hk") HK, @SerialName("native_addon") NATIVE_ADDON }

@Serializable
data class ProviderManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val resources: List<String> = emptyList(),
    val endpoints: ProviderEndpoints = ProviderEndpoints(),
    val headers: Map<String, String> = emptyMap(),
    val protocol: ProviderProtocol = ProviderProtocol.HK,
    val types: List<String> = emptyList(),
    val catalogs: List<ProviderCatalog> = emptyList(),
    val idPrefixes: List<String> = emptyList()
) {
    val offersCatalog: Boolean get() = endpoints.catalog != null || catalogs.isNotEmpty()
    val offersMetadata: Boolean get() = endpoints.metadata != null || "meta" in resources
    val offersSources: Boolean get() = endpoints.sources != null || "stream" in resources
}

@Serializable data class ProviderEndpoints(val catalog: String? = null, val search: String? = null, val metadata: String? = null, val sources: String? = null)
@Serializable data class ProviderCatalog(val type: String, val id: String, val name: String? = null, val extra: List<CatalogExtra> = emptyList())
@Serializable data class CatalogExtra(val name: String, val required: Boolean = false, val options: List<String> = emptyList())
@Serializable data class CatalogResponse(val items: List<MediaItem> = emptyList())
@Serializable data class MediaItem(val id: String, val title: String, val description: String? = null, val poster: String? = null, val type: String = "movie", val year: Int? = null, val seasons: List<Season> = emptyList())
@Serializable data class Season(val number: Int, val title: String? = null, val episodes: List<Episode> = emptyList())
@Serializable data class Episode(val id: String, val number: Int, val title: String, val description: String? = null, val season: Int? = null)
@Serializable data class SourcesResponse(val sources: List<MediaSource> = emptyList())
@Serializable data class MediaSource(val id: String, val name: String, val url: String, val format: SourceFormat, val description: String? = null, val quality: String? = null, val headers: Map<String, String> = emptyMap(), val verified: Boolean = false)
@Serializable enum class SourceFormat { @SerialName("mp4") MP4, @SerialName("hls") HLS }
@Serializable data class StoredProvider(val manifestUrl: String, val manifest: ProviderManifest, val enabled: Boolean = true, val primary: Boolean = false)
