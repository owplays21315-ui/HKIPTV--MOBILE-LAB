package com.hkiptv.lab

import android.app.Application
import com.hkiptv.lab.data.local.DataStoreProviderStorage
import com.hkiptv.lab.data.local.DataStoreLabSettingsStorage
import com.hkiptv.lab.data.remote.ProviderHttpClient
import com.hkiptv.lab.data.repository.ProviderRepository

class HkIptvApplication : Application() {
    val httpClient by lazy { ProviderHttpClient() }
    val providerRepository by lazy { ProviderRepository(DataStoreProviderStorage(this), httpClient) }
    val labSettings by lazy { DataStoreLabSettingsStorage(this) }
}
