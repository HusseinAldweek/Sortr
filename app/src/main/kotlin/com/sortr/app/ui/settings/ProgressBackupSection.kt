package com.sortr.app.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.theme.palette
import com.sortr.domain.repository.ProgressFileException
import com.sortr.domain.repository.ProgressImportResult
import com.sortr.domain.repository.SortingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

sealed interface ProgressEvent {
    data class Saved(val count: Int) : ProgressEvent
    data class Restored(val result: ProgressImportResult) : ProgressEvent
    data object NotAProgressFile : ProgressEvent
    data object Failed : ProgressEvent
}

@HiltViewModel
class ProgressBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: SortingProgress,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<ProgressEvent>(Channel.BUFFERED)
    val events: Flow<ProgressEvent> = _events.receiveAsFlow()

    fun save(uri: Uri) = run {
        val count = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { progress.export(it) } ?: throw IOException("Couldn't write $uri")
        }
        ProgressEvent.Saved(count)
    }

    fun restore(uri: Uri) = run {
        val result = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { progress.import(it) } ?: throw IOException("Couldn't read $uri")
        }
        ProgressEvent.Restored(result)
    }

    private fun run(block: suspend () -> ProgressEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val event = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProgressFileException) {
                Log.w(TAG, "Not a progress file", e)
                ProgressEvent.NotAProgressFile
            } catch (e: Exception) {
                Log.w(TAG, "Saving or restoring progress failed", e)
                ProgressEvent.Failed
            } finally {
                _busy.value = false
            }
            _events.send(event)
        }
    }

    private companion object {
        const val TAG = "SortingProgress"
    }
}

/** Settings rows to save sorting progress to a file and bring it back after reinstalling. */
@Composable
fun ProgressBackupSection(snackbarHostState: SnackbarHostState, viewModel: ProgressBackupViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = MaterialTheme.palette

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.save(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val resources = context.resources
            val message = when (event) {
                is ProgressEvent.Saved -> resources.getQuantityString(R.plurals.progress_saved, event.count, formatCount(event.count))
                is ProgressEvent.Restored -> buildString {
                    val result = event.result
                    append(resources.getQuantityString(R.plurals.progress_restored, result.restored, formatCount(result.restored)))
                    if (result.notFound > 0) {
                        append(' ')
                        append(resources.getQuantityString(R.plurals.progress_not_found, result.notFound, formatCount(result.notFound)))
                    }
                }
                ProgressEvent.NotAProgressFile -> context.getString(R.string.progress_not_a_file)
                ProgressEvent.Failed -> context.getString(R.string.progress_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    GroupedSection(header = stringResource(R.string.progress_section), footer = stringResource(R.string.progress_footer)) {
        row {
            ListRow(
                title = stringResource(R.string.progress_save),
                icon = Icons.Rounded.SaveAlt,
                iconColor = palette.blue,
                onClick = if (busy) null else ({ createFile.launch("sortr-progress-${LocalDate.now()}.json") }),
                trailing = if (busy) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
        row {
            ListRow(
                title = stringResource(R.string.progress_restore),
                icon = Icons.Rounded.Restore,
                iconColor = palette.green,
                onClick = if (busy) null else ({ openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }),
            )
        }
    }
}
