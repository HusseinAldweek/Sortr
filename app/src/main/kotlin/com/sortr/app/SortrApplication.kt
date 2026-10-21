package com.sortr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.sortr.app.faces.ScanNotifications
import com.sortr.app.goal.GoalNotifications
import com.sortr.app.image.FaceCropFetcher
import com.sortr.app.image.FaceCropKeyer
import com.sortr.app.image.MediaThumbnailFetcher
import com.sortr.app.image.MediaThumbnailKeyer
import com.sortr.app.vault.VaultLock
import com.sortr.app.widget.WidgetUpdater
import com.sortr.data.faces.FaceCropLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SortrApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var vaultLock: VaultLock

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var faceCropLoader: FaceCropLoader

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Lock the vault whenever the app leaves the screen.
        ProcessLifecycleOwner.get().lifecycle.addObserver(vaultLock)
        ScanNotifications.createChannel(this)
        GoalNotifications.createChannel(this)
        widgetUpdater.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailKeyer())
                add(MediaThumbnailFetcher.Factory(contentResolver))
                add(FaceCropKeyer())
                add(FaceCropFetcher.Factory(faceCropLoader))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            // Every image is already a local file, so a disk cache would only duplicate them.
            // Face pictures have their own small cache (FaceCropLoader).
            .diskCache(null)
            .crossfade(true)
            .build()
}
