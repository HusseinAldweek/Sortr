package com.sortr.app.ui.trash

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.MediaTile
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var freedBytes by rememberSaveable { mutableStateOf<Long?>(null) }
    val selecting = state.selected.isNotEmpty()

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeletionResult(accepted = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrashEvent.ConfirmDeletion ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(event.request).build())
                is TrashEvent.Freed -> freedBytes = event.bytes
                TrashEvent.DeletionCancelled -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.trash_delete_cancelled))
                }
                TrashEvent.DeletionFailed -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.trash_delete_failed))
                }
                is TrashEvent.Restored -> scope.launch {
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(R.plurals.trash_restored, event.count, formatCount(event.count)),
                    )
                }
            }
        }
    }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LargeTitleScaffold(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = {
            if (selecting) {
                BarTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::clearSelection)
            }
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                TrashActions(
                    selectedCount = state.selected.size,
                    busy = state.busy,
                    onRestoreSelected = viewModel::restoreSelected,
                    onRestoreAll = viewModel::restoreAll,
                    onEmptyTrash = viewModel::emptyTrash,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.DeleteOutline,
                    iconColor = MaterialTheme.palette.gray,
                    title = stringResource(R.string.trash_empty_title),
                    body = stringResource(R.string.trash_empty_body),
                )
                else -> TrashGrid(state = state, onToggle = viewModel::toggleSelection)
            }
        }
    }

    freedBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { freedBytes = null },
            icon = { IconBadge(Icons.Rounded.Check, MaterialTheme.palette.green, size = 48.dp) },
            title = { Text(stringResource(R.string.trash_freed_title)) },
            text = {
                Text(
                    text = stringResource(R.string.trash_freed, formatBytes(context, bytes)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { freedBytes = null }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }
}

@Composable
private fun TrashGrid(
    state: TrashUiState,
    onToggle: (Long) -> Unit,
) {
    val context = LocalContext.current
    val selecting = state.selected.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                Text(
                    text = if (selecting) {
                        pluralStringResource(R.plurals.trash_selected, state.selected.size, formatCount(state.selected.size))
                    } else {
                        pluralStringResource(
                            R.plurals.trash_summary,
                            state.items.size,
                            formatCount(state.items.size),
                            formatBytes(context, state.totalBytes),
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.trash_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.id }) { photo ->
            MediaTile(
                photo = photo,
                selected = if (selecting) photo.id in state.selected else null,
                onClick = { onToggle(photo.id) },
                onLongClick = { onToggle(photo.id) },
                contentDescription = stringResource(R.string.trash_photo_description, formatDate(photo.dateTaken)),
            )
        }
    }
}

@Composable
private fun TrashActions(
    selectedCount: Int,
    busy: Boolean,
    onRestoreSelected: () -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            if (selectedCount > 0) {
                PillButton(
                    text = pluralStringResource(R.plurals.trash_restore_selected, selectedCount, formatCount(selectedCount)),
                    onClick = onRestoreSelected,
                    enabled = !busy,
                )
            } else {
                TintedButton(
                    text = stringResource(R.string.trash_restore_all),
                    onClick = onRestoreAll,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.trash_empty_action),
                    onClick = onEmptyTrash,
                    enabled = !busy,
                    color = MaterialTheme.palette.red,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
