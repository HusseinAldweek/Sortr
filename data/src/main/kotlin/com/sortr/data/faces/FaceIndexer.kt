package com.sortr.data.faces

import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.sortr.data.db.FaceEntity
import com.sortr.data.db.PersonEntity
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.faces.Centroid
import com.sortr.domain.faces.FaceClusterer
import com.sortr.domain.faces.FaceMath
import com.sortr.domain.faces.FaceSample
import com.sortr.domain.faces.Placement
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.repository.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Serialises everything that changes faces or people: the indexer and the user's edits. */
@Singleton
class FaceLock @Inject constructor() {
    val mutex = Mutex()
}

/**
 * Scans photos for faces in small batches. Each batch is analysed first (slow, no lock held),
 * then grouped and saved in one transaction, so stopping at any point loses at most one batch
 * and the next run carries on from there.
 */
@Singleton
class FaceIndexer @Inject constructor(
    private val db: SortrDatabase,
    private val analyzer: FaceAnalyzer,
    private val lock: FaceLock,
    private val settings: AppSettings,
    private val crops: FaceCropLoader,
) {
    private val photoDao = db.photoDao()
    private val faceDao = db.faceDao()

    suspend fun progress(): FaceIndexProgress =
        photoDao.indexProgress().let { FaceIndexProgress(indexed = it.scanned, total = it.total) }

    /**
     * Drops people whose faces have all gone, e.g. because the photos were deleted, and cached
     * face pictures that can no longer be shown.
     */
    suspend fun cleanUp() {
        val shown = lock.mutex.withLock {
            faceDao.deleteEmptyPeople()
            faceDao.shownFaceIds().toSet()
        }
        crops.prune(shown)
    }

    /** Scans the next batch. Returns how many photos it handled; 0 means everything is done. */
    suspend fun indexNextBatch(batchSize: Int = BATCH_SIZE): Int {
        val photos = photoDao.unindexed(batchSize)
        if (photos.isEmpty()) return 0

        val analysed = photos.map { photo ->
            coroutineContext.ensureActive()
            photo.mediaStoreId to withContext(Dispatchers.Default) { analyzer.analyze(photo.uri.toUri()) }
        }
        val threshold = settings.faceSimilarityThreshold.first()

        lock.mutex.withLock {
            db.withTransaction {
                // Photos may have been trashed, vaulted or deleted while they were being analysed.
                val scannable = photoDao.stillScannable(analysed.map { it.first }).toSet()
                val faces = analysed.filter { it.first in scannable }.flatMap { (photoId, found) -> found.map { photoId to it } }
                if (faces.isNotEmpty()) saveFaces(faces, threshold)
                photoDao.markIndexed(photos.map { it.mediaStoreId })
            }
        }
        Log.d(TAG, "Indexed ${photos.size} photos, ${analysed.sumOf { it.second.size }} faces")
        return photos.size
    }

    private suspend fun saveFaces(faces: List<Pair<Long, DetectedFace>>, threshold: Float) {
        val centroids = faceDao.centroids()
            .map { Centroid(it.id, FaceMath.decode(it.averageEmbedding), it.faceCount) }
            .toMutableList()
        val placements = FaceClusterer(threshold).placeAll(
            faces.map { (_, face) -> FaceSample(face.embedding, face.quality) },
            centroids,
        )

        // New people have negative temporary ids until they're saved.
        val savedIds = HashMap<Long, Long>()
        for (centroid in centroids) {
            if (centroid.personId < 0) {
                savedIds[centroid.personId] = faceDao.insertPerson(
                    PersonEntity(
                        name = null,
                        averageEmbedding = FaceMath.encode(centroid.mean),
                        coverFaceId = null,
                        faceCount = centroid.count,
                    ),
                )
            }
        }
        val grown = placements.filterIsInstance<Placement.Existing>().map { it.personId }.filter { it > 0 }.toSet()
        centroids.filter { it.personId in grown }.forEach {
            faceDao.updateCentroid(it.personId, FaceMath.encode(it.mean), it.count)
        }

        faceDao.insertFaces(
            faces.mapIndexed { index, (photoId, face) ->
                val placement = placements[index]
                val personId = when (placement) {
                    is Placement.Existing -> savedIds[placement.personId] ?: placement.personId
                    is Placement.NewPerson -> savedIds.getValue(placement.personId)
                    is Placement.Unsorted -> null
                }
                FaceEntity(
                    photoId = photoId,
                    left = face.left,
                    top = face.top,
                    right = face.right,
                    bottom = face.bottom,
                    embedding = FaceMath.encode(face.embedding),
                    personId = personId,
                    confidence = placement.similarity.coerceAtLeast(0f),
                    quality = face.quality.ordinal,
                )
            },
        )
    }

    private companion object {
        const val TAG = "FaceIndexer"
        const val BATCH_SIZE = 16
    }
}
