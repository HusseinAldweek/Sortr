package com.sortr.app.ui.swipe

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortr.app.ui.navigation.SwipeRoute
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.PeopleRepository
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.repository.VaultRepository
import com.sortr.domain.session.SwipeDeck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Tally of this session's decisions, for the summary at the end. */
data class SessionCounts(
    val kept: Int = 0,
    val trashed: Int = 0,
    val skipped: Int = 0,
    val vaulted: Int = 0,
) {
    fun adjust(action: SwipeAction, delta: Int) = when (action) {
        SwipeAction.KEEP -> copy(kept = kept + delta)
        SwipeAction.DELETE -> copy(trashed = trashed + delta)
        SwipeAction.SKIP -> copy(skipped = skipped + delta)
        SwipeAction.PRIVATE -> copy(vaulted = vaulted + delta)
    }
}

sealed interface SwipeUiState {
    data object Loading : SwipeUiState

    data class Ready(
        val current: Photo?,
        val upcoming: List<Photo>,
        val position: Int,
        val total: Int,
        val canUndo: Boolean,
        val counts: SessionCounts,
        /** Bumped on every update, so a card whose save failed snaps back into place. */
        val revision: Int,
    ) : SwipeUiState
}

enum class SwipeMessage { SAVE_FAILED, VAULT_FAILED, UNDO_BLOCKED }

@HiltViewModel
class SwipeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photos: PhotoRepository,
    private val vault: VaultRepository,
    private val gallerySync: GallerySync,
    vaultAuth: VaultAuth,
    people: PeopleRepository,
) : ViewModel() {

    val filter: SessionFilter = SessionFilter.fromKey(savedStateHandle.toRoute<SwipeRoute>().filterKey)

    val enabledActions: Set<SwipeAction> = SwipeAction.entries.toSet()

    /** Swiping up needs a vault PIN first. */
    val vaultReady: StateFlow<Boolean> =
        vaultAuth.isSetUp.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The person's name, for a person session. */
    val personName: StateFlow<String?> = when (val f = filter) {
        is SessionFilter.Person -> people.observePerson(f.personId).map { it?.name }
        else -> flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Decisions and undos are applied one at a time, in the order the user made them.
    private val mutex = Mutex()
    private var deck: SwipeDeck? = null
    private var counts = SessionCounts()
    private var revision = 0

    private val _state = MutableStateFlow<SwipeUiState>(SwipeUiState.Loading)
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val _messages = Channel<SwipeMessage>(Channel.BUFFERED)
    val messages: Flow<SwipeMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            mutex.withLock {
                deck = SwipeDeck(photos.loadSession(filter))
                publish()
            }
        }
        viewModelScope.launch {
            vault.encryptionFailures.collect { _messages.send(SwipeMessage.VAULT_FAILED) }
        }
    }

    /** Swiping up needs a vault, and only photos can go in it. */
    fun canCommit(photo: Photo, action: SwipeAction): Boolean =
        action != SwipeAction.PRIVATE || (vaultReady.value && !photo.isVideo)

    fun onSwipe(swiped: Photo, action: SwipeAction) {
        if (action !in enabledActions || !canCommit(swiped, action)) return
        viewModelScope.launch {
            mutex.withLock {
                val deck = deck ?: return@withLock
                // Ignore stale commits, e.g. a card that finished flying out after an undo.
                val photo = deck.current?.takeIf { it.id == swiped.id } ?: return@withLock
                runSaving {
                    deck.commit(photos.applyDecision(photo, action))
                    counts = counts.adjust(action, +1)
                    if (action == SwipeAction.PRIVATE) vault.enqueue(photo)
                }
                publish()
            }
        }
    }

    fun onUndo() {
        viewModelScope.launch {
            mutex.withLock {
                val deck = deck ?: return@withLock
                val decision = deck.lastDecision ?: return@withLock
                val photoId = decision.photo.id
                if (decision.action == SwipeAction.PRIVATE && gallerySync.existingIds(listOf(photoId)).isEmpty()) {
                    // The original already left the gallery; the photo now lives only in the vault.
                    deck.clearHistory()
                    _messages.send(SwipeMessage.UNDO_BLOCKED)
                    publish()
                    return@withLock
                }
                runSaving {
                    photos.undoDecision(decision)
                    deck.undoLast()
                    counts = counts.adjust(decision.action, -1)
                    // After the status is restored, so a queued encryption skips the photo.
                    if (decision.action == SwipeAction.PRIVATE) vault.cancel(photoId)
                }
                publish()
            }
        }
    }

    /** Vaulted originals were deleted, so those decisions can no longer be undone. */
    fun onOriginalsRemoved() {
        viewModelScope.launch {
            mutex.withLock {
                deck?.clearHistory()
                publish()
            }
        }
    }

    private suspend fun runSaving(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saving a decision failed", e)
            _messages.send(SwipeMessage.SAVE_FAILED)
        }
    }

    private fun publish() {
        val deck = deck ?: return
        revision++
        _state.value = SwipeUiState.Ready(
            current = deck.current,
            upcoming = deck.upcoming(PRELOAD_COUNT),
            position = deck.position,
            total = deck.total,
            canUndo = deck.canUndo,
            counts = counts,
            revision = revision,
        )
    }

    private companion object {
        const val TAG = "SwipeViewModel"
        const val PRELOAD_COUNT = 3
    }
}
