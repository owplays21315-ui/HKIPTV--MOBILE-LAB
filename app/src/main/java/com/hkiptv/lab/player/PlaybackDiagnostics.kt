package com.hkiptv.lab.player

import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.data.model.SourceFormat
import com.hkiptv.lab.data.model.SourceFormatDetector
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException

enum class PlaybackFailureCategory(val label: String) {
    AUTHENTICATION("Autenticação necessária"), ACCESS_DENIED("Acesso negado"), NOT_FOUND("Mídia não encontrada"), RATE_LIMIT("Limite de requisições"), SERVER("Falha no servidor"), CONNECTION("Falha de conexão"), HTTP("Resposta HTTP inválida"), REDIRECT("Falha de redirecionamento"), FORMAT("Formato de mídia incompatível"), DECODING("Falha de decodificação"), NONE("Sem falha")
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
    val effectiveFormat: SourceFormat,
    val initialStatus: Int?,
    val finalStatus: Int?,
    val initialProtocol: String?,
    val finalProtocol: String?,
    val requestStage: PlaybackRequestStage,
    val redirectCount: Int,
    val category: PlaybackFailureCategory,
    val stage: String,
    val redirected: Boolean,
    val crossHostRedirect: Boolean,
    val downgradedToHttp: Boolean,
    val redirectCompleted: Boolean,
    val experimentalHttpEnabled: Boolean,
    val sensitiveHeadersWithheld: Boolean,
    val redirectFailure: RedirectFailure?,
    val stopReason: PlaybackStopReason?,
    val hops: List<RedirectHop>,
    val finalContentType: String?,
    val headers: HeaderPresence,
    val result: String
) { val status: Int? get() = finalStatus ?: initialStatus }

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

    fun snapshot(source: MediaSource, network: PlaybackNetworkSnapshot, stage: String, statusOverride: Int? = null, failed: Boolean = false, playbackErrorCode: Int? = null): PlaybackDiagnostic {
        val finalStatus = network.finalStatus ?: statusOverride
        val status = finalStatus ?: network.initialStatus
        val category = when {
            network.stopReason == PlaybackStopReason.FINAL_HTML_RESPONSE -> PlaybackFailureCategory.FORMAT
            network.redirectFailure != null -> PlaybackFailureCategory.REDIRECT
            playbackErrorCode in formatErrorCodes -> PlaybackFailureCategory.FORMAT
            playbackErrorCode in decodingErrorCodes -> PlaybackFailureCategory.DECODING
            status == null && !network.connectionFailed && !failed -> PlaybackFailureCategory.NONE
            else -> category(status)
        }
        val detectedFinal = SourceFormatDetector.detect("", network.finalContentType)
        val effectiveFormat = detectedFinal.takeUnless { it == SourceFormat.UNKNOWN } ?: source.format
        val result = when { failed || category != PlaybackFailureCategory.NONE -> "Falha"; stage == "concluído" -> "Concluído"; status in setOf(200, 206) -> "Conectado"; else -> "Pendente" }
        return PlaybackDiagnostic(source.providerName ?: "Não informado", source.quality ?: "Não informada", source.format, effectiveFormat, network.initialStatus ?: statusOverride, finalStatus, network.initialProtocol, network.finalProtocol, network.requestStage, network.redirectCount, category, stage, network.redirected, network.crossHostRedirect, network.downgradedToHttp, network.redirectCompleted, network.experimentalHttpEnabled, network.sensitiveHeadersWithheld, network.redirectFailure, network.stopReason, network.hops, network.finalContentType, HeaderPresence.from(source.headers), result)
    }

    fun userMessage(diagnostic: PlaybackDiagnostic): String {
        if (diagnostic.requestStage == PlaybackRequestStage.SEEK && diagnostic.status == 416) return "O servidor rejeitou o intervalo solicitado durante o seek"
        if (diagnostic.sensitiveHeadersWithheld && diagnostic.status in setOf(401, 403)) return "A reprodução exige credenciais que não podem ser transmitidas com segurança após o redirecionamento"
        if (diagnostic.stopReason == PlaybackStopReason.REDIRECT_BLOCKED) {
            return when (diagnostic.redirectFailure) {
                RedirectFailure.CLEARTEXT_DISABLED -> "Destino HTTP bloqueado: ative o modo HTTP experimental nos Ajustes"
                RedirectFailure.PRIVATE_ADDRESS -> "Redirecionamento bloqueado para endereço privado ou local"
                RedirectFailure.UNSUPPORTED_SCHEME -> "Redirecionamento bloqueado para protocolo não suportado"
                RedirectFailure.DNS_REBINDING -> "Redirecionamento bloqueado por mudança insegura de DNS"
                else -> "Próximo destino bloqueado pela política de segurança"
            }
        }
        when (diagnostic.stopReason) {
            PlaybackStopReason.REDIRECT_LIMIT_EXCEEDED -> return "Limite seguro de redirecionamentos excedido"
            PlaybackStopReason.REDIRECT_LOOP -> return "Loop de redirecionamento detectado"
            PlaybackStopReason.MISSING_LOCATION -> return "Redirecionamento sem header Location"
            PlaybackStopReason.INVALID_LOCATION -> return "Location do redirecionamento é inválido"
            PlaybackStopReason.REDIRECT_BLOCKED -> return "Próximo destino bloqueado pela política de segurança"
            PlaybackStopReason.FINAL_HTML_RESPONSE -> return "O destino final retornou HTML em vez de mídia"
            PlaybackStopReason.HTTP_ACCESS_DENIED -> return "Acesso à mídia negado pelo servidor"
            null -> Unit
        }
        if (diagnostic.category == PlaybackFailureCategory.FORMAT) return "Formato de mídia incompatível ou resposta inesperada"
        if (diagnostic.category == PlaybackFailureCategory.DECODING) return "Falha ao decodificar a mídia"
        return when (diagnostic.status) {
        401 -> "HTTP 401 — autenticação necessária"
        403 -> "HTTP 403 — acesso negado pelo servidor da mídia"
        404 -> "HTTP 404 — mídia não encontrada"
        429 -> "HTTP 429 — limite de requisições atingido"
        in 500..599 -> "HTTP ${diagnostic.status} — falha no servidor da mídia"
        null -> "Falha de conexão durante ${diagnostic.stage}"
        else -> when (diagnostic.redirectFailure) {
            RedirectFailure.CLEARTEXT_DISABLED -> "Destino HTTP bloqueado: ative o modo HTTP experimental nos Ajustes"
            RedirectFailure.PRIVATE_ADDRESS -> "Redirecionamento bloqueado para endereço privado"
            RedirectFailure.UNSUPPORTED_SCHEME -> "Protocolo de mídia não suportado"
            RedirectFailure.DNS_REBINDING -> "Destino bloqueado por mudança insegura de DNS"
            RedirectFailure.REDIRECT_LOOP -> "Loop de redirecionamento detectado"
            RedirectFailure.MISSING_LOCATION -> "HTTP ${diagnostic.status} — redirecionamento sem destino válido"
            RedirectFailure.INVALID_LOCATION -> "HTTP ${diagnostic.status} — endereço de redirecionamento inválido"
            RedirectFailure.TOO_MANY_REDIRECTS -> "Loop ou excesso de redirecionamentos"
            null -> "HTTP ${diagnostic.status} — ${diagnostic.category.label}"
        }
    }
    }

    private val formatErrorCodes = setOf(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)
    private val decodingErrorCodes = setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
}

object PlaybackMimeType {
    fun forFormat(format: SourceFormat): String? = when (format) { SourceFormat.HLS -> MimeTypes.APPLICATION_M3U8; SourceFormat.MP4 -> MimeTypes.VIDEO_MP4; SourceFormat.UNKNOWN -> null }
}
