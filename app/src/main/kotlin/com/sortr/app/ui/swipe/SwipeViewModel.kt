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
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.session.SwipeDeck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

@HiltViewModel
class SwipeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photos: PhotoRepository,
) : ViewModel() {

    val filter: SessionFilter = SessionFilter.fromKey(savedStateHandle.toRoute<SwipeRoute>().filterKey)

    /** Private (swipe up) arrives with the vault in Phase 2. */
    val enabledActions: Set<SwipeAction> = setOf(SwipeAction.KEEP, SwipeAction.DELETE, SwipeAction.SKIP)

    // Decisions and undos are applied one at a time, in the order the user made them.
    private val mutex = Mutex()
    private var deck: SwipeDeck? = null
    private var counts = SessionCounts()
    private var revision = 0

    private val _state = MutableStateFlow<SwipeUiState>(SwipeUiState.Loading)
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val _saveErrors = Channel<Unit>(Channel.CONFLATED)
    val saveErrors: Flow<Unit> = _saveErrors.receiveAsFlow()

    init {
        viewModelScope.launch {
            mutex.withLock {
                deck = SwipeDeck(photos.loadSession(filter))
                publish()
            }
        }
    }

    fun onSwipe(photoId: Long, action: SwipeAction) {
        if (action !in enabledActions) return
        viewModelScope.launch {
            mutex.withLock {
                val deck = deck ?: return@withLock
                // Ignore stale commits, e.g. a card that finished flying out after an undo.
                val photo = deck.current?.takeIf { it.id == photoId } ?: return@withLock
                runSaving {
                    deck.commit(photos.applyDecision(photo, action))
                    counts = counts.adjust(action, +1)
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
                runSaving {
                    photos.undoDecision(decision)
                    deck.undoLast()
                    counts = counts.adjust(decision.action, -1)
                }
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
            _saveErrors.trySend(Unit)
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
