package com.sortr.data.vault

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.net.toUri
import com.sortr.data.crypto.BackupItemMeta
import com.sortr.data.crypto.KeyWrapper
import com.sortr.data.crypto.VaultBackupReader
import com.sortr.data.crypto.VaultBackupWriter
import com.sortr.data.crypto.VaultCipher
import com.sortr.data.crypto.VaultCryptoException
import com.sortr.data.db.PhotoEntity
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.data.db.VaultItemEntity
import com.sortr.data.db.toDomain
import com.sortr.data.di.ApplicationScope
import com.sortr.data.faces.FaceCropLoader
import com.sortr.data.media.MediaLocator
import com.sortr.data.media.MediaStoreSource
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.BackupImportResult
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.VaultItem
import com.sortr.domain.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves photos into the encrypted vault and back out.
 *
 * Each item has its own random AES-256 data key, stored wrapped by the Keystore master key.
 * The photo is streamed from MediaStore straight into an encrypted file, then decrypted again
 * and compared by SHA-256 before the vault row is written. Only verified items are offered for
 * deleting their gallery original.
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
    private val storage: VaultStorage,
    private val keyWrapper: KeyWrapper,
    private val media: MediaStoreSource,
    private val locator: MediaLocator,
    private val faceCrops: FaceCropLoader,
    @ApplicationScope scope: CoroutineScope,
) : VaultRepository {

    private val photoDao = db.photoDao()
    private val vaultDao = db.vaultDao()

    /** Serialises everything that creates or removes vault files. */
    private val itemMutex = Mutex()
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val backlog = MutableStateFlow(0)
    private val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override val encryptionBacklog: StateFlow<Int> = backlog.asStateFlow()
    override val encryptionFailures: Flow<Unit> = failures.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) {
            // Pick up photos whose encryption was interrupted, e.g. the app was killed.
            runCatching { photoDao.vaultedWithoutCopy() }
                .onSuccess { photos -> photos.forEach { enqueueId(it.mediaStoreId) } }
            for (photoId in queue) {
                try {
                    encryptIntoVault(photoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't move photo $photoId into the vault", e)
                    // Put it back so it isn't lost from sorting.
                    runCatching { photoDao.updateStatus(photoId, PhotoStatus.UNREVIEWED, System.currentTimeMillis()) }
                    failures.tryEmit(Unit)
                } finally {
                    backlog.update { it - 1 }
                }
            }
        }
    }

    override fun observeItems(): Flow<List<VaultItem>> =
        vaultDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePendingOriginals(): Flow<List<VaultItem>> =
        vaultDao.observePendingOriginals().map { rows -> rows.map { it.toDomain() } }

    override suspend fun pendingOriginals(): List<VaultItem> =
        vaultDao.pendingOriginals().map { it.toDomain() }

    override fun enqueue(photo: Photo) = enqueueId(photo.id)

    private fun enqueueId(photoId: Long) {
        backlog.update { it + 1 }
        queue.trySend(photoId)
    }

    private suspend fun encryptIntoVault(photoId: Long) = itemMutex.withLock {
        val photo = photoDao.byId(photoId) ?: return@withLock
        // Undone before its turn came, or already done.
        if (photo.status != PhotoStatus.VAULTED || vaultDao.bySource(photoId) != null) return@withLock
        // Face pictures cut from this photo, and text read from it, must not outlive it outside the vault.
        faceCrops.evict(photoId)
        db.libraryDao().deleteText(photoId)

        val base = storage.newBaseName()
        val photoFile = "$base.svf"
        val thumbnailFile = "$base.svt"
        val dataKey = VaultCipher.newDataKey()
        try {
            // The stored URI also covers photos read from hidden folders (e.g. WhatsApp's sent media).
            val uri = photo.uri.toUri()
            val digest = MessageDigest.getInstance("SHA-256")
            val iv = context.contentResolver.openInputStream(uri)?.use { input ->
                storage.file(photoFile).outputStream().buffered().use { out ->
                    VaultCipher.encrypt(DigestInputStream(input, digest), out, dataKey)
                }
            } ?: throw IOException("Couldn't open photo $photoId")
            val plainHash = digest.digest()

            // Read the encrypted copy back before trusting it.
            val decrypted = decryptFile(photoFile, dataKey)
            val check = MessageDigest.getInstance("SHA-256").digest(decrypted)
            if (!MessageDigest.isEqual(plainHash, check)) throw VaultCryptoException("Encrypted copy didn't verify")

            // Android has no ready thumbnail for photos outside the gallery; make one in memory.
            val thumbnail = systemThumbnail(uri) ?: thumbnailFromBytes(decrypted)
            if (thumbnail != null) {
                storage.file(thumbnailFile).outputStream().buffered().use { VaultCipher.encrypt(thumbnail, it, dataKey) }
            }

            vaultDao.insert(
                VaultItemEntity(
                    encryptedFilePath = photoFile,
                    thumbnailPath = thumbnailFile.takeIf { thumbnail != null },
                    originalName = photo.displayName,
                    mimeType = photo.mimeType,
                    originalSize = photo.size,
                    dateTaken = photo.dateTaken,
                    iv = iv,
                    wrappedKey = keyWrapper.wrap(dataKey),
                    addedAt = System.currentTimeMillis(),
                    sourceMediaStoreId = photoId,
                    contentSha256 = plainHash.toHex(),
                    width = photo.width,
                    height = photo.height,
                    originalRelativePath = photo.relativePath,
                ),
            )
        } catch (e: Exception) {
            storage.delete(photoFile)
            storage.delete(thumbnailFile)
            throw e
        } finally {
            dataKey.fill(0)
        }
    }

    override suspend fun addShared(uri: String, displayName: String?, mimeType: String?): Boolean = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            val base = storage.newBaseName()
            val photoFile = "$base.svf"
            val thumbnailFile = "$base.svt"
            val dataKey = VaultCipher.newDataKey()
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val iv = context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                    storage.file(photoFile).outputStream().buffered().use { out ->
                        VaultCipher.encrypt(DigestInputStream(input, digest), out, dataKey)
                    }
                } ?: throw IOException("Couldn't open the shared photo")
                val plainHash = digest.digest()
                val sha = plainHash.toHex()
                if (vaultDao.countWithHash(sha) > 0) {
                    storage.delete(photoFile)
                    return@withContext false
                }

                // Read the encrypted copy back before trusting it.
                val decrypted = decryptFile(photoFile, dataKey)
                if (!MessageDigest.isEqual(plainHash, MessageDigest.getInstance("SHA-256").digest(decrypted))) {
                    throw VaultCryptoException("Encrypted copy didn't verify")
                }
                val thumbnail = thumbnailFromBytes(decrypted)
                if (thumbnail != null) {
                    storage.file(thumbnailFile).outputStream().buffered().use { VaultCipher.encrypt(thumbnail, it, dataKey) }
                }
                val info = SharedPhotoInfo.read(decrypted)
                val now = System.currentTimeMillis()
                vaultDao.insert(
                    VaultItemEntity(
                        encryptedFilePath = photoFile,
                        thumbnailPath = thumbnailFile.takeIf { thumbnail != null },
                        originalName = displayName?.takeIf { it.isNotBlank() } ?: "IMG_$now.${info.extension}",
                        mimeType = mimeType?.takeIf { it.startsWith("image/") } ?: info.mimeType,
                        originalSize = decrypted.size.toLong(),
                        dateTaken = info.dateTaken ?: now,
                        iv = iv,
                        wrappedKey = keyWrapper.wrap(dataKey),
                        addedAt = now,
                        // Nothing in the gallery to remove afterwards: the original belongs to another app.
                        sourceMediaStoreId = null,
                        originalRemoved = true,
                        contentSha256 = sha,
                        width = info.width,
                        height = info.height,
                    ),
                )
                true
            } catch (e: Exception) {
                storage.delete(photoFile)
                storage.delete(thumbnailFile)
                throw e
            } finally {
                dataKey.fill(0)
            }
        }
    }

    override suspend fun cancel(photoId: Long) = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            // A queued job that hasn't run yet will see the restored status and skip the photo.
            val item = vaultDao.bySource(photoId) ?: return@withContext
            vaultDao.delete(item.id)
            deleteItemFiles(item)
        }
    }

    override suspend fun markOriginalsRemoved(itemIds: Collection<Long>) {
        itemIds.toList().chunked(SQL_CHUNK).forEach { vaultDao.markOriginalsRemoved(it) }
    }

    override suspend fun readDecrypted(itemId: Long, thumbnail: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val item = vaultDao.byId(itemId) ?: throw FileNotFoundException("No vault item $itemId")
        val file = if (thumbnail) item.thumbnailPath ?: item.encryptedFilePath else item.encryptedFilePath
        withDataKey(item) { key -> decryptFile(file, key) }
    }

    override suspend fun restoreToGallery(itemId: Long): Long = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            val item = vaultDao.byId(itemId) ?: throw FileNotFoundException("No vault item $itemId")

            // The original never left the gallery (its removal was declined or is still pending):
            // un-vault it instead of writing a second copy. Left as VAULTED, it would also be
            // encrypted again on the next start.
            val sourceId = item.sourceMediaStoreId.takeUnless { item.originalRemoved }
            if (sourceId != null && locator.existingIds(listOf(sourceId)).isNotEmpty()) {
                photoDao.updateStatus(sourceId, PhotoStatus.KEPT, System.currentTimeMillis())
                deleteItemFiles(item)
                vaultDao.delete(item.id)
                return@withContext sourceId
            }

            val bytes = withDataKey(item) { key -> decryptFile(item.encryptedFilePath, key) }

            val resolver = context.contentResolver
            // Back to the album it came from when Android allows it, otherwise Pictures/Sortr.
            val relativePath = item.originalRelativePath?.takeIf(AlbumPaths::isPhotoFolder)
                ?: "${Environment.DIRECTORY_PICTURES}/Sortr/"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create a gallery entry")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Couldn't write the photo")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                // Only our own half-written entry is removed here, never a user photo.
                resolver.delete(uri, null, null)
                throw e
            }

            val newId = ContentUris.parseId(uri)
            // Remember it as kept so the user isn't asked to sort it again. A zero modified time
            // makes the next gallery sync fill in the real metadata.
            val now = System.currentTimeMillis()
            photoDao.insertAll(
                listOf(
                    PhotoEntity(
                        mediaStoreId = newId,
                        uri = media.uriFor(newId).toString(),
                        displayName = item.originalName,
                        mimeType = item.mimeType,
                        dateTaken = item.dateTaken,
                        size = bytes.size.toLong(),
                        width = item.width,
                        height = item.height,
                        bucketName = AlbumPaths.displayName(relativePath),
                        relativePath = relativePath,
                        dateModified = 0,
                        status = PhotoStatus.KEPT,
                        statusChangedAt = now,
                    ),
                ),
            )
            deleteItemFiles(item)
            vaultDao.delete(item.id)
            newId
        }
    }

    override suspend fun delete(itemId: Long) = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            val item = vaultDao.byId(itemId) ?: return@withContext
            // Removing the row also discards the wrapped key, so any leftover file is unreadable.
            vaultDao.delete(item.id)
            deleteItemFiles(item)
        }
    }

    override suspend fun exportBackup(
        output: OutputStream,
        password: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            val items = vaultDao.all()
            onProgress(0, items.size)
            val writer = VaultBackupWriter(output, password, items.size)
            items.forEachIndexed { index, item ->
                withDataKey(item) { key ->
                    writer.writeItem(
                        meta = BackupItemMeta(
                            originalName = item.originalName,
                            mimeType = item.mimeType,
                            originalSize = item.originalSize,
                            dateTaken = item.dateTaken,
                            addedAt = item.addedAt,
                            width = item.width,
                            height = item.height,
                            sha256 = item.contentSha256,
                        ),
                        photo = decryptFile(item.encryptedFilePath, key),
                        thumbnail = item.thumbnailPath?.let { decryptFile(it, key) },
                    )
                }
                onProgress(index + 1, items.size)
            }
            writer.finish()
            items.size
        }
    }

    override suspend fun importBackup(
        input: InputStream,
        password: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): BackupImportResult = itemMutex.withLock {
        withContext(Dispatchers.IO) {
            val reader = VaultBackupReader(input, password)
            val total = reader.itemCount
            var imported = 0
            var skipped = 0
            onProgress(0, total)
            while (true) {
                val entry = reader.next() ?: break
                val sha = MessageDigest.getInstance("SHA-256").digest(entry.photo).toHex()
                if (vaultDao.countWithHash(sha) > 0) {
                    skipped++
                } else {
                    storeImported(entry.meta, entry.photo, entry.thumbnail ?: thumbnailFromBytes(entry.photo), sha)
                    imported++
                }
                onProgress(imported + skipped, total)
            }
            BackupImportResult(imported = imported, alreadyInVault = skipped)
        }
    }

    private suspend fun storeImported(meta: BackupItemMeta, photo: ByteArray, thumbnail: ByteArray?, sha: String) {
        val base = storage.newBaseName()
        val photoFile = "$base.svf"
        val thumbnailFile = "$base.svt"
        val dataKey = VaultCipher.newDataKey()
        try {
            val iv = storage.file(photoFile).outputStream().buffered().use { VaultCipher.encrypt(photo, it, dataKey) }
            if (thumbnail != null) {
                storage.file(thumbnailFile).outputStream().buffered().use { VaultCipher.encrypt(thumbnail, it, dataKey) }
            }
            vaultDao.insert(
                VaultItemEntity(
                    encryptedFilePath = photoFile,
                    thumbnailPath = thumbnailFile.takeIf { thumbnail != null },
                    originalName = meta.originalName,
                    mimeType = meta.mimeType,
                    originalSize = meta.originalSize,
                    dateTaken = meta.dateTaken,
                    iv = iv,
                    wrappedKey = keyWrapper.wrap(dataKey),
                    addedAt = meta.addedAt,
                    sourceMediaStoreId = null,
                    originalRemoved = true,
                    contentSha256 = sha,
                    width = meta.width,
                    height = meta.height,
                ),
            )
        } catch (e: Exception) {
            storage.delete(photoFile)
            storage.delete(thumbnailFile)
            throw e
        } finally {
            dataKey.fill(0)
        }
    }

    private inline fun <T> withDataKey(item: VaultItemEntity, block: (ByteArray) -> T): T {
        val key = keyWrapper.unwrap(item.wrappedKey)
        try {
            return block(key)
        } finally {
            key.fill(0)
        }
    }

    private fun decryptFile(name: String, key: ByteArray): ByteArray {
        val file = storage.file(name)
        return file.inputStream().buffered().use { VaultCipher.decrypt(it, key, sizeHint = file.length()) }
    }

    private fun deleteItemFiles(item: VaultItemEntity) {
        storage.delete(item.encryptedFilePath)
        storage.delete(item.thumbnailPath)
    }

    /** A small JPEG preview from the system's thumbnail cache, so the vault grid opens fast. */
    private fun systemThumbnail(uri: Uri): ByteArray? = try {
        context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null).toJpeg()
    } catch (e: Exception) {
        // The grid falls back to the full photo when there's no thumbnail.
        Log.w(TAG, "No thumbnail for $uri", e)
        null
    }

    /** Thumbnail for an imported photo. ImageDecoder applies the EXIF rotation. */
    private fun thumbnailFromBytes(photo: ByteArray): ByteArray? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(photo))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > THUMBNAIL_PX) {
                val scale = THUMBNAIL_PX.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.toJpeg()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't make a thumbnail for an imported photo", e)
        null
    }

    private fun Bitmap.toJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        recycle()
        return out.toByteArray()
    }

    // Locale.ROOT: in Arabic the default locale would write Arabic-Indic digits, and the same photo
    // would no longer match its stored hash.
    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    private companion object {
        const val TAG = "VaultRepository"
        const val THUMBNAIL_PX = 512
    }
}
