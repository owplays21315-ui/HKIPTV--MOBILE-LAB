package com.hkiptv.lab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hkiptv.lab.data.model.*
import com.hkiptv.lab.data.remote.ProviderHttpClient
import com.hkiptv.lab.data.repository.ProviderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(val providers: List<StoredProvider> = emptyList(), val items: List<MediaItem> = emptyList(), val selected: MediaItem? = null, val sources: List<MediaSource> = emptyList(), val selectedSource: MediaSource? = null, val loading: Boolean = false, val error: String? = null)

class AppViewModel(private val repository: ProviderRepository, private val http: ProviderHttpClient) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state = combine(mutable, repository.providers) { ui, providers -> ui.copy(providers = providers) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
    fun addProvider(url: String) = task { repository.add(url) }
    fun editProvider(old: String, new: String) = task { repository.edit(old, new) }
    fun removeProvider(url: String) = task { repository.remove(url) }
    fun enableProvider(url: String, enabled: Boolean) = task { repository.setEnabled(url, enabled) }
    fun makePrimary(url: String) = task { repository.setPrimary(url) }
    fun loadCatalog() = task { primary()?.let { mutable.update { state -> state.copy(items = http.catalog(it).items) } } ?: error("Adicione e ative um provedor") }
    fun search(query: String) = task { primary()?.let { mutable.update { state -> state.copy(items = http.search(it, query).items) } } ?: error("Adicione e ative um provedor") }
    fun select(item: MediaItem) {
        mutable.update { it.copy(selected = item, sources = emptyList()) }
        val metadataProvider = state.value.providers.firstOrNull { it.enabled && it.primary && it.manifest.offersMetadata }
            ?: state.value.providers.firstOrNull { it.enabled && it.manifest.offersMetadata }
        metadataProvider?.let { provider -> task { mutable.update { current -> current.copy(selected = http.metadata(provider, item.type, item.id)) } } }
    }
    fun loadSources(type: String, id: String) = task {
        val providers = state.value.providers.filter { it.enabled && it.manifest.offersSources }
        if (providers.isEmpty()) error("Nenhum provedor ativo oferece fontes")
        val attempts = providers.map { provider -> runCatching { http.sources(provider, type, id).sources } }
        val results = attempts.mapNotNull { it.getOrNull() }.flatten()
        mutable.update { current -> current.copy(sources = results) }
        if (results.isEmpty()) throw attempts.firstNotNullOfOrNull { it.exceptionOrNull() } ?: error("Nenhuma fonte foi retornada pelos provedores ativos")
    }
    fun selectSource(source: MediaSource) { mutable.update { it.copy(selectedSource = source) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun primary() = state.value.providers.firstOrNull { it.primary && it.enabled }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(loading = true, error = null) }
        runCatching { block() }.onFailure { error -> mutable.update { it.copy(error = error.message ?: "Erro inesperado") } }
        mutable.update { it.copy(loading = false) }
    }
    companion object {
        fun factory(repository: ProviderRepository, http: ProviderHttpClient) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, http) as T
        }
    }
}
