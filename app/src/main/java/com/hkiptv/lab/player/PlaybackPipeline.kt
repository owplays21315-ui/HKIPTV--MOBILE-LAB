package com.hkiptv.lab.player

import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.util.UnstableApi
import com.hkiptv.lab.data.model.MediaSource
import okhttp3.OkHttpClient

data class PlaybackSettingState(val experimentalHttpEnabled: Boolean, val loaded: Boolean)

data class PlaybackPipeline(
    val httpClient: OkHttpClient,
    val dataSourceFactory: OkHttpDataSource.Factory,
    val mediaSourceFactory: DefaultMediaSourceFactory
)

@UnstableApi
object PlaybackPipelineFactory {
    fun create(
        source: MediaSource,
        setting: PlaybackSettingState,
        observer: PlaybackNetworkObserver,
        basePolicy: PlaybackRedirectPolicy = PlaybackRedirectPolicy(),
        configureClient: (OkHttpClient.Builder.() -> Unit)? = null
    ): PlaybackPipeline? {
        if (!setting.loaded) return null
        val policy = basePolicy.copy(experimentalHttpEnabled = setting.experimentalHttpEnabled)
        val client = PlaybackHttpClient.create(source, observer, policy, configureClient)
        val dataSourceFactory = OkHttpDataSource.Factory(client)
        return PlaybackPipeline(client, dataSourceFactory, DefaultMediaSourceFactory(dataSourceFactory))
    }
}
