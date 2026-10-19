package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActionDao {

    @Insert
    suspend fun insert(action: ActionEntity): Long

    @Insert
    suspend fun insertAll(actions: List<ActionEntity>)

    @Query("DELETE FROM actions WHERE id = :id")
    suspend fun delete(id: Long)
}
