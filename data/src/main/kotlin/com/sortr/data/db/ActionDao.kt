package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}

data class DayCountRow(val day: String, val count: Int)
