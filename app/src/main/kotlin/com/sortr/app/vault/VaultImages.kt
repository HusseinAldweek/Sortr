package com.sortr.app.vault

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.Options
import com.sortr.data.di.ApplicationScope
import com.sortr.domain.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import okio.Buffer
import okio.FileSystem
import javax.inject.Inject
import javax.inject.Singleton

/** Coil model for a vault photo. Decrypted in memory only, and only while the vault is open. */
data class VaultImage(val itemId: Long, val thumbnail: Boolean)

/**
 * A separate image loader for vault photos: memory cache only (no disk cache), cleared the
 * moment the vault locks.
 */
@Singleton
class VaultImages @Inject constructor(
    @ApplicationContext context: Context,
    vault: VaultRepository,
    vaultLock: VaultLock,
    @ApplicationScope scope: CoroutineScope,
) {
    val loader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(VaultImageKeyer())
            add(VaultImageFetcher.Factory(vault, vaultLock))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.15)
                .build()
        }
        .diskCache(null)
        .build()

    init {
        scope.launch {
            vaultLock.unlocked.filter { !it }.collect { loader.memoryCache?.clear() }
        }
    }
}

private class VaultImageFetcher(
    private val data: VaultImage,
    private val vault: VaultRepository,
    private val vaultLock: VaultLock,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        check(vaultLock.isUnlocked) { "Vault is locked" }
        val bytes = vault.readDecrypted(data.itemId, data.thumbnail)
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(
        private val vault: VaultRepository,
        private val vaultLock: VaultLock,
    ) : Fetcher.Factory<VaultImage> {
        override fun create(data: VaultImage, options: Options, imageLoader: ImageLoader): Fetcher =
            VaultImageFetcher(data, vault, vaultLock)
    }
}

private class VaultImageKeyer : Keyer<VaultImage> {
    override fun key(data: VaultImage, options: Options): String = "vault:${data.itemId}:${data.thumbnail}"
}
