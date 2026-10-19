package com.sortr.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Keeps the local database in step with the device's MediaStore. */
interface GallerySync {

    val isSyncing: StateFlow<Boolean>

    /** Queues a sync. Requests made while one is running collapse into a single follow-up run. */
    fun requestSync()

    /** Emits whenever the device's image collection changes. */
    fun mediaChanges(): Flow<Unit>

    /** Which of [ids] still exist on the device. */
    suspend fun existingIds(ids: Collection<Long>): Set<Long>
}
