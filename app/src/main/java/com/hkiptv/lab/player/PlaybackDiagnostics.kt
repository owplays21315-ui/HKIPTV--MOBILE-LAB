package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.data.model.SourceFormat
import androidx.media3.common.MimeTypes

enum class PlaybackFailureCategory(val label: String) {
    AUTHENTICATION("Autenticação necessária"), ACCESS_DENIED("Acesso negado"), NOT_FOUND("Mídia não encontrada"), RATE_LIMIT("Limite de requisições"), SERVER("Falha no servidor"), CONNECTION("Falha de conexão"), HTTP("Resposta HTTP inválida"), NONE("Sem falha")
}

data class HeaderPresence(val total: Int, val authorization: Boolean, val cookie: Boolean, val referer: Boolean, val origin: Boolean, val userAgent: Boolean) {
    companion object {
        fun from(headers: Map<String, String>) = HeaderPresence(headers.size, headers.containsName("Authorization"), headers.containsName("Cookie"), headers.containsName("Referer"), headers.containsName("Origin"), headers.containsName("User-Agent"))
        private fun Map<String, String>.containsName(name: String) = keys.any { it.equals(name, true) }
    }
}

data class PlaybackDiagnostic(
    val provider: String,
    val quality: String,
    val format: SourceFormat,
    val status: Int?,
    val category: PlaybackFailureCategory,
    val stage: String,
    val redirected: Boolean,
    val crossHostRedirect: Boolean,
    val headers: HeaderPresence
)

object PlaybackDiagnostics {
    fun category(status: Int?): PlaybackFailureCategory = when (status) {
        null -> PlaybackFailureCategory.CONNECTION
        401 -> PlaybackFailureCategory.AUTHENTICATION
        403 -> PlaybackFailureCategory.ACCESS_DENIED
        404 -> PlaybackFailureCategory.NOT_FOUND
        429 -> PlaybackFailureCategory.RATE_LIMIT
        in 500..599 -> PlaybackFailureCategory.SERVER
        in 400..499 -> PlaybackFailureCategory.HTTP
        else -> PlaybackFailureCategory.NONE
    }

    fun snapshot(source: MediaSource, network: PlaybackNetworkSnapshot, stage: String, statusOverride: Int? = null, failed: Boolean = false): PlaybackDiagnostic {
        val status = statusOverride ?: network.status
        val category = if (status == null && !network.connectionFailed && !failed) PlaybackFailureCategory.NONE else category(status)
        return PlaybackDiagnostic(source.providerName ?: "Não informado", source.quality ?: "Não informada", source.format, status, category, stage, network.redirected, network.crossHostRedirect, HeaderPresence.from(source.headers))
    }

    fun userMessage(diagnostic: PlaybackDiagnostic): String = when (diagnostic.status) {
        401 -> "HTTP 401 — autenticação necessária"
        403 -> "HTTP 403 — acesso negado pelo servidor da mídia"
        404 -> "HTTP 404 — mídia não encontrada"
        429 -> "HTTP 429 — limite de requisições atingido"
        in 500..599 -> "HTTP ${diagnostic.status} — falha no servidor da mídia"
        null -> "Falha de conexão durante ${diagnostic.stage}"
        else -> "HTTP ${diagnostic.status} — ${diagnostic.category.label}"
    }
}

object PlaybackMimeType {
    fun forFormat(format: SourceFormat): String? = when (format) { SourceFormat.HLS -> MimeTypes.APPLICATION_M3U8; SourceFormat.MP4 -> MimeTypes.VIDEO_MP4; SourceFormat.UNKNOWN -> null }
}
