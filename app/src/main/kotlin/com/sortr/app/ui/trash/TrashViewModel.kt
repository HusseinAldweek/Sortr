package com.sortr.app.ui.trash

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.media.DeleteRequestFactory
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
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val busy = MutableStateFlow(false)

    /** Photos handed to the system dialog, kept until its result comes back. */
    private var awaitingConfirmation: List<Photo> = emptyList()

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
                awaitingConfirmation = toDelete
                _events.send(TrashEvent.ConfirmDeletion(deleteRequests.create(toDelete)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start the delete request", e)
                awaitingConfirmation = emptyList()
                busy.value = false
                _events.send(TrashEvent.DeletionFailed)
            }
        }
    }

    fun onDeletionResult(accepted: Boolean) {
        val requested = awaitingConfirmation
        awaitingConfirmation = emptyList()
        viewModelScope.launch {
            try {
                if (!accepted || requested.isEmpty()) {
                    _events.send(TrashEvent.DeletionCancelled)
                    return@launch
                }
                // Count only what is actually gone, in case the system skipped any.
                val stillThere = gallerySync.existingIds(requested.map { it.id })
                val deleted = requested.filterNot { it.id in stillThere }
                val freed = photos.markPurged(deleted)
                selected.value = emptySet()
                _events.send(TrashEvent.Freed(freed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't record the deletion", e)
                gallerySync.requestSync()
                _events.send(TrashEvent.DeletionFailed)
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "TrashViewModel"
    }
}
