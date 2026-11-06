package com.hkiptv.lab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hkiptv.lab.data.model.StoredProvider
import com.hkiptv.lab.ui.AppUiState

@Composable fun ProvidersScreen(state: AppUiState, add: (String) -> Unit, edit: (String, String) -> Unit, enable: (String, Boolean) -> Unit, primary: (String) -> Unit, remove: (String) -> Unit) {
    var url by remember { mutableStateOf("") }; var editing by remember { mutableStateOf<String?>(null) }; var removing by remember { mutableStateOf<StoredProvider?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Provedores", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Adicione um manifest HTTPS. Ele será validado antes de ser salvo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("URL do manifest") }, singleLine = true)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing?.let { edit(it, url) } ?: add(url) }, enabled = url.startsWith("https://") && !state.loading) { Text(if (editing == null) "Adicionar e validar" else "Salvar alteração") }
            if (editing != null) TextButton({ editing = null; url = "" }) { Text("Cancelar") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(state.providers, key = { it.manifestUrl }) { provider ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(provider.manifest.name, fontWeight = FontWeight.Bold); Text("v${provider.manifest.version}"); Text("Recursos: ${provider.manifest.resources.joinToString().ifBlank { "não declarados" }}", color = MaterialTheme.colorScheme.onSurfaceVariant); if (provider.manifest.types.isNotEmpty()) Text("Tipos: ${provider.manifest.types.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant); if (provider.manifest.catalogs.isNotEmpty()) Text("Catálogos: ${provider.manifest.catalogs.joinToString { it.name ?: it.id }}", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(capabilityLabel(provider), color = MaterialTheme.colorScheme.primary) }; Switch(provider.enabled, { enable(provider.manifestUrl, it) }) }
                if (provider.primary) AssistChip({}, { Text("Principal") })
                Row { TextButton({ url = provider.manifestUrl; editing = provider.manifestUrl }) { Text("Editar URL") }; TextButton({ primary(provider.manifestUrl) }, enabled = provider.enabled && !provider.primary) { Text("Tornar principal") }; TextButton({ removing = provider }) { Text("Remover") } }
            } }
        } }
    }
    removing?.let { provider -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover ${provider.manifest.name}?") }, text = { Text("As configurações locais deste provedor serão apagadas.") }, confirmButton = { TextButton({ remove(provider.manifestUrl); removing = null }) { Text("Remover") } }, dismissButton = { TextButton({ removing = null }) { Text("Cancelar") } }) }
}

private fun capabilityLabel(provider: StoredProvider): String = buildList {
    if (provider.manifest.offersCatalog) add("catálogo")
    if (provider.manifest.offersMetadata) add("metadados")
    if (provider.manifest.offersSources) add("fontes")
}.joinToString(" • ").ifBlank { "sem capacidades reconhecidas" }
