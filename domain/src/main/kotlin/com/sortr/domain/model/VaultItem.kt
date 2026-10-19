package com.sortr.domain.model

/** A photo stored encrypted inside the app. */
data class VaultItem(
    val id: Long,
    val originalName: String,
    val mimeType: String,
    val originalSize: Long,
    val dateTaken: Long,
    val addedAt: Long,
    val width: Int,
    val height: Int,
    /** The gallery photo this was copied from, while that original still exists. */
    val sourcePhotoId: Long?,
) {
    val originalInGallery: Boolean get() = sourcePhotoId != null
}

/** Result of importing a vault backup. */
data class BackupImportResult(val imported: Int, val alreadyInVault: Int)

class VaultBackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        NOT_A_BACKUP,
        UNSUPPORTED_VERSION,

        /** The first block couldn't be decrypted: almost always a wrong password. */
        WRONG_PASSWORD,

        /** Decryption failed part-way, or the file ends early. */
        DAMAGED,
    }
}
