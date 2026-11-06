package com.hkiptv.lab.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hkiptv.lab.data.model.MediaItem
import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.ui.AppUiState

@Composable private fun Page(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            back?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp)); content()
    }
}

@Composable fun HomeScreen(state: AppUiState, refresh: () -> Unit, select: (MediaItem) -> Unit) = Page("HK IPTV Mobile Lab") {
    Text("Catálogo do provedor principal", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp)); Button(onClick = refresh, enabled = !state.loading) { Text("Atualizar catálogo") }
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
    if (state.items.isEmpty() && !state.loading) EmptyState("Nenhum item carregado") else MediaList(state.items, select)
}

@Composable fun SearchScreen(state: AppUiState, search: (String) -> Unit, select: (MediaItem) -> Unit) = Page("Busca") {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
    Button({ search(query) }, Modifier.padding(top = 8.dp), enabled = query.isNotBlank() && !state.loading) { Text("Buscar") }
    MediaList(state.items, select)
}

@Composable private fun MediaList(values: List<MediaItem>, select: (MediaItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { it.id }) { item -> Card(Modifier.fillMaxWidth().clickable { select(item) }) { Column(Modifier.padding(16.dp)) { Text(item.title, fontWeight = FontWeight.Bold); Text(listOfNotNull(item.type, item.year?.toString()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable fun DetailsScreen(item: MediaItem?, onBack: () -> Unit, onSources: (MediaItem) -> Unit, onSeasons: () -> Unit) = Page("Detalhes", onBack) {
    if (item == null) return@Page EmptyState("Item não encontrado")
    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    item.description?.let { Text(it, Modifier.padding(vertical = 12.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ onSources(item) }) { Text("Selecionar fonte") }
        if (item.seasons.isNotEmpty()) OutlinedButton(onSeasons) { Text("Temporadas") }
    }
}

@Composable fun SeasonsScreen(item: MediaItem?, back: () -> Unit) = Page("Temporadas e episódios", back) {
    if (item?.seasons.isNullOrEmpty()) EmptyState("Nenhuma temporada disponível")
    else LazyColumn { item!!.seasons.forEach { season -> item { Text(season.title ?: "Temporada ${season.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }; items(season.episodes) { episode -> ListItem(headlineContent = { Text("${episode.number}. ${episode.title}") }, supportingContent = episode.description?.let { description -> { Text(description) } }) } } }
}

@Composable fun SourcesScreen(sources: List<MediaSource>, loading: Boolean, onBack: () -> Unit, play: (MediaSource) -> Unit) = Page("Fontes", onBack) {
    if (loading) CircularProgressIndicator()
    else if (sources.isEmpty()) EmptyState("Nenhuma fonte compatível foi retornada")
    else LazyColumn { items(sources, key = { it.id + it.url.hashCode() }) { source -> ListItem(headlineContent = { Text(source.name) }, modifier = Modifier.clickable { play(source) }, supportingContent = { Text(listOfNotNull(source.quality, source.description, source.format.name).joinToString(" • ")) }, trailingContent = { Text("Reproduzir", color = MaterialTheme.colorScheme.primary) }) } }
}

@Composable fun SettingsScreen() = Page("Configurações") {
    Text("HK IPTV Mobile Lab 0.1.0", fontWeight = FontWeight.Bold)
    Text("Cliente de testes independente. Dados e provedores permanecem neste dispositivo.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun EmptyState(message: String) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
