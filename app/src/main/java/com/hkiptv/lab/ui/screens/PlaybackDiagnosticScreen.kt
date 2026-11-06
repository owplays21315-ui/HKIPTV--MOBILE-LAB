package com.hkiptv.lab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hkiptv.lab.player.PlaybackDiagnostic

@Composable fun PlaybackDiagnosticScreen(diagnostic: PlaybackDiagnostic, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }; Text("Diagnóstico técnico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        DiagnosticRow("Provedor", diagnostic.provider)
        DiagnosticRow("Qualidade", diagnostic.quality)
        DiagnosticRow("Formato", diagnostic.format.name)
        DiagnosticRow("Status HTTP", diagnostic.status?.toString() ?: "Não recebido")
        DiagnosticRow("Categoria", diagnostic.category.label)
        DiagnosticRow("Etapa", diagnostic.stage)
        DiagnosticRow("Redirecionamento", if (diagnostic.redirected) if (diagnostic.crossHostRedirect) "Sim, para outro host" else "Sim, mesmo host" else "Não detectado")
        HorizontalDivider()
        Text("Headers fornecidos pelo provedor", fontWeight = FontWeight.Bold)
        DiagnosticRow("Quantidade", diagnostic.headers.total.toString())
        DiagnosticRow("Authorization", present(diagnostic.headers.authorization)); DiagnosticRow("Cookie", present(diagnostic.headers.cookie)); DiagnosticRow("Referer", present(diagnostic.headers.referer)); DiagnosticRow("Origin", present(diagnostic.headers.origin)); DiagnosticRow("User-Agent", present(diagnostic.headers.userAgent))
        Text("Os valores dos headers, a URL e parâmetros de autenticação não são exibidos nem registrados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (diagnostic.crossHostRedirect && diagnostic.headers.total > 0) Text("Headers do provedor não foram encaminhados ao host diferente.", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun DiagnosticRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) } }
private fun present(value: Boolean) = if (value) "Presente" else "Ausente"
