package com.sortr.data.analysis

import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.sortr.data.db.SortrDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fingerprints files that could be exact copies of each other, a batch at a time. Only files
 * whose size matches another file's are read, which on most phones is a small part of the
 * library.
 */
@Singleton
class ExactDuplicateIndexer @Inject constructor(
    private val db: SortrDatabase,
    private val fingerprint: ContentFingerprint,
) {
    private val libraryDao = db.libraryDao()
    private val dispatcher = Dispatchers.IO.limitedParallelism(PARALLEL_FILES)

    suspend fun pending(): Int = libraryDao.unfingerprintedCount()

    /** Fingerprints the next batch. Returns how many files it handled; 0 means everything is done. */
    suspend fun fingerprintNextBatch(batchSize: Int = BATCH_SIZE): Int {
        val files = libraryDao.unfingerprinted(batchSize)
        if (files.isEmpty()) return 0
        val results = coroutineScope {
            files.map { file -> async(dispatcher) { file.mediaStoreId to fingerprint.of(file.uri.toUri(), file.size) } }.awaitAll()
        }
        // "" marks unreadable files so they aren't tried again on every run.
        db.withTransaction { results.forEach { (id, hash) -> libraryDao.setContentHash(id, hash.orEmpty()) } }
        Log.d(TAG, "Fingerprinted ${files.size} files")
        return files.size
    }

    private companion object {
        const val TAG = "ExactDuplicates"
        const val BATCH_SIZE = 32
        const val PARALLEL_FILES = 2
    }
}
