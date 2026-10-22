package com.sortr.data.repository

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.withTransaction
import com.sortr.data.db.SQL_CHUNK
import com.sortr.data.db.SortrDatabase
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.repository.ProgressFileException
import com.sortr.domain.repository.ProgressImportResult
import com.sortr.domain.repository.SortingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The progress file is plain JSON:
 * `{"format":"sortr-progress","version":1,"exportedAt":…,"items":[{"path":…,"name":…,"size":…,"status":…}]}`.
 * It holds file names and decisions, never photos.
 */
@Singleton
class SortingProgressImpl @Inject constructor(private val db: SortrDatabase) : SortingProgress {

    private val photoDao = db.photoDao()

    override suspend fun export(output: OutputStream): Int = withContext(Dispatchers.IO) {
        val rows = photoDao.progressRows().filter { it.status in SAVED }
        JsonWriter(output.bufferedWriter()).use { json ->
            json.beginObject()
            json.name("format").value(FORMAT)
            json.name("version").value(VERSION)
            json.name("exportedAt").value(System.currentTimeMillis())
            json.name("items").beginArray()
            rows.forEach { row ->
                json.beginObject()
                json.name("path").value(row.relativePath.orEmpty())
                json.name("name").value(row.displayName)
                json.name("size").value(row.size)
                json.name("status").value(row.status.name)
                json.endObject()
            }
            json.endArray()
            json.endObject()
        }
        rows.size
    }

    override suspend fun import(input: InputStream): ProgressImportResult = withContext(Dispatchers.IO) {
        val saved = try {
            read(input)
        } catch (e: ProgressFileException) {
            throw e
        } catch (e: Exception) {
            throw ProgressFileException("Couldn't read the progress file", e)
        }
        val rows = photoDao.progressRows()
        val byPath = rows.associateBy { key(it.relativePath.orEmpty(), it.displayName, it.size) }
        // Albums can be renamed or moved between phones; a unique name and size still matches.
        val byName = rows.groupBy { it.displayName to it.size }.filterValues { it.size == 1 }.mapValues { it.value.single() }

        var alreadySorted = 0
        var notFound = 0
        val updates = HashMap<PhotoStatus, MutableList<Long>>()
        for (item in saved) {
            val row = byPath[key(item.path, item.name, item.size)] ?: byName[item.name to item.size]
            if (row == null) {
                notFound++
                continue
            }
            // Decisions made on this phone win; a skip here can still become a keep or a delete.
            val open = row.status == PhotoStatus.UNREVIEWED || (row.status == PhotoStatus.SKIPPED && item.status != PhotoStatus.SKIPPED)
            if (open) updates.getOrPut(item.status) { mutableListOf() } += row.mediaStoreId else alreadySorted++
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            updates.forEach { (status, ids) -> ids.chunked(SQL_CHUNK).forEach { photoDao.updateStatuses(it, status, now) } }
        }
        ProgressImportResult(restored = updates.values.sumOf { it.size }, alreadySorted = alreadySorted, notFound = notFound)
    }

    private class SavedItem(val path: String, val name: String, val size: Long, val status: PhotoStatus)

    private fun read(input: InputStream): List<SavedItem> {
        val items = mutableListOf<SavedItem>()
        var format: String? = null
        JsonReader(input.bufferedReader()).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "format" -> format = json.nextString()
                    "version" -> if (json.nextInt() > VERSION) throw ProgressFileException("Made by a newer Sortr")
                    "items" -> {
                        json.beginArray()
                        while (json.hasNext()) readItem(json)?.let(items::add)
                        json.endArray()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }
        if (format != FORMAT) throw ProgressFileException("Not a Sortr progress file")
        return items
    }

    private fun readItem(json: JsonReader): SavedItem? {
        var path = ""
        var name: String? = null
        var size = -1L
        var status: PhotoStatus? = null
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "path" -> path = json.nextString()
                "name" -> name = json.nextString()
                "size" -> size = json.nextLong()
                "status" -> status = runCatching { PhotoStatus.valueOf(json.nextString()) }.getOrNull()
                else -> json.skipValue()
            }
        }
        json.endObject()
        return if (name != null && size >= 0 && status in SAVED) SavedItem(path, name, size, status!!) else null
    }

    private fun key(path: String, name: String, size: Long) = "$path\u0000$name\u0000$size"

    private companion object {
        const val FORMAT = "sortr-progress"
        const val VERSION = 1

        /** The vault isn't included: its photos are encrypted with keys that stay on the phone. */
        val SAVED = setOf(PhotoStatus.KEPT, PhotoStatus.SKIPPED, PhotoStatus.TRASHED)
    }
}
