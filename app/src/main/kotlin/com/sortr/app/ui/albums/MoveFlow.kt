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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.domain.model.AlbumPaths
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

/** How a move ended, for the message shown afterwards. */
data class MoveOutcome(val moved: Int, val failed: Int, val targetName: String, val cancelled: Boolean)

/**
 * Moving items between albums: Android asks the user once per batch, and only then are the
 * items moved. Big moves are split into batches so each request stays a safe size.
 */
abstract class MovingViewModel(
    private val library: LibraryRepository,
    private val writeRequests: WriteRequestFactory,
) : ViewModel() {

    private val pending = ArrayDeque<List<Photo>>()
    private var target: String? = null
    private var moved = 0
    private var failed = 0

    private val _moving = MutableStateFlow(false)
    val moving: StateFlow<Boolean> = _moving.asStateFlow()

    private val _requests = Channel<IntentSender>(Channel.BUFFERED)
    val requests: Flow<IntentSender> = _requests.receiveAsFlow()

    private val _outcomes = Channel<MoveOutcome>(Channel.BUFFERED)
    val outcomes: Flow<MoveOutcome> = _outcomes.receiveAsFlow()

    /** Called once all of a move's batches are done and at least one item moved. */
    protected open fun onMoved(target: String) = Unit

    protected fun startMove(photos: List<Photo>, relativePath: String) {
        if (_moving.value) return
        val toMove = photos.filter { it.relativePath != relativePath }
        if (toMove.isEmpty()) return
        pending.clear()
        pending.addAll(toMove.chunked(WriteRequestFactory.MAX_PER_REQUEST))
        target = relativePath
        moved = 0
        failed = 0
        _moving.value = true
        askForNextBatch()
    }

    /** The system dialog's answer for the current batch. */
    fun onWriteResult(granted: Boolean) {
        val batch = pending.removeFirstOrNull() ?: return finish(cancelled = false)
        val relativePath = target ?: return finish(cancelled = false)
        if (!granted) {
            pending.clear()
            return finish(cancelled = true)
        }
        viewModelScope.launch {
            try {
                val result = library.movePhotos(batch, relativePath)
                moved += result.moved
                failed += result.failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Move failed", e)
                failed += batch.size
            }
            if (pending.isEmpty()) finish(cancelled = false) else askForNextBatch()
        }
    }

    private fun askForNextBatch() {
        val batch = pending.firstOrNull() ?: return finish(cancelled = false)
        try {
            _requests.trySend(writeRequests.create(batch))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't create the write request", e)
            failed += pending.sumOf { it.size }
            pending.clear()
            finish(cancelled = false)
        }
    }

    private fun finish(cancelled: Boolean) {
        val relativePath = target
        _moving.value = false
        target = null
        if (relativePath != null) {
            _outcomes.trySend(
                MoveOutcome(moved, failed, AlbumPaths.displayName(relativePath), cancelled = cancelled && moved == 0),
            )
            if (moved > 0) onMoved(relativePath)
        }
    }

    private companion object {
        const val TAG = "MoveFlow"
    }
}

/** Launches the system permission dialogs for [viewModel] and reports how each move went. */
@Composable
fun MoveEffects(viewModel: MovingViewModel, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onWriteResult(granted = result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.requests.collect { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.outcomes.collect { outcome ->
            val resources = context.resources
            val text = when {
                outcome.cancelled -> context.getString(R.string.move_cancelled)
                outcome.failed > 0 && outcome.moved > 0 ->
                    resources.getQuantityString(R.plurals.moved_result, outcome.moved, formatCount(outcome.moved), outcome.targetName) +
                        ". " + resources.getQuantityString(R.plurals.move_failed, outcome.failed, formatCount(outcome.failed))
                outcome.failed > 0 -> resources.getQuantityString(R.plurals.move_failed, outcome.failed, formatCount(outcome.failed))
                else -> resources.getQuantityString(R.plurals.moved_result, outcome.moved, formatCount(outcome.moved), outcome.targetName)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
}
