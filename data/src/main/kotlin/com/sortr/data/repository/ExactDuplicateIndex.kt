package com.sortr.data.repository

import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.data.db.toDomain
import com.sortr.domain.analysis.ExactCopies
import com.sortr.domain.model.DuplicateGroup
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Groups of byte-identical photos and videos, from the stored fingerprints. */
@Singleton
class ExactDuplicateIndex @Inject constructor(db: SortrDatabase) {

    private val libraryDao = db.libraryDao()

    /**
     * Biggest saving first; inside a group the copy to keep comes first. A group where the user
     * kept every copy is left out.
     */
    suspend fun groups(): List<DuplicateGroup> {
        val grouped = withContext(Dispatchers.Default) {
            libraryDao.fingerprints()
                .groupBy { it.size to it.hash }
                .values
                .filter { it.size >= 2 }
                .map { rows -> rows.map { it.mediaStoreId } }
        }
        if (grouped.isEmpty()) return emptyList()
        val byId = grouped.flatten().chunked(SQL_CHUNK).flatMap { libraryDao.byIds(it) }.associateBy { it.mediaStoreId }
        return withContext(Dispatchers.Default) {
            grouped
                .map { ids -> ids.mapNotNull { byId[it]?.toDomain() } }
                .filter { it.size >= 2 }
                .map { copies ->
                    val keeperId = ExactCopies.pickKeeper(copies)
                    DuplicateGroup(
                        photos = copies.sortedWith(compareByDescending<Photo> { it.id == keeperId }.thenBy { it.relativePath.orEmpty() }),
                        bestId = keeperId,
                    )
                }
                .filterNot { group -> group.photos.all { it.status == PhotoStatus.KEPT } }
                .sortedByDescending { it.reclaimableBytes }
        }
    }
}
