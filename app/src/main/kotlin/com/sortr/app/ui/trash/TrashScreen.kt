package com.sortr.app.ui.trash

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sortr.app.R
import com.sortr.app.ui.design.BackButton
import com.sortr.app.image.MediaThumbnail
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.domain.model.Photo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) {
                            pluralStringResource(R.plurals.trash_selected, state.selected.size, formatCount(state.selected.size))
                        } else {
                            stringResource(R.string.trash_title)
                        },
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.trash_clear_selection))
                        }
                    } else {
                        BackButton(onBack)
                    }
                },
            )
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
                state.items.isEmpty() -> EmptyTrash(Modifier.align(Alignment.Center))
                else -> TrashGrid(
                    state = state,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }

    freedBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { freedBytes = null },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.trash_summary,
                        state.items.size,
                        formatCount(state.items.size),
                        formatBytes(context, state.totalBytes),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.trash_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.id }) { photo ->
            TrashTile(
                photo = photo,
                selected = photo.id in state.selected,
                onToggle = { onToggle(photo.id) },
            )
        }
    }
}

@Composable
private fun TrashTile(photo: Photo, selected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier,
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        AsyncImage(
            model = MediaThumbnail(photo.uri.toUri()),
            contentDescription = stringResource(R.string.trash_photo_description, formatDate(photo.dateTaken)),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
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
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            if (selectedCount > 0) {
                FilledTonalButton(
                    onClick = onRestoreSelected,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(pluralStringResource(R.plurals.trash_restore_selected, selectedCount, formatCount(selectedCount)))
                }
            } else {
                OutlinedButton(
                    onClick = onRestoreAll,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.trash_restore_all))
                }
                Button(
                    onClick = onEmptyTrash,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(stringResource(R.string.trash_empty_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTrash(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.trash_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.trash_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
