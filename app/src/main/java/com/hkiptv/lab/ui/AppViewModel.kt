package com.hkiptv.lab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.local.LabSettingsStorage
import com.hkiptv.lab.data.remote.ProviderHttpClient
import com.hkiptv.lab.data.repository.ProviderRepository
import com.hkiptv.lab.data.repository.ProviderRouting
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(val providers: List<StoredProvider> = emptyList(), val items: List<MediaItem> = emptyList(), val selected: MediaItem? = null, val sources: List<MediaSource> = emptyList(), val selectedSource: MediaSource? = null, val experimentalHttpPlayback: Boolean = false, val loading: Boolean = false, val error: String? = null)

class AppViewModel(private val repository: ProviderRepository, private val http: ProviderHttpClient, private val settings: LabSettingsStorage) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state = combine(mutable, repository.providers, settings.experimentalHttpPlayback) { ui, providers, experimentalHttp -> ui.copy(providers = providers, experimentalHttpPlayback = experimentalHttp) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
    init { viewModelScope.launch { repository.refreshManifests() } }
    fun addProvider(url: String) = task { repository.add(url) }
    fun editProvider(old: String, new: String) = task { repository.edit(old, new) }
    fun removeProvider(url: String) = task { repository.remove(url) }
    fun enableProvider(url: String, enabled: Boolean) = task { repository.setEnabled(url, enabled) }
    fun makePrimary(url: String) = task { repository.setPrimary(url) }
    fun loadCatalog() = task { primary()?.let { mutable.update { state -> state.copy(items = http.catalog(it).items) } } ?: error("Adicione e ative um provedor") }
    fun search(query: String) = task { primary()?.let { mutable.update { state -> state.copy(items = http.search(it, query).items) } } ?: error("Adicione e ative um provedor") }
    fun select(item: MediaItem) {
        mutable.update { it.copy(selected = item, sources = emptyList()) }
        val targets = ProviderRouting.targets(state.value.providers, "meta", item)
        val target = targets.firstOrNull { it.provider.primary } ?: targets.firstOrNull()
        target?.let { selected -> task { mutable.update { current -> current.copy(selected = http.metadata(selected.provider, item, selected.identifier)) } } }
    }
    fun loadSources(item: MediaItem) = task {
        val providers = state.value.providers.filter { it.enabled && it.manifest.offersSources }
        if (providers.isEmpty()) error("Nenhum provedor ativo oferece fontes")
        val targets = ProviderRouting.targets(providers, "stream", item)
        if (targets.isEmpty()) {
            val kinds = buildList { add(item.id); addAll(item.identifiers.values) }.map { ProviderRouting.identifierKind(it).label }.distinct().joinToString("/")
            error("Nenhum provedor de fontes aceita o tipo ${item.type} com identificador $kinds")
        }
        val attempts = targets.map { target -> runCatching { http.sources(target.provider, item.type, target.identifier).sources } }
        val results = attempts.mapNotNull { it.getOrNull() }.flatten()
        mutable.update { current -> current.copy(sources = results) }
        if (results.isEmpty()) throw attempts.firstNotNullOfOrNull { it.exceptionOrNull() } ?: error("Nenhuma fonte foi retornada pelos provedores ativos")
    }
    fun selectSource(source: MediaSource) { mutable.update { it.copy(selectedSource = source) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun setExperimentalHttpPlayback(enabled: Boolean) = viewModelScope.launch { settings.setExperimentalHttpPlayback(enabled) }
    private fun primary() = state.value.providers.firstOrNull { it.primary && it.enabled }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        runCatching { block() }.onFailure { error -> mutable.update { it.copy(error = error.message ?: "Erro inesperado") } }
        mutable.update { it.copy(loading = false) }
    }
    companion object {
        fun factory(repository: ProviderRepository, http: ProviderHttpClient, settings: LabSettingsStorage) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, http, settings) as T
        }
    }
}
