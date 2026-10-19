package com.sortr.domain.repository

import com.sortr.domain.model.BackupImportResult
import com.sortr.domain.model.Photo
import com.sortr.domain.model.VaultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

interface VaultRepository {

    fun observeItems(): Flow<List<VaultItem>>

    /** Vault items whose gallery original hasn't been removed yet. */
    fun observePendingOriginals(): Flow<List<VaultItem>>

    suspend fun pendingOriginals(): List<VaultItem>

    /** Photos waiting to be encrypted. */
    val encryptionBacklog: StateFlow<Int>

    /** Emits when a photo couldn't be encrypted and was put back into the gallery flow. */
    val encryptionFailures: Flow<Unit>

    /** Queues a photo (already marked as vaulted) for encryption. */
    fun enqueue(photo: Photo)

    /**
     * Undoes moving a photo into the vault. Call after the photo's status has been put back, and
     * only while its original is still in the gallery: waits for any encryption in progress,
     * then deletes the vault copy.
     */
    suspend fun cancel(photoId: Long)

    /** Records that these items' gallery originals were deleted. */
    suspend fun markOriginalsRemoved(itemIds: Collection<Long>)

    /** Decrypts into memory. Nothing decrypted is ever written to disk. */
    suspend fun readDecrypted(itemId: Long, thumbnail: Boolean): ByteArray

    /** Saves the photo back to the gallery and removes it from the vault. Returns the new gallery id. */
    suspend fun restoreToGallery(itemId: Long): Long

    suspend fun delete(itemId: Long)

    /** Writes every vault item to [output], encrypted with [password]. Returns the item count. */
    suspend fun exportBackup(
        output: OutputStream,
        password: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int

    /** Adds the items in a backup to the vault, skipping any already present. */
    suspend fun importBackup(
        input: InputStream,
        password: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): BackupImportResult
}
