package io.vela

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import io.vela.core.ui.image.AppIconFetcher
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import io.vela.core.ui.sound.UiSounds

@HiltAndroidApp
class VelaApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var okHttpClient: OkHttpClient

    /** Interface sounds; created on first use so app start stays quiet and cheap. */
    val uiSounds: UiSounds by lazy { UiSounds(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    /**
     * One image loader for the whole app: generous memory cache for thousands of thumbnails,
     * a disk cache for anything remote, app-icon fetcher, and video frame posters.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(AppIconFetcher.Factory(this@VelaApplication))
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, 0.30).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(cacheDir, "images"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
