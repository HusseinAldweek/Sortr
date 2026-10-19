package com.sortr.app.ui.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.sortr.app.vault.VaultImages
import com.sortr.app.vault.VaultLock
import com.sortr.domain.model.VaultItem
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.repository.VaultRepository
import com.sortr.domain.vault.PinPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { INTRO, CREATE, CONFIRM }

enum class SetupError { INVALID, MISMATCH }

data class SetupState(
    val step: SetupStep = SetupStep.INTRO,
    val error: SetupError? = null,
    /** Bumped on each step so the PIN pad clears. */
    val attempt: Int = 0,
)

data class VaultUiState(
    val loading: Boolean = true,
    val isSetUp: Boolean = false,
    val unlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val showBackupReminder: Boolean = false,
    val setup: SetupState = SetupState(),
)

sealed interface VaultEvent {
    data object Restored : VaultEvent
    data object RestoreFailed : VaultEvent
    data object Deleted : VaultEvent
    data object Failed : VaultEvent
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val vaultAuth: VaultAuth,
    private val vaultLock: VaultLock,
    vaultImages: VaultImages,
) : ViewModel() {

    val imageLoader: ImageLoader = vaultImages.loader

    private val setup = MutableStateFlow(SetupState())

    /** First entry of a new PIN, held in memory only until it's confirmed. */
    private var firstPin: String? = null

    private val backupTimes = combine(vaultAuth.lastBackupAt, vaultAuth.backupReminderDismissedAt) { backup, dismissed ->
        maxOf(backup ?: 0L, dismissed ?: 0L)
    }

    val state: StateFlow<VaultUiState> = combine(
        vaultAuth.isSetUp,
        vaultLock.unlocked,
        vault.observeItems(),
        backupTimes,
        setup,
    ) { isSetUp, unlocked, items, backupSeenAt, setup ->
        val newest = items.maxOfOrNull { it.addedAt } ?: 0L
        VaultUiState(
            loading = false,
            isSetUp = isSetUp,
            unlocked = unlocked,
            items = items,
            showBackupReminder = items.isNotEmpty() && newest > backupSeenAt,
            setup = setup,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    private val _events = Channel<VaultEvent>(Channel.BUFFERED)
    val events: Flow<VaultEvent> = _events.receiveAsFlow()

    fun startPinSetup() {
        firstPin = null
        setup.update { SetupState(step = SetupStep.CREATE, attempt = it.attempt + 1) }
    }

    fun onSetupPin(pin: String) {
        when (setup.value.step) {
            SetupStep.INTRO -> Unit
            SetupStep.CREATE -> {
                if (!PinPolicy.isValid(pin)) {
                    setup.update { it.copy(error = SetupError.INVALID, attempt = it.attempt + 1) }
                } else {
                    firstPin = pin
                    setup.update { SetupState(step = SetupStep.CONFIRM, attempt = it.attempt + 1) }
                }
            }
            SetupStep.CONFIRM -> {
                if (pin == firstPin) {
                    firstPin = null
                    viewModelScope.launch {
                        vaultAuth.setUp(pin)
                        vaultLock.unlock()
                    }
                } else {
                    firstPin = null
                    setup.update { SetupState(step = SetupStep.CREATE, error = SetupError.MISMATCH, attempt = it.attempt + 1) }
                }
            }
        }
    }

    fun lock() = vaultLock.lock()

    fun restore(itemId: Long) = runAction(VaultEvent.Restored, VaultEvent.RestoreFailed) {
        vault.restoreToGallery(itemId)
    }

    fun delete(itemId: Long) = runAction(VaultEvent.Deleted, VaultEvent.Failed) {
        vault.delete(itemId)
    }

    fun dismissBackupReminder() {
        viewModelScope.launch { vaultAuth.dismissBackupReminder(System.currentTimeMillis()) }
    }

    private fun runAction(success: VaultEvent, failure: VaultEvent, block: suspend () -> Unit) {
        viewModelScope.launch {
            val event = try {
                block()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Vault action failed", e)
                failure
            }
            _events.send(event)
        }
    }

    private companion object {
        const val TAG = "VaultViewModel"
    }
}
