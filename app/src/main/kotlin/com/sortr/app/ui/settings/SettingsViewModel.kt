package com.sortr.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.ui.vault.PinFeedback
import com.sortr.app.vault.VaultLock
import com.sortr.domain.model.VaultBackupException
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.repository.VaultRepository
import com.sortr.domain.vault.PinCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class SettingsUiState(
    val vaultSetUp: Boolean = false,
    val lastBackupAt: Long? = null,
)

/** A running export or import. [total] is 0 until the item count is known. */
data class BackupProgress(val exporting: Boolean, val done: Int, val total: Int)

sealed interface SettingsEvent {
    data object ResetDone : SettingsEvent
    data object VaultEmpty : SettingsEvent
    data class ExportDone(val count: Int) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class ImportDone(val imported: Int, val skipped: Int) : SettingsEvent
    data class ImportFailed(val reason: VaultBackupException.Reason?) : SettingsEvent
    data object PinChanged : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photos: PhotoRepository,
    private val vault: VaultRepository,
    private val vaultAuth: VaultAuth,
    private val vaultLock: VaultLock,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(vaultAuth.isSetUp, vaultAuth.lastBackupAt) { setUp, lastBackup ->
            SettingsUiState(vaultSetUp = setUp, lastBackupAt = lastBackup)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _progress = MutableStateFlow<BackupProgress?>(null)
    val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()

    private val _pinFeedback = MutableStateFlow<PinFeedback?>(null)
    val pinFeedback: StateFlow<PinFeedback?> = _pinFeedback.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** The chosen backup password, held only between the password dialog and the file picker. */
    private var exportPassword: CharArray? = null

    val isVaultUnlocked: Boolean get() = vaultLock.isUnlocked

    fun resetReviewed() {
        viewModelScope.launch {
            photos.resetReviewed()
            _events.send(SettingsEvent.ResetDone)
        }
    }

    suspend fun isVaultEmpty(): Boolean = vault.observeItems().first().isEmpty()

    fun onVaultEmpty() {
        _events.trySend(SettingsEvent.VaultEmpty)
    }

    fun setExportPassword(password: CharArray) {
        exportPassword?.fill('\u0000')
        exportPassword = password
    }

    fun cancelExport() {
        exportPassword?.fill('\u0000')
        exportPassword = null
    }

    fun export(uri: Uri) {
        val password = exportPassword ?: return
        exportPassword = null
        viewModelScope.launch {
            _progress.value = BackupProgress(exporting = true, done = 0, total = 0)
            try {
                val count = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        vault.exportBackup(output, password) { done, total ->
                            _progress.value = BackupProgress(exporting = true, done = done, total = total)
                        }
                    } ?: throw IOException("Couldn't open the backup file")
                }
                vaultAuth.markBackedUp(System.currentTimeMillis())
                _events.send(SettingsEvent.ExportDone(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Backup export failed", e)
                // Don't leave a half-written backup that looks usable.
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                _events.send(SettingsEvent.ExportFailed)
            } finally {
                password.fill('\u0000')
                _progress.value = null
            }
        }
    }

    fun import(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _progress.value = BackupProgress(exporting = false, done = 0, total = 0)
            try {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        vault.importBackup(input, password) { done, total ->
                            _progress.value = BackupProgress(exporting = false, done = done, total = total)
                        }
                    } ?: throw IOException("Couldn't open the backup file")
                }
                _events.send(SettingsEvent.ImportDone(result.imported, result.alreadyInVault))
            } catch (e: CancellationException) {
                throw e
            } catch (e: VaultBackupException) {
                Log.w(TAG, "Backup import failed: ${e.reason}", e)
                _events.send(SettingsEvent.ImportFailed(e.reason))
            } catch (e: Exception) {
                Log.w(TAG, "Backup import failed", e)
                _events.send(SettingsEvent.ImportFailed(null))
            } finally {
                password.fill('\u0000')
                _progress.value = null
            }
        }
    }

    /** Returns true through [onDone] when the PIN was changed. */
    fun changePin(current: String, new: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vaultAuth.changePin(current, new)
            _pinFeedback.value = when (result) {
                PinCheck.Correct -> null
                is PinCheck.Wrong -> PinFeedback.Wrong(result.attemptsBeforeLockout)
                is PinCheck.LockedOut -> PinFeedback.LockedOut(result.untilMillis)
            }
            if (result == PinCheck.Correct) _events.send(SettingsEvent.PinChanged)
            onDone(result == PinCheck.Correct)
        }
    }

    fun clearPinFeedback() {
        _pinFeedback.value = null
    }

    override fun onCleared() {
        cancelExport()
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
