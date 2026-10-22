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
        PhotoTextEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // v2: vault content hash and dimensions.
        AutoMigration(from = 1, to = 2),
        // v3: face quality, rejected-person links, pinned people, index on photos.indexed.
        AutoMigration(from = 2, to = 3),
        // v4: videos (media type, duration) and an index for grouping by album.
        AutoMigration(from = 3, to = 4),
        // v5: the album a vault photo came from.
        AutoMigration(from = 4, to = 5),
        // v6: photos and videos read from folders the gallery hides (WhatsApp).
        AutoMigration(from = 5, to = 6),
        // v7: content fingerprints for exact duplicates.
        AutoMigration(from = 6, to = 7),
        // v8: smart categories and the photo_text search table.
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class SortrDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun actionDao(): ActionDao
    abstract fun vaultDao(): VaultDao
    abstract fun faceDao(): FaceDao
    abstract fun libraryDao(): LibraryDao
}
