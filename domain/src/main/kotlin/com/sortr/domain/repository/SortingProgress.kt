package com.sortr.domain.repository

import java.io.InputStream
import java.io.OutputStream

/**
 * Saves which photos were kept, skipped or put in the Trash to a small file, and brings those
 * decisions back after a reinstall or on a new phone. Photos are matched by album, file name
 * and size, since gallery ids don't survive either.
 */
interface SortingProgress {

    /** Returns how many decisions were written. */
    suspend fun export(output: OutputStream): Int

    /** Applies saved decisions to matching photos that are still waiting to be sorted. */
    suspend fun import(input: InputStream): ProgressImportResult
}

data class ProgressImportResult(
    val restored: Int,
    /** Already decided on this phone; left as they are. */
    val alreadySorted: Int,
    /** No longer on the phone, or renamed. */
    val notFound: Int,
)

/** The file isn't a Sortr progress file, or is damaged. */
class ProgressFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
