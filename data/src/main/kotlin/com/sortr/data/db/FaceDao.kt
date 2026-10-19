package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Photos whose faces may be shown: not in the vault and not in the Trash. */
internal const val SHOWN_PHOTO = "status NOT IN ('VAULTED', 'TRASHED')"

/**
 * True for a face `f` that belongs on the Unsorted screen: not matched to anyone, or matched
 * only to an unnamed, visible person who has fewer than two shown faces.
 */
internal const val IS_UNSORTED_FACE =
    "(f.personId IS NULL OR f.personId IN (" +
        "SELECT p.id FROM people p WHERE p.name IS NULL AND p.hidden = 0 AND (" +
        "SELECT COUNT(*) FROM faces x JOIN photos px ON px.mediaStoreId = x.photoId " +
        "WHERE x.personId = p.id AND px.$SHOWN_PHOTO) < 2))"

private const val PERSON_COLUMNS =
    "SELECT g.id, g.name, g.hidden, g.faceCount, g.photoCount, " +
        "cf.id AS coverFaceId, cf.photoId AS coverPhotoId, cph.uri AS coverUri, " +
        "cf.`left` AS coverLeft, cf.top AS coverTop, cf.`right` AS coverRight, cf.bottom AS coverBottom FROM ("

private const val PERSON_GROUP =
    "SELECT p.id, p.name, p.hidden, COUNT(f.id) AS faceCount, COUNT(DISTINCT f.photoId) AS photoCount, " +
        "(SELECT f2.id FROM faces f2 JOIN photos ph2 ON ph2.mediaStoreId = f2.photoId " +
        "WHERE f2.personId = p.id AND ph2.$SHOWN_PHOTO " +
        "ORDER BY f2.quality DESC, (f2.`right` - f2.`left`) * (f2.bottom - f2.top) DESC LIMIT 1) AS coverId " +
        "FROM people p JOIN faces f ON f.personId = p.id JOIN photos ph ON ph.mediaStoreId = f.photoId " +
        "WHERE ph.$SHOWN_PHOTO "

private const val PERSON_COVER_JOIN =
    ") g LEFT JOIN faces cf ON cf.id = g.coverId LEFT JOIN photos cph ON cph.mediaStoreId = cf.photoId"

private const val FACE_REF_COLUMNS =
    "SELECT f.id AS faceId, f.photoId, ph.uri AS photoUri, f.`left`, f.top, f.`right`, f.bottom, f.personId " +
        "FROM faces f JOIN photos ph ON ph.mediaStoreId = f.photoId"

@Dao
interface FaceDao {

    // Writing: used by the indexer and people operations, always inside FaceStore's lock.

    @Insert
    suspend fun insertFaces(faces: List<FaceEntity>): List<Long>

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Query("SELECT id, averageEmbedding, faceCount, pinned FROM people")
    suspend fun centroids(): List<CentroidRow>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun person(id: Long): PersonEntity?

    @Query("UPDATE people SET averageEmbedding = :mean, faceCount = :count WHERE id = :id")
    suspend fun updateCentroid(id: Long, mean: ByteArray, count: Int)

    @Query("UPDATE people SET name = :name, pinned = 1 WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE people SET hidden = :hidden, pinned = 1 WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE people SET pinned = 1 WHERE id = :id")
    suspend fun pin(id: Long)

    @Query("DELETE FROM people WHERE id IN (:ids)")
    suspend fun deletePeople(ids: List<Long>)

    @Query("DELETE FROM people WHERE pinned = 0")
    suspend fun deleteUnpinnedPeople()

    /** Removes people left without any face, e.g. after their photos were deleted. */
    @Query("DELETE FROM people WHERE id NOT IN (SELECT DISTINCT personId FROM faces WHERE personId IS NOT NULL)")
    suspend fun deleteEmptyPeople()

    @Query("SELECT id, embedding, quality, personId, rejectedPersonId FROM faces WHERE id IN (:ids)")
    suspend fun faceVectors(ids: List<Long>): List<FaceVectorRow>

    @Query("SELECT id, embedding, quality, personId, rejectedPersonId FROM faces WHERE personId = :personId")
    suspend fun faceVectorsOf(personId: Long): List<FaceVectorRow>

    @Query("SELECT id, embedding, quality, personId, rejectedPersonId FROM faces")
    suspend fun allFaceVectors(): List<FaceVectorRow>

    @Query("UPDATE faces SET personId = :personId, confidence = :confidence WHERE id = :faceId")
    suspend fun assign(faceId: Long, personId: Long?, confidence: Float)

    @Query("UPDATE faces SET personId = :to WHERE personId IN (:from)")
    suspend fun reassignPeople(from: List<Long>, to: Long)

    @Query("UPDATE faces SET rejectedPersonId = :to WHERE rejectedPersonId IN (:from)")
    suspend fun moveRejections(from: List<Long>, to: Long)

    @Query("UPDATE faces SET personId = NULL, rejectedPersonId = :personId WHERE id IN (:faceIds)")
    suspend fun reject(personId: Long, faceIds: List<Long>)

    @Query("DELETE FROM faces WHERE photoId IN (:photoIds)")
    suspend fun deleteForPhotos(photoIds: List<Long>)

    @Query("DELETE FROM faces")
    suspend fun deleteAllFaces()

    @Query("DELETE FROM people")
    suspend fun deleteAllPeople()

    // Reading, for screens.

    @Query(
        PERSON_COLUMNS + PERSON_GROUP + "AND (:includeHidden OR p.hidden = 0) GROUP BY p.id " +
            "HAVING p.name IS NOT NULL OR COUNT(f.id) >= 2" + PERSON_COVER_JOIN +
            " ORDER BY g.name IS NULL, g.faceCount DESC, g.id",
    )
    fun observePeople(includeHidden: Boolean): Flow<List<PersonRow>>

    @Query(PERSON_COLUMNS + PERSON_GROUP + "AND p.id = :personId GROUP BY p.id" + PERSON_COVER_JOIN)
    fun observePerson(personId: Long): Flow<PersonRow?>

    @Query(
        FACE_REF_COLUMNS + " WHERE f.personId = :personId AND ph.$SHOWN_PHOTO " +
            "ORDER BY f.quality DESC, ph.dateTaken DESC, f.id",
    )
    fun observeFacesOf(personId: Long): Flow<List<FaceRefRow>>

    @Query(FACE_REF_COLUMNS + " WHERE ph.$SHOWN_PHOTO AND $IS_UNSORTED_FACE ORDER BY ph.dateTaken DESC, f.id")
    fun observeUnsorted(): Flow<List<FaceRefRow>>
}

data class CentroidRow(
    val id: Long,
    val averageEmbedding: ByteArray,
    val faceCount: Int,
    val pinned: Boolean,
)

data class FaceVectorRow(
    val id: Long,
    val embedding: ByteArray,
    val quality: Int,
    val personId: Long?,
    val rejectedPersonId: Long?,
)

data class PersonRow(
    val id: Long,
    val name: String?,
    val hidden: Boolean,
    val faceCount: Int,
    val photoCount: Int,
    val coverFaceId: Long?,
    val coverPhotoId: Long?,
    val coverUri: String?,
    val coverLeft: Int?,
    val coverTop: Int?,
    val coverRight: Int?,
    val coverBottom: Int?,
)

data class FaceRefRow(
    val faceId: Long,
    val photoId: Long,
    val photoUri: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val personId: Long?,
)
