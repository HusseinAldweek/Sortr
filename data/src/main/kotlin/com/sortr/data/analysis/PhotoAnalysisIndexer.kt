package com.sortr.data.analysis

import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.model.AnalysisProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Works through photos that haven't been checked for duplicates and blur yet, a batch at a
 * time. Each batch is saved as soon as it's done, so stopping loses at most one batch.
 */
@Singleton
class PhotoAnalysisIndexer @Inject constructor(
    private val db: SortrDatabase,
    private val analyzer: PhotoAnalyzer,
) {
    private val libraryDao = db.libraryDao()

    suspend fun progress(): AnalysisProgress =
        libraryDao.analysisProgress().let { AnalysisProgress(analysed = it.analysed, total = it.total) }

    /** Analyses the next batch. Returns how many photos it handled; 0 means everything is done. */
    suspend fun analyseNextBatch(batchSize: Int = BATCH_SIZE): Int {
        val photos = libraryDao.unanalysed(batchSize)
        if (photos.isEmpty()) return 0
        val results = photos.map { photo ->
            coroutineContext.ensureActive()
            photo to withContext(Dispatchers.Default) { analyzer.analyze(photo.uri.toUri()) }
        }
        db.withTransaction {
            results.forEach { (photo, result) ->
                // Photos that can't be read get −1 so they aren't retried forever or flagged as blurry.
                libraryDao.setAnalysis(photo.mediaStoreId, result?.hash, result?.blurScore ?: UNREADABLE)
                // Without a MediaStore capture date the photo was filed under the day it was
                // added to the phone. Its own date wins when it's clearly different; a real
                // MediaStore date is never more than a time-zone difference away from it.
                val exifDate = result?.exifDateTaken
                if (exifDate != null && abs(exifDate - photo.dateTaken) > DATE_TOLERANCE_MS) {
                    libraryDao.setDateTaken(photo.mediaStoreId, exifDate)
                }
            }
        }
        Log.d(TAG, "Analysed ${photos.size} photos")
        return photos.size
    }

    private companion object {
        const val TAG = "PhotoAnalysis"
        const val BATCH_SIZE = 32
        const val UNREADABLE = -1.0
        const val DATE_TOLERANCE_MS = 26L * 60 * 60 * 1000
    }
}
