package com.sortr.data.repository

import androidx.room.withTransaction
import com.sortr.data.db.BOX_SCALE
import com.sortr.data.db.FaceRefRow
import com.sortr.data.db.PersonEntity
import com.sortr.data.db.PersonRow
import com.sortr.data.db.SortrDatabase
import com.sortr.data.faces.FaceLock
import com.sortr.data.faces.FaceModel
import com.sortr.domain.faces.Centroid
import com.sortr.domain.faces.FaceClusterer
import com.sortr.domain.faces.FaceMath
import com.sortr.domain.faces.FaceQuality
import com.sortr.domain.faces.FaceSample
import com.sortr.domain.faces.Placement
import com.sortr.domain.model.FaceBox
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person
import com.sortr.domain.repository.PeopleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepositoryImpl @Inject constructor(
    private val db: SortrDatabase,
    private val lock: FaceLock,
    model: FaceModel,
) : PeopleRepository {

    private val faceDao = db.faceDao()
    private val photoDao = db.photoDao()

    override val modelState: StateFlow<FaceModelState> = model.state

    override fun observePeople(includeHidden: Boolean): Flow<List<Person>> =
        faceDao.observePeople(includeHidden).map { rows -> rows.map { it.toDomain() } }

    override fun observePerson(personId: Long): Flow<Person?> =
        faceDao.observePerson(personId).map { it?.toDomain() }

    override fun observeFaces(personId: Long): Flow<List<FaceRef>> =
        faceDao.observeFacesOf(personId).map { rows -> rows.map { it.toDomain() } }

    override fun observeUnsortedFaces(): Flow<List<FaceRef>> =
        faceDao.observeUnsorted().map { rows -> rows.map { it.toDomain() } }

    override fun observeIndexProgress(): Flow<FaceIndexProgress> =
        photoDao.observeIndexProgress().map { FaceIndexProgress(indexed = it.scanned, total = it.total) }

    override suspend fun rename(personId: Long, name: String?) = edit {
        faceDao.rename(personId, name?.trim()?.ifEmpty { null })
    }

    override suspend fun setHidden(personId: Long, hidden: Boolean) = edit {
        faceDao.setHidden(personId, hidden)
    }

    override suspend fun merge(targetId: Long, otherIds: Collection<Long>) = edit {
        val others = otherIds.filter { it != targetId }.distinct()
        if (others.isEmpty()) return@edit
        val target = faceDao.person(targetId) ?: return@edit
        val name = target.name ?: others.firstNotNullOfOrNull { faceDao.person(it)?.name }
        faceDao.reassignPeople(others, targetId)
        // "Not this person" on any of them now means "not the merged person".
        faceDao.moveRejections(others, targetId)
        faceDao.deletePeople(others)
        recalculate(targetId)
        faceDao.rename(targetId, name)
    }

    override suspend fun removeFaces(personId: Long, faceIds: Collection<Long>) = edit {
        val ids = faceDao.faceVectors(faceIds.toList()).filter { it.personId == personId }.map { it.id }
        if (ids.isEmpty()) return@edit
        faceDao.reject(personId, ids)
        if (recalculate(personId)) faceDao.pin(personId)
    }

    override suspend fun moveFaces(faceIds: Collection<Long>, personId: Long?): Long = edit {
        val faces = faceDao.faceVectors(faceIds.toList())
        require(faces.isNotEmpty()) { "No faces to move" }
        val target = personId ?: faceDao.insertPerson(
            PersonEntity(
                name = null,
                averageEmbedding = faces.first().embedding,
                coverFaceId = null,
                faceCount = 0,
                pinned = true,
            ),
        )
        faces.forEach { faceDao.assign(it.id, target, 1f) }
        faces.mapNotNull { it.personId }.filter { it != target }.toSet().forEach { recalculate(it) }
        recalculate(target)
        faceDao.pin(target)
        target
    }

    override suspend fun regroup(threshold: Float) {
        lock.mutex.withLock {
            withContext(Dispatchers.Default) {
                val pinned = faceDao.centroids().filter { it.pinned }.map { it.id }.toSet()
                val faces = faceDao.allFaceVectors()
                val (keep, free) = faces.partition { it.personId != null && it.personId in pinned }

                // People the user worked on keep their faces and act as fixed anchors.
                val centroids = keep.groupBy { it.personId!! }
                    .map { (id, members) -> Centroid.of(id, members.map { FaceMath.decode(it.embedding) }) }
                    .toMutableList()
                val placements = FaceClusterer(threshold).placeAll(
                    free.map { FaceSample(FaceMath.decode(it.embedding), qualityOf(it.quality), it.rejectedPersonId) },
                    centroids,
                )

                // Compute first, then write everything in one go so a cancelled regroup changes nothing.
                withContext(NonCancellable) {
                    db.withTransaction {
                        faceDao.deleteUnpinnedPeople()
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
                            } else {
                                faceDao.updateCentroid(centroid.personId, FaceMath.encode(centroid.mean), centroid.count)
                            }
                        }
                        free.forEachIndexed { index, face ->
                            val placement = placements[index]
                            val personId = when (placement) {
                                is Placement.Existing -> savedIds[placement.personId] ?: placement.personId
                                is Placement.NewPerson -> savedIds.getValue(placement.personId)
                                is Placement.Unsorted -> null
                            }
                            faceDao.assign(face.id, personId, placement.similarity.coerceAtLeast(0f))
                        }
                        faceDao.deleteEmptyPeople()
                    }
                }
            }
        }
    }

    override suspend fun resetAll() = edit {
        faceDao.deleteAllFaces()
        faceDao.deleteAllPeople()
        photoDao.resetIndexed()
    }

    /** Runs a people edit under the face lock, in one transaction. */
    private suspend fun <T> edit(block: suspend () -> T): T =
        lock.mutex.withLock { db.withTransaction { block() } }

    /**
     * Rebuilds a person's average from the faces they actually have now. Deletes the person and
     * returns false if none are left.
     */
    private suspend fun recalculate(personId: Long): Boolean {
        val members = faceDao.faceVectorsOf(personId)
        if (members.isEmpty()) {
            faceDao.deletePeople(listOf(personId))
            return false
        }
        val centroid = Centroid.of(personId, members.map { FaceMath.decode(it.embedding) })
        faceDao.updateCentroid(personId, FaceMath.encode(centroid.mean), centroid.count)
        return true
    }

    private fun qualityOf(ordinal: Int) = FaceQuality.entries.getOrElse(ordinal) { FaceQuality.WEAK }

    private fun PersonRow.toDomain() = Person(
        id = id,
        name = name,
        photoCount = photoCount,
        faceCount = faceCount,
        hidden = hidden,
        cover = if (coverFaceId != null && coverPhotoId != null && coverUri != null) {
            FaceRef(
                faceId = coverFaceId,
                photoId = coverPhotoId,
                photoUri = coverUri,
                box = box(coverLeft ?: 0, coverTop ?: 0, coverRight ?: BOX_SCALE, coverBottom ?: BOX_SCALE),
                personId = id,
            )
        } else {
            null
        },
    )

    private fun FaceRefRow.toDomain() = FaceRef(
        faceId = faceId,
        photoId = photoId,
        photoUri = photoUri,
        box = box(left, top, right, bottom),
        personId = personId,
    )

    private fun box(left: Int, top: Int, right: Int, bottom: Int) = FaceBox(
        left = left.toFloat() / BOX_SCALE,
        top = top.toFloat() / BOX_SCALE,
        right = right.toFloat() / BOX_SCALE,
        bottom = bottom.toFloat() / BOX_SCALE,
    )
}
