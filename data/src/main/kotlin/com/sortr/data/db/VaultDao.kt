package com.sortr.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert
    suspend fun insert(item: VaultItemEntity): Long

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC, id DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC, id DESC")
    suspend fun all(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun byId(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE sourceMediaStoreId = :photoId LIMIT 1")
    suspend fun bySource(photoId: Long): VaultItemEntity?

    @Query(PENDING_ORIGINALS)
    fun observePendingOriginals(): Flow<List<VaultItemEntity>>

    @Query(PENDING_ORIGINALS)
    suspend fun pendingOriginals(): List<VaultItemEntity>

    @Query(
        "UPDATE vault_items SET originalRemoved = 1, sourceMediaStoreId = NULL WHERE id IN (:ids)",
    )
    suspend fun markOriginalsRemoved(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM vault_items WHERE contentSha256 = :sha256")
    suspend fun countWithHash(sha256: String): Int

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: Long)
}

private const val PENDING_ORIGINALS =
    "SELECT * FROM vault_items WHERE originalRemoved = 0 AND sourceMediaStoreId IS NOT NULL " +
        "ORDER BY addedAt DESC, id DESC"
