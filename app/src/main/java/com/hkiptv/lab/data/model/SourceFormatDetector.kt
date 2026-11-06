package com.hkiptv.lab.data.model

object SourceFormatDetector {
    fun detect(url: String, declaredType: String? = null): SourceFormat {
        val type = declaredType.orEmpty().lowercase()
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            type.contains("hls") || type.contains("mpegurl") || path.endsWith(".m3u8") -> SourceFormat.HLS
            type.contains("mp4") || type.contains("video/mp4") || path.endsWith(".mp4") -> SourceFormat.MP4
            else -> SourceFormat.UNKNOWN
        }
    }
}
