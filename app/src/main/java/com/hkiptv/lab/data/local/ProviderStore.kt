package com.hkiptv.lab.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hkiptv.lab.data.model.StoredProvider
import com.hkiptv.lab.data.remote.protocolJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.providerDataStore by preferencesDataStore("providers")

interface ProviderStorage {
    val providers: Flow<List<StoredProvider>>
    suspend fun save(providers: List<StoredProvider>)
}

class DataStoreProviderStorage(private val context: Context) : ProviderStorage {
    private val key = stringPreferencesKey("provider_list")
    override val providers = context.providerDataStore.data.map { preferences ->
        preferences[key]?.let { runCatching { protocolJson.decodeFromString<List<StoredProvider>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }
    override suspend fun save(providers: List<StoredProvider>) { context.providerDataStore.edit { it[key] = protocolJson.encodeToString(providers) } }
}
