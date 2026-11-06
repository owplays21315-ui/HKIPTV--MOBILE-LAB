package com.hkiptv.lab.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.labSettingsDataStore by preferencesDataStore("lab_settings")

interface LabSettingsStorage {
    val experimentalHttpPlayback: Flow<Boolean>
    suspend fun setExperimentalHttpPlayback(enabled: Boolean)
}

class DataStoreLabSettingsStorage(private val context: Context) : LabSettingsStorage {
    private val experimentalHttpKey = booleanPreferencesKey("experimental_http_playback")

    override val experimentalHttpPlayback = context.labSettingsDataStore.data.map { preferences ->
        preferences[experimentalHttpKey] ?: false
    }

    override suspend fun setExperimentalHttpPlayback(enabled: Boolean) {
        context.labSettingsDataStore.edit { it[experimentalHttpKey] = enabled }
    }
}
