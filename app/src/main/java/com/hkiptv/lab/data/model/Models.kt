package com.hkiptv.lab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ProviderManifest(val id: String, val name: String, val version: String, val description: String? = null, val resources: List<String> = emptyList(), val endpoints: ProviderEndpoints, val headers: Map<String, String> = emptyMap())
@Serializable data class ProviderEndpoints(val catalog: String? = null, val search: String? = null, val metadata: String? = null, val sources: String? = null)
@Serializable data class CatalogResponse(val items: List<MediaItem> = emptyList())
@Serializable data class MediaItem(val id: String, val title: String, val description: String? = null, val poster: String? = null, val type: String = "movie", val year: Int? = null, val seasons: List<Season> = emptyList())
@Serializable data class Season(val number: Int, val title: String? = null, val episodes: List<Episode> = emptyList())
@Serializable data class Episode(val id: String, val number: Int, val title: String, val description: String? = null)
@Serializable data class SourcesResponse(val sources: List<MediaSource> = emptyList())
@Serializable data class MediaSource(val id: String, val name: String, val url: String, val format: SourceFormat, val headers: Map<String, String> = emptyMap())
@Serializable enum class SourceFormat { @SerialName("mp4") MP4, @SerialName("hls") HLS }
@Serializable data class StoredProvider(val manifestUrl: String, val manifest: ProviderManifest, val enabled: Boolean = true, val primary: Boolean = false)
