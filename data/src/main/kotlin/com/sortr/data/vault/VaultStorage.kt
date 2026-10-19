package com.sortr.data.vault

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted vault files, in the app's private no-backup folder. Only encrypted data is ever
 * written here. The database stores names relative to this folder.
 */
@Singleton
class VaultStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }

    fun newBaseName(): String = UUID.randomUUID().toString()

    fun file(name: String): File {
        require(!name.contains('/') && !name.contains('\\') && name != ".." ) { "Invalid vault file name" }
        return File(dir, name)
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }
}
