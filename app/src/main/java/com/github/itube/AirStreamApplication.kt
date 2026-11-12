package com.github.itube

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import org.schabi.newpipe.extractor.NewPipe

class AirStreamApplication : Application(), ImageLoaderFactory {

    /** The Shorts feed, players and bank: one per process, outliving any window. */
    val shorts by lazy { ShortsState(this, kotlinx.coroutines.MainScope()) }

    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
        NewPipe.init(NewPipeDownloader.instance)
        // The iOS client only adds HLS livestream formats; AirStream plays VOD, so skip that request.
        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)
        ThemeSettings.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AudioOnly.init(this)
        PlayerControls.init(this)
        CaptionSettings.init(this)
        CaptionTranslator.refresh()
        SearchHistory.init(this)
        ListeningProfile.init(this)
        MusicCacheManager.init(this)
        DownloadLibrary.init(this)
        LikedSongsStore.init(this)
        SavedCollections.init(this)
        ResumeStore.init(this)
        SavedSession.init(this)
        Account.init(this)
        Bells.init(this)
        NewVideos.schedule(this)
        SponsorBlock.init(this)
        ShortsSetting.init(this)
        com.github.itube.ui.components.BlurSetting.init(this)
        ShortsBank.init(this)
        com.github.itube.backend.EqualizerManager.init(this)
        AppUpdate.init(this)
        // Stream URLs are bound to the network they were fetched on: forget them on a switch.
        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }

    // Thumbnails are in every list and the player; a short crossfade replaces pop-in.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(180)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()
}
