package com.sortr.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sortr.data.db.PhotoEntity
import com.sortr.data.db.PhotoTextEntity
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.analysis.ImageSignals
import com.sortr.domain.analysis.SmartCategories
import com.sortr.domain.model.AnalysisProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads photos with ML Kit's bundled image-labelling and text-recognition models, a batch at a
 * time: the labels and text become categories (food, pets, documents…), and the text is saved
 * for searching. Everything runs on the phone.
 */
@Singleton
class SmartIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SortrDatabase,
) {
    private val libraryDao = db.libraryDao()
    private val dispatcher = Dispatchers.Default.limitedParallelism(PARALLEL_PHOTOS)
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_LABEL_CONFIDENCE).build())
    }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun progress(): AnalysisProgress = libraryDao.smartProgress().let { AnalysisProgress(analysed = it.analysed, total = it.total) }

    /** Reads the next batch. Returns how many photos it handled; 0 means everything is done. */
    suspend fun indexNextBatch(batchSize: Int = BATCH_SIZE): Int {
        val photos = libraryDao.unclassified(batchSize)
        if (photos.isEmpty()) {
            libraryDao.deleteOrphanText()
            return 0
        }
        val results = coroutineScope {
            photos.map { photo -> async(dispatcher) { photo to read(photo) } }.awaitAll()
        }
        db.withTransaction {
            results.forEach { (photo, result) ->
                // Unreadable photos get 0 (no categories) so they aren't retried forever.
                libraryDao.setSmartCategories(photo.mediaStoreId, result?.mask ?: 0)
                val text = result?.text.orEmpty()
                if (text.isNotBlank()) libraryDao.insertText(PhotoTextEntity(photo.mediaStoreId, text)) else libraryDao.deleteText(photo.mediaStoreId)
            }
        }
        Log.d(TAG, "Read ${photos.size} photos")
        return photos.size
    }

    private class Result(val mask: Int, val text: String)

    private fun read(photo: PhotoEntity): Result? {
        val bitmap = decode(photo.uri.toUri()) ?: return null
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = Tasks.await(labeler.process(image)).associate { it.text to it.confidence }
            val text = Tasks.await(recognizer.process(image)).textBlocks.joinToString("\n") { it.text }.trim()
            Result(SmartCategories.classify(ImageSignals(labels, text, photo.relativePath, photo.displayName)), text)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read ${photo.mediaStoreId}", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** A copy at most [MAX_SIDE] pixels on its longest side, enough for labels and normal-sized text. */
    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't decode $uri", e)
        null
    }

    private companion object {
        const val TAG = "SmartIndexer"
        const val BATCH_SIZE = 24
        const val PARALLEL_PHOTOS = 2
        const val MAX_SIDE = 1600
        const val MIN_LABEL_CONFIDENCE = 0.5f
    }
}
