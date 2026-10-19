package com.sortr.app.ui.vault

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.whatsapp.HiddenDeleteDialog
import com.sortr.app.ui.whatsapp.HiddenDeleteKind
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.VaultItem
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.VaultRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoveOriginalsState(
    val pending: Int = 0,
    val encrypting: Int = 0,
    val busy: Boolean = false,
)

sealed interface RemoveOriginalsEvent {
    data class Confirm(val request: IntentSender) : RemoveOriginalsEvent

    /** Originals in hidden WhatsApp folders: Android has no dialog for them, so Sortr asks. */
    data class ConfirmHidden(val count: Int) : RemoveOriginalsEvent
    data class Removed(val count: Int) : RemoveOriginalsEvent
    data object Kept : RemoveOriginalsEvent
    data object Failed : RemoveOriginalsEvent
}

/**
 * Deletes the gallery originals of photos that are safely in the vault, in one batch behind the
 * system confirmation dialog. Cancelling leaves everything as it was.
 */
@HiltViewModel
class RemoveOriginalsViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val gallerySync: GallerySync,
    private val deleteRequests: DeleteRequestFactory,
    private val locator: MediaLocator,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private var awaitingConfirmation: List<VaultItem> = emptyList()
    private var awaitingHidden: List<VaultItem> = emptyList()
    private var removedSoFar = 0

    val state: StateFlow<RemoveOriginalsState> =
        combine(vault.observePendingOriginals(), vault.encryptionBacklog, busy) { pending, encrypting, busy ->
            RemoveOriginalsState(pending = pending.size, encrypting = encrypting, busy = busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoveOriginalsState())

    private val _events = Channel<RemoveOriginalsEvent>(Channel.BUFFERED)
    val events: Flow<RemoveOriginalsEvent> = _events.receiveAsFlow()

    fun start() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val pending = vault.pendingOriginals()
                val existing = gallerySync.existingIds(pending.mapNotNull { it.sourcePhotoId })
                val (present, gone) = pending.partition { it.sourcePhotoId in existing }
                // Originals deleted some other way don't need asking about.
                if (gone.isNotEmpty()) vault.markOriginalsRemoved(gone.map { it.id })
                if (present.isEmpty()) {
                    busy.value = false
                    return@launch
                }
                // Hidden WhatsApp originals are confirmed in Sortr's own dialog after Android's.
                val (hidden, gallery) = present.partition { (it.sourcePhotoId ?: 0) < 0 }
                awaitingHidden = hidden
                removedSoFar = 0
                if (gallery.isEmpty()) {
                    _events.send(RemoveOriginalsEvent.ConfirmHidden(hidden.size))
                    return@launch
                }
                val request = deleteRequests.createForIds(gallery.mapNotNull { it.sourcePhotoId })
                    ?: throw IllegalStateException("No original can be removed")
                awaitingConfirmation = gallery
                _events.send(RemoveOriginalsEvent.Confirm(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start removing originals", e)
                awaitingConfirmation = emptyList()
                busy.value = false
                _events.send(RemoveOriginalsEvent.Failed)
            }
        }
    }

    fun onConfirmationResult(accepted: Boolean) {
        val requested = awaitingConfirmation
        awaitingConfirmation = emptyList()
        var waitingForHidden = false
        viewModelScope.launch {
            try {
                if (!accepted || requested.isEmpty()) {
                    awaitingHidden = emptyList()
                    _events.send(RemoveOriginalsEvent.Kept)
                    return@launch
                }
                val stillThere = gallerySync.existingIds(requested.mapNotNull { it.sourcePhotoId })
                val removed = requested.filter { it.sourcePhotoId !in stillThere }
                vault.markOriginalsRemoved(removed.map { it.id })
                gallerySync.requestSync()
                removedSoFar = removed.size
                if (awaitingHidden.isNotEmpty()) {
                    waitingForHidden = true
                    _events.send(RemoveOriginalsEvent.ConfirmHidden(awaitingHidden.size))
                } else {
                    _events.send(RemoveOriginalsEvent.Removed(removed.size))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't record removed originals", e)
                _events.send(RemoveOriginalsEvent.Failed)
            } finally {
                if (!waitingForHidden) busy.value = false
            }
        }
    }

    /** The answer to Sortr's own dialog for hidden WhatsApp originals. */
    fun onHiddenResult(accepted: Boolean) {
        val requested = awaitingHidden
        awaitingHidden = emptyList()
        viewModelScope.launch {
            try {
                if (!accepted || requested.isEmpty()) {
                    _events.send(if (removedSoFar > 0) RemoveOriginalsEvent.Removed(removedSoFar) else RemoveOriginalsEvent.Kept)
                    return@launch
                }
                val deleted = locator.deleteHidden(requested.mapNotNull { it.sourcePhotoId })
                val removed = requested.filter { it.sourcePhotoId in deleted }
                vault.markOriginalsRemoved(removed.map { it.id })
                gallerySync.requestSync()
                val total = removedSoFar + removed.size
                _events.send(if (total == 0) RemoveOriginalsEvent.Failed else RemoveOriginalsEvent.Removed(total))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove hidden originals", e)
                _events.send(RemoveOriginalsEvent.Failed)
            } finally {
                removedSoFar = 0
                busy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "RemoveOriginals"
    }
}

/**
 * The dialogs and messages behind removing vault originals: Android's confirmation for gallery
 * originals, Sortr's own for hidden WhatsApp ones. Used by the card and by Home's to-do row.
 */
@Composable
fun RemoveOriginalsEffects(
    viewModel: RemoveOriginalsViewModel,
    snackbarHostState: SnackbarHostState,
    onRemoved: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnRemoved by rememberUpdatedState(onRemoved)
    var hiddenToConfirm by rememberSaveable { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConfirmationResult(accepted = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is RemoveOriginalsEvent.Confirm -> {
                    launcher.launch(IntentSenderRequest.Builder(event.request).build())
                    null
                }
                is RemoveOriginalsEvent.ConfirmHidden -> {
                    hiddenToConfirm = event.count
                    null
                }
                is RemoveOriginalsEvent.Removed -> {
                    currentOnRemoved()
                    context.resources.getQuantityString(R.plurals.vault_originals_removed, event.count, formatCount(event.count))
                }
                RemoveOriginalsEvent.Kept -> context.getString(R.string.vault_originals_kept)
                RemoveOriginalsEvent.Failed -> context.getString(R.string.vault_originals_failed)
            }
            if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    hiddenToConfirm?.let { count ->
        HiddenDeleteDialog(
            count = count,
            kind = HiddenDeleteKind.VAULT_ORIGINALS,
            onConfirm = {
                hiddenToConfirm = null
                viewModel.onHiddenResult(accepted = true)
            },
            onDismiss = {
                hiddenToConfirm = null
                viewModel.onHiddenResult(accepted = false)
            },
        )
    }
}

/**
 * Shown while photos are being encrypted, or while vaulted photos still have their original in
 * the gallery. Renders nothing otherwise.
 */
@Composable
fun RemoveOriginalsCard(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onRemoved: () -> Unit = {},
    viewModel: RemoveOriginalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoveOriginalsEffects(viewModel, snackbarHostState, onRemoved)

    if (state.pending == 0 && state.encrypting == 0) return

    GroupedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            if (state.encrypting > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = pluralStringResource(R.plurals.vault_encrypting, state.encrypting, formatCount(state.encrypting)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.pending > 0) {
                if (state.encrypting > 0) Spacer(Modifier.height(12.dp))
                Row {
                    IconBadge(Icons.Rounded.Lock, MaterialTheme.palette.indigo)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = pluralStringResource(R.plurals.vault_pending_title, state.pending, formatCount(state.pending)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.vault_pending_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Wait for encryption to finish so every original goes in one confirmation.
                        TintedButton(
                            text = stringResource(R.string.vault_pending_action),
                            onClick = viewModel::start,
                            enabled = !state.busy && state.encrypting == 0,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}
