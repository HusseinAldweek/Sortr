package com.sortr.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PhotoEntity::class,
        FaceEntity::class,
        PersonEntity::class,
        VaultItemEntity::class,
        ActionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: vault content hash and dimensions.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class SortrDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun actionDao(): ActionDao
    abstract fun vaultDao(): VaultDao
}
