package com.hkiptv.lab.data.remote

import android.util.Log
import com.hkiptv.lab.data.model.IdentifierKind

data class DiagnosticEvent(val provider: String, val resource: String, val contentType: String, val identifierKind: IdentifierKind, val stage: String, val status: Int? = null)
fun interface DiagnosticLogger { fun log(event: DiagnosticEvent) }

object AndroidDiagnosticLogger : DiagnosticLogger {
    override fun log(event: DiagnosticEvent) {
        Log.d("HKProvider", "provider=${event.provider}; resource=${event.resource}; type=${event.contentType}; idKind=${event.identifierKind.label}; stage=${event.stage}; status=${event.status ?: "n/a"}")
    }
}
