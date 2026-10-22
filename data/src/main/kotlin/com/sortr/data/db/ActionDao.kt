package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.sortr.domain.model.ActionType
import com.sortr.domain.model.PhotoStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    @Insert
    suspend fun insert(action: ActionEntity): Long

    @Insert
    suspend fun insertAll(actions: List<ActionEntity>)

    @Query("DELETE FROM actions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Keep, Delete and Private decisions per local day (yyyy-MM-dd) since [since]. Skips don't count. */
    @Query(
        "SELECT date(timestamp / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count FROM actions " +
            "WHERE action IN ('KEEP', 'DELETE', 'PRIVATE') AND timestamp >= :since GROUP BY day",
    )
    fun observeSortedPerDay(since: Long): Flow<List<DayCountRow>>

    /** Each photo's latest decision since [since], if the photo still has the status it led to. */
    @Query(
        "SELECT a.id AS actionId, a.action AS actionType, a.previousStatus AS previousStatus, a.timestamp AS decidedAt, p.* " +
            "FROM actions a JOIN photos p ON p.mediaStoreId = a.photoId " +
            "WHERE a.timestamp >= :since AND a.id = (SELECT MAX(b.id) FROM actions b WHERE b.photoId = a.photoId) AND (" +
            "(a.action = 'KEEP' AND p.status = 'KEPT') OR (a.action = 'DELETE' AND p.status = 'TRASHED') OR " +
            "(a.action = 'PRIVATE' AND p.status = 'VAULTED') OR (a.action = 'SKIP' AND p.status = 'SKIPPED')) " +
            "ORDER BY a.timestamp DESC, a.id DESC LIMIT :limit",
    )
    fun observeRecent(since: Long, limit: Int): Flow<List<RecentActionRow>>
}

data class RecentActionRow(
    val actionId: Long,
    val actionType: ActionType,
    val previousStatus: PhotoStatus?,
    val decidedAt: Long,
    @Embedded val photo: PhotoEntity,
)

data class DayCountRow(val day: String, val count: Int)
