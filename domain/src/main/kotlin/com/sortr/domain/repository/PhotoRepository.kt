package com.sortr.domain.repository

import com.sortr.domain.model.Decision
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {

    fun observeSummary(): Flow<LibrarySummary>

    fun observeTrash(): Flow<List<Photo>>

    /**
     * Snapshot of the photos for a new session: unreviewed first, then previously skipped,
     * each newest first.
     */
    suspend fun loadSession(filter: SessionFilter): List<Photo>

    suspend fun applyDecision(photo: Photo, action: SwipeAction): Decision

    /** Puts the photo back to the status it had before [decision] and drops the log entry. */
    suspend fun undoDecision(decision: Decision)

    /** Moves trashed photos back to unreviewed. */
    suspend fun restoreFromTrash(ids: Collection<Long>)

    /**
     * Records photos the system has deleted from the device and removes them from the
     * database. Takes the photos rather than ids because a gallery sync may already have
     * dropped their rows by the time this runs. Returns the number of bytes freed.
     */
    suspend fun markPurged(photos: Collection<Photo>): Long

    /** Makes kept and skipped photos show up in sessions again. */
    suspend fun resetReviewed()
}
