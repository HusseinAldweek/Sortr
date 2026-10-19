package com.sortr.app.ui.trash

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.Photo
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.PhotoRepository
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

data class TrashUiState(
    val items: List<Photo> = emptyList(),
    val totalBytes: Long = 0,
    val selected: Set<Long> = emptySet(),
    val loaded: Boolean = false,
    /** A deletion is waiting on the system dialog or being recorded. */
    val busy: Boolean = false,
)

sealed interface TrashEvent {
    data class ConfirmDeletion(val request: IntentSender) : TrashEvent

    /** Hidden WhatsApp files: Android has no dialog for them, so Sortr asks itself. */
    data class ConfirmHiddenDeletion(val count: Int) : TrashEvent

    /** The folder permission doesn't allow deleting (granted before Sortr asked for that). */
    data object HiddenNotDeleted : TrashEvent
    data class Freed(val bytes: Long) : TrashEvent
    data object DeletionCancelled : TrashEvent
    data object DeletionFailed : TrashEvent
    data class Restored(val count: Int) : TrashEvent
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val photos: PhotoRepository,
    private val gallerySync: GallerySync,
    private val deleteRequests: DeleteRequestFactory,
    private val locator: MediaLocator,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val busy = MutableStateFlow(false)

    /** Photos handed to the system dialog, kept until its result comes back. */
    private var awaitingConfirmation: List<Photo> = emptyList()

    /** Hidden-folder photos waiting for Sortr's own confirmation, after the system dialog. */
    private var awaitingHidden: List<Photo> = emptyList()
    private var freedSoFar = 0L

    val state: StateFlow<TrashUiState> =
        combine(photos.observeTrash(), selected, busy) { items, selected, busy ->
            val ids = items.mapTo(HashSet()) { it.id }
            TrashUiState(
                items = items,
                totalBytes = items.sumOf { it.sizeBytes },
                selected = selected intersect ids,
                loaded = true,
                busy = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    private val _events = Channel<TrashEvent>(Channel.BUFFERED)
    val events: Flow<TrashEvent> = _events.receiveAsFlow()

    fun toggleSelection(id: Long) {
        selected.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Replaces the selection, e.g. while dragging across photos. */
    fun setSelection(ids: Set<Long>) {
        selected.value = ids
    }

    fun restoreSelected() = restore(state.value.selected)

    fun restoreAll() = restore(state.value.items.map { it.id })

    private fun restore(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            photos.restoreFromTrash(ids)
            selected.value = emptySet()
            _events.send(TrashEvent.Restored(ids.size))
        }
    }

    /** Asks Android to delete everything in the Trash, behind one system confirmation. */
    fun emptyTrash() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val items = state.value.items
                // Photos already deleted by another app would make the request fail.
                val existing = gallerySync.existingIds(items.map { it.id })
                val toDelete = items.filter { it.id in existing }
                if (toDelete.size < items.size) gallerySync.requestSync()
                if (toDelete.isEmpty()) {
                    busy.value = false
                    return@launch
                }
                // Gallery photos go through Android's dialog; hidden WhatsApp files are confirmed
                // in Sortr's own dialog afterwards, because Android has none for them.
                val (hidden, gallery) = toDelete.partition { it.fromHiddenFolder }
                awaitingHidden = hidden
                freedSoFar = 0
                if (gallery.isEmpty()) {
                    _events.send(TrashEvent.ConfirmHiddenDeletion(hidden.size))
                    return@launch
                }
                val request = deleteRequests.create(gallery) ?: throw IllegalStateException("Nothing can be deleted")
                awaitingConfirmation = gallery
                _events.send(TrashEvent.ConfirmDeletion(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start the delete request", e)
                awaitingConfirmation = emptyList()
                awaitingHidden = emptyList()
                busy.value = false
                _events.send(TrashEvent.DeletionFailed)
            }
        }
    }

    fun onDeletionResult(accepted: Boolean) {
        val requested = awaitingConfirmation
        awaitingConfirmation = emptyList()
        var waitingForHidden = false
        viewModelScope.launch {
            try {
                if (!accepted || requested.isEmpty()) {
                    // Deny means no deleting at all, hidden files included.
                    awaitingHidden = emptyList()
                    _events.send(TrashEvent.DeletionCancelled)
                    return@launch
                }
                // Count only what is actually gone, in case the system skipped any.
                val stillThere = gallerySync.existingIds(requested.map { it.id })
                val deleted = requested.filterNot { it.id in stillThere }
                freedSoFar = photos.markPurged(deleted)
                selected.value = emptySet()
                if (awaitingHidden.isNotEmpty()) {
                    waitingForHidden = true
                    _events.send(TrashEvent.ConfirmHiddenDeletion(awaitingHidden.size))
                } else {
                    _events.send(TrashEvent.Freed(freedSoFar))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't record the deletion", e)
                gallerySync.requestSync()
                _events.send(TrashEvent.DeletionFailed)
            } finally {
                if (!waitingForHidden) busy.value = false
            }
        }
    }

    /** The answer to Sortr's own dialog for hidden WhatsApp files. */
    fun onHiddenDeletionResult(accepted: Boolean) {
        val requested = awaitingHidden
        awaitingHidden = emptyList()
        viewModelScope.launch {
            try {
                if (!accepted || requested.isEmpty()) {
                    _events.send(if (freedSoFar > 0) TrashEvent.Freed(freedSoFar) else TrashEvent.DeletionCancelled)
                    return@launch
                }
                val deletedIds = locator.deleteHidden(requested.map { it.id })
                val freed = freedSoFar + photos.markPurged(requested.filter { it.id in deletedIds })
                selected.value = emptySet()
                if (freed > 0 || deletedIds.isNotEmpty()) _events.send(TrashEvent.Freed(freed))
                if (deletedIds.size < requested.size) _events.send(TrashEvent.HiddenNotDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't delete hidden files", e)
                _events.send(TrashEvent.DeletionFailed)
            } finally {
                freedSoFar = 0
                busy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "TrashViewModel"
    }
}
