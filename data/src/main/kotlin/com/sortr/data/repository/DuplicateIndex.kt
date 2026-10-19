package com.sortr.data.repository

import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.data.db.toDomain
import com.sortr.domain.analysis.DuplicateCandidate
import com.sortr.domain.analysis.DuplicateFinder
import com.sortr.domain.analysis.HashedPhoto
import com.sortr.domain.model.DuplicateGroup
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Groups of similar photos, worked out from the stored perceptual hashes. */
@Singleton
class DuplicateIndex @Inject constructor(db: SortrDatabase) {

    private val libraryDao = db.libraryDao()

    /**
     * Groups biggest saving first; inside a group the best copy comes first, then newest first.
     * A group where the user kept every copy is treated as "not duplicates" and left out.
     */
    suspend fun groups(): List<DuplicateGroup> {
        val hashes = libraryDao.hashes()
        val grouped = withContext(Dispatchers.Default) {
            DuplicateFinder.groups(hashes.map { HashedPhoto(it.mediaStoreId, it.dHash) })
        }
        if (grouped.isEmpty()) return emptyList()
        // Full rows only for photos that are in a group, usually a small part of the library.
        val byId = grouped.flatten().chunked(SQL_CHUNK).flatMap { libraryDao.byIds(it) }.associateBy { it.mediaStoreId }
        return withContext(Dispatchers.Default) {
            grouped
                .map { ids -> ids.filter(byId::containsKey) }
                .filter { it.size >= 2 }
                .map { ids ->
                    val members = ids.map { byId.getValue(it).toDomain() }
                    val bestId = DuplicateFinder.pickBest(
                        members.map {
                            DuplicateCandidate(
                                id = it.id,
                                pixels = it.width.toLong() * it.height,
                                sharpness = it.blurScore,
                                sizeBytes = it.sizeBytes,
                                dateTaken = it.dateTaken,
                            )
                        },
                    )
                    DuplicateGroup(
                        photos = members.sortedWith(compareByDescending<Photo> { it.id == bestId }.thenByDescending { it.dateTaken }),
                        bestId = bestId,
                    )
                }
                .filterNot { group -> group.photos.all { it.status == PhotoStatus.KEPT } }
                .sortedByDescending { it.reclaimableBytes }
        }
    }
}
