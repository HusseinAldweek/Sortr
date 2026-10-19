package com.sortr.data.repository

import androidx.room.withTransaction
import com.sortr.data.db.ActionEntity
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.data.db.toDomain
import com.sortr.domain.model.ActionType
import com.sortr.domain.model.Decision
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val db: SortrDatabase,
) : PhotoRepository {

    private val photoDao = db.photoDao()
    private val actionDao = db.actionDao()

    override fun observeSummary(): Flow<LibrarySummary> =
        photoDao.observeSummary().map { it.toDomain() }

    override fun observeTrash(): Flow<List<Photo>> =
        photoDao.observeByStatusRecentlyChanged(PhotoStatus.TRASHED).map { rows -> rows.map { it.toDomain() } }

    override suspend fun loadSession(filter: SessionFilter): List<Photo> = when (filter) {
        SessionFilter.AllPhotos -> db.withTransaction {
            photoDao.byStatusNewestFirst(PhotoStatus.UNREVIEWED) +
                photoDao.byStatusNewestFirst(PhotoStatus.SKIPPED)
        }.map { it.toDomain() }
    }

    override suspend fun applyDecision(photo: Photo, action: SwipeAction): Decision = db.withTransaction {
        val now = System.currentTimeMillis()
        photoDao.updateStatus(photo.id, action.resultStatus, now)
        val actionId = actionDao.insert(
            ActionEntity(
                photoId = photo.id,
                action = action.actionType,
                previousStatus = photo.status,
                sizeBytes = photo.sizeBytes,
                timestamp = now,
            ),
        )
        Decision(actionId, photo, action, photo.status)
    }

    override suspend fun undoDecision(decision: Decision) = db.withTransaction {
        photoDao.updateStatus(decision.photo.id, decision.previousStatus, System.currentTimeMillis())
        actionDao.delete(decision.actionId)
    }

    override suspend fun restoreFromTrash(ids: Collection<Long>) = db.withTransaction {
        val now = System.currentTimeMillis()
        ids.chunked(SQL_CHUNK).forEach { photoDao.updateStatuses(it, PhotoStatus.UNREVIEWED, now) }
        actionDao.insertAll(
            ids.map {
                ActionEntity(
                    photoId = it,
                    action = ActionType.RESTORE,
                    previousStatus = PhotoStatus.TRASHED,
                    sizeBytes = 0,
                    timestamp = now,
                )
            },
        )
    }

    override suspend fun markPurged(photos: Collection<Photo>): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        actionDao.insertAll(
            photos.map {
                ActionEntity(
                    photoId = it.id,
                    action = ActionType.PURGE,
                    previousStatus = it.status,
                    sizeBytes = it.sizeBytes,
                    timestamp = now,
                )
            },
        )
        photos.map { it.id }.chunked(SQL_CHUNK).forEach { photoDao.deleteByIds(it) }
        photos.sumOf { it.sizeBytes }
    }

    override suspend fun resetReviewed() {
        photoDao.resetReviewed(System.currentTimeMillis())
    }
}
