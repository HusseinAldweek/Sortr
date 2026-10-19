package com.sortr.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.sortr.app.image.MediaThumbnailFetcher
import com.sortr.app.image.MediaThumbnailKeyer
import com.sortr.app.vault.VaultLock
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SortrApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var vaultLock: VaultLock

    override fun onCreate() {
        super.onCreate()
        // Lock the vault whenever the app leaves the screen.
        ProcessLifecycleOwner.get().lifecycle.addObserver(vaultLock)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailKeyer())
                add(MediaThumbnailFetcher.Factory(contentResolver))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            // Every image is already a local file, so a disk cache would only duplicate them.
            .diskCache(null)
            .crossfade(true)
            .build()
}
