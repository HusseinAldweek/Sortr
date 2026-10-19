package com.sortr.domain.repository

import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * People found by face grouping. Faces in photos that are in the vault or the Trash are never
 * shown or counted.
 */
interface PeopleRepository {

    /** Starts as READY when the model file exists; becomes UNSUPPORTED if it fails to load. */
    val modelState: StateFlow<FaceModelState>

    /** People with a name, or with at least two faces. */
    fun observePeople(includeHidden: Boolean): Flow<List<Person>>

    fun observePerson(personId: Long): Flow<Person?>

    fun observeFaces(personId: Long): Flow<List<FaceRef>>

    /** Faces that weren't confidently matched, plus one-off faces of unnamed people. */
    fun observeUnsortedFaces(): Flow<List<FaceRef>>

    fun observeIndexProgress(): Flow<FaceIndexProgress>

    suspend fun rename(personId: Long, name: String?)

    suspend fun setHidden(personId: Long, hidden: Boolean)

    /** Folds [otherIds] into [targetId]; the target keeps its name, or takes the first other name. */
    suspend fun merge(targetId: Long, otherIds: Collection<Long>)

    /** "Not this person": unlinks the faces and stops them being matched to [personId] again. */
    suspend fun removeFaces(personId: Long, faceIds: Collection<Long>)

    /** Moves faces to [personId], or to a new person when it's null. Returns the person's id. */
    suspend fun moveFaces(faceIds: Collection<Long>, personId: Long?): Long

    /**
     * Regroups every face with a new similarity threshold, from the stored embeddings. People the
     * user named, merged, hid or corrected are kept as they are.
     */
    suspend fun regroup(threshold: Float)

    /** Forgets all faces and people so every photo is scanned again. */
    suspend fun resetAll()
}
