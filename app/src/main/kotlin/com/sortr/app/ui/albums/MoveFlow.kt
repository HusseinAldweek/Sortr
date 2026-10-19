package com.sortr.app.ui.albums

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.whatsapp.HiddenDeleteDialog
import com.sortr.app.ui.whatsapp.HiddenDeleteKind
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.MovedCopy
import com.sortr.domain.model.Photo
import com.sortr.domain.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** How a move ended, for the message shown afterwards. [albums] > 1 for an organize-by-year move. */
data class MoveOutcome(val moved: Int, val failed: Int, val targetName: String, val albums: Int, val cancelled: Boolean)

/**
 * Moving items between albums. Android asks once per batch (up to 1,000 items) for permission,
 * and only then are items moved. Items Android won't move in place (another app's own folder,
 * hidden WhatsApp folders) are copied instead; afterwards the user is asked once to remove those
 * originals: Android's dialog for gallery items, Sortr's own for hidden WhatsApp files. Copies
 * whose original stays are removed again, so nothing ends up twice.
 */
abstract class MovingViewModel(
    private val library: LibraryRepository,
    private val writeRequests: WriteRequestFactory,
    private val deleteRequests: DeleteRequestFactory,
    private val locator: MediaLocator,
) : ViewModel() {

    private class Item(val photo: Photo, val target: String)

    private val pending = ArrayDeque<List<Item>>()
    private val copies = ArrayList<MovedCopy>()
    private var hiddenCopies: List<MovedCopy> = emptyList()
    private var targets: Set<String> = emptySet()
    private var moved = 0
    private var failed = 0

    private val _moving = MutableStateFlow(false)
    val moving: StateFlow<Boolean> = _moving.asStateFlow()

    private val _requests = Channel<IntentSender>(Channel.BUFFERED)
    /** Android's "allow Sortr to modify" dialogs, one per batch. */
    val requests: Flow<IntentSender> = _requests.receiveAsFlow()

    private val _originalRemovals = Channel<IntentSender>(Channel.BUFFERED)
    /** Android's dialog to remove the originals of copied gallery items. */
    val originalRemovals: Flow<IntentSender> = _originalRemovals.receiveAsFlow()

    private val _hiddenConfirmations = Channel<Int>(Channel.BUFFERED)
    /** How many hidden WhatsApp originals Sortr's own dialog should ask about. */
    val hiddenConfirmations: Flow<Int> = _hiddenConfirmations.receiveAsFlow()

    private val _outcomes = Channel<MoveOutcome>(Channel.BUFFERED)
    val outcomes: Flow<MoveOutcome> = _outcomes.receiveAsFlow()

    /** Called once a single-album move is done and at least one item moved. */
    protected open fun onMoved(target: String) = Unit

    protected fun startMove(photos: List<Photo>, relativePath: String) = startPlan(photos.map { it to relativePath })

    /** Moves each photo into its own target, e.g. one album per year. */
    protected fun startPlan(plan: List<Pair<Photo, String>>) {
        if (_moving.value) return
        val items = plan.filter { (photo, target) -> photo.relativePath != target }.map { (photo, target) -> Item(photo, target) }
        if (items.isEmpty()) return
        pending.clear()
        pending.addAll(items.chunked(WriteRequestFactory.MAX_PER_REQUEST))
        copies.clear()
        hiddenCopies = emptyList()
        targets = items.mapTo(LinkedHashSet()) { it.target }
        moved = 0
        failed = 0
        _moving.value = true
        askForNextBatch()
    }

    /** The system dialog's answer for the current batch. */
    fun onWriteResult(granted: Boolean) {
        val batch = pending.removeFirstOrNull() ?: return removeOriginals()
        if (!granted) {
            // Deny stops the move; anything already copied still gets its originals step.
            pending.clear()
            return removeOriginals(cancelled = moved == 0 && copies.isEmpty())
        }
        process(batch)
    }

    private fun process(batch: List<Item>) {
        viewModelScope.launch {
            for ((target, items) in batch.groupBy { it.target }) {
                try {
                    val result = library.movePhotos(items.map { it.photo }, target)
                    moved += result.moved
                    failed += result.failed
                    copies += result.copies
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Move failed", e)
                    failed += items.size
                }
            }
            if (pending.isEmpty()) removeOriginals() else askForNextBatch()
        }
    }

    private fun askForNextBatch() {
        val batch = pending.firstOrNull() ?: return removeOriginals()
        viewModelScope.launch {
            try {
                val request = writeRequests.create(batch.map { it.photo })
                if (request == null) {
                    // Only hidden WhatsApp files: they're copied, which needs no permission here.
                    pending.removeFirst()
                    process(batch)
                } else {
                    _requests.send(request)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't create the write request", e)
                failed += pending.sumOf { it.size }
                pending.clear()
                removeOriginals()
            }
        }
    }

    private fun removeOriginals(cancelled: Boolean = false) {
        if (copies.isEmpty()) return finish(cancelled)
        val (hidden, gallery) = copies.partition { it.original.fromHiddenFolder }
        hiddenCopies = hidden
        val request = runCatching { deleteRequests.create(gallery.map { it.original }) }
            .onFailure { Log.w(TAG, "Couldn't ask to remove originals", it) }
            .getOrNull()
        if (request != null) _originalRemovals.trySend(request) else askAboutHidden()
    }

    /** Android's answer about removing the copied gallery originals. */
    fun onDeleteOriginalsResult(granted: Boolean) {
        // No means no: hidden originals aren't asked about either, and all copies are undone.
        if (!granted) hiddenCopies = emptyList()
        askAboutHidden()
    }

    private fun askAboutHidden() {
        if (hiddenCopies.isNotEmpty()) _hiddenConfirmations.trySend(hiddenCopies.size) else complete()
    }

    /** The answer to Sortr's own dialog about hidden WhatsApp originals. */
    fun onHiddenOriginalsResult(accepted: Boolean) {
        val toDelete = hiddenCopies
        hiddenCopies = emptyList()
        viewModelScope.launch {
            if (accepted) runCatching { locator.deleteHidden(toDelete.map { it.original.id }) }
            complete()
        }
    }

    private fun complete() {
        viewModelScope.launch {
            val total = copies.size
            val kept = runCatching { library.completeCopies(copies.toList()) }
                .onFailure { Log.w(TAG, "Couldn't finish copied items", it) }
                .getOrDefault(0)
            moved += kept
            failed += total - kept
            copies.clear()
            finish(cancelled = false)
        }
    }

    private fun finish(cancelled: Boolean) {
        val single = targets.singleOrNull()
        _moving.value = false
        if (targets.isNotEmpty()) {
            _outcomes.trySend(
                MoveOutcome(
                    moved = moved,
                    failed = failed,
                    targetName = single?.let(AlbumPaths::displayName).orEmpty(),
                    albums = targets.size,
                    cancelled = cancelled && moved == 0,
                ),
            )
            if (single != null && moved > 0) onMoved(single)
        }
        targets = emptySet()
    }

    private companion object {
        const val TAG = "MoveFlow"
    }
}

/** Launches the system dialogs for [viewModel], Sortr's own dialog for hidden files, and reports the result. */
@Composable
fun MoveEffects(viewModel: MovingViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    var hiddenToConfirm by rememberSaveable { mutableStateOf<Int?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onWriteResult(granted = result.resultCode == Activity.RESULT_OK)
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteOriginalsResult(granted = result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { writeLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.originalRemovals.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.hiddenConfirmations.collect { hiddenToConfirm = it }
    }
    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            val resources = context.resources
            val movedText = if (outcome.albums > 1) {
                resources.getQuantityString(R.plurals.moved_result_albums, outcome.moved, formatCount(outcome.moved), formatCount(outcome.albums))
            } else {
                resources.getQuantityString(R.plurals.moved_result, outcome.moved, formatCount(outcome.moved), outcome.targetName)
            }
            val text = when {
                outcome.cancelled -> context.getString(R.string.move_cancelled)
                outcome.failed > 0 && outcome.moved > 0 ->
                    movedText + ". " + resources.getQuantityString(R.plurals.move_failed, outcome.failed, formatCount(outcome.failed))
                outcome.failed > 0 -> resources.getQuantityString(R.plurals.move_failed, outcome.failed, formatCount(outcome.failed))
                else -> movedText
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    hiddenToConfirm?.let { count ->
        HiddenDeleteDialog(
            count = count,
            kind = HiddenDeleteKind.MOVED_ORIGINALS,
            onConfirm = {
                hiddenToConfirm = null
                viewModel.onHiddenOriginalsResult(accepted = true)
            },
            onDismiss = {
                hiddenToConfirm = null
                viewModel.onHiddenOriginalsResult(accepted = false)
            },
        )
    }
}
