package com.sortr.app.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.sortr.app.media.Sharing
import com.sortr.app.ui.albums.MoveToAlbumDialogs
import com.sortr.app.ui.albums.MoveToAlbumViewModel
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.SelectionToolbar
import com.sortr.app.ui.design.ToolbarItem
import com.sortr.app.ui.design.dragToSelect
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SessionFilter
import kotlinx.coroutines.launch

@Composable
fun PersonScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
    mover: MoveToAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val moving by mover.moving.collectAsStateWithLifecycle()
    val person = state.person
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val selecting = state.selected.isNotEmpty()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var pickingMerge by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<List<Photo>?>(null) }
    val gridState = rememberLazyGridState()

    // The person can disappear, e.g. when every face was removed.
    LaunchedEffect(state.loaded, person) {
        if (state.loaded && person == null) currentOnBack()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PersonEvent.FacesRemoved -> scope.launch {
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(R.plurals.person_faces_removed, event.count, formatCount(event.count)),
                    )
                }
                PersonEvent.Merged -> scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.person_merged)) }
                is PersonEvent.PhotosTrashed -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.dup_trashed, event.count, formatCount(event.count)),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash()
                }
            }
        }
    }
    // Once a move starts, the selection has done its job.
    LaunchedEffect(moving) {
        if (moving) viewModel.clearSelection()
    }
    MoveToAlbumDialogs(mover, snackbarHostState)
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LargeTitleScaffold(
        title = person?.let { personLabel(it) }.orEmpty(),
        onBack = if (selecting) null else onBack,
        actions = {
            if (selecting) {
                BarTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::clearSelection)
            } else if (person != null) {
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.person_rename_title), tint = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_move_all)) },
                            enabled = !moving,
                            onClick = {
                                menuOpen = false
                                scope.launch { mover.choose(viewModel.allPhotos()) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_merge_with)) },
                            onClick = {
                                menuOpen = false
                                pickingMerge = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (person.hidden) R.string.people_unhide else R.string.people_hide)) },
                            onClick = {
                                menuOpen = false
                                viewModel.setHidden(!person.hidden)
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionToolbar(
                    count = state.selected.size,
                    items = listOf(
                        ToolbarItem(Icons.Rounded.PersonRemove, stringResource(R.string.person_not_this_person_short), viewModel::removeSelected),
                        ToolbarItem(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.album_move_to), {
                            scope.launch { mover.choose(viewModel.selectedPhotos()) }
                        }, enabled = !moving),
                        ToolbarItem(Icons.Rounded.Visibility, stringResource(R.string.action_view), {
                            scope.launch { viewing = viewModel.selectedPhotos() }
                        }),
                        ToolbarItem(Icons.Rounded.Share, stringResource(R.string.action_share), {
                            Sharing.shareUris(context, viewModel.selectedFaces().map { it.photoUri }.distinct())
                        }),
                        ToolbarItem(Icons.Rounded.Delete, stringResource(R.string.action_delete), viewModel::trashSelected, tint = MaterialTheme.palette.red),
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .dragToSelect(gridState, state.faces.map { it.faceId }, state.selected, viewModel::setSelection),
            ) {
                if (person != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            // The name is the screen's title, so it isn't repeated here.
                            FaceImage(face = person.cover, size = 112.dp)
                            Spacer(Modifier.height(8.dp))
                            if (person.name == null) {
                                TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.person_add_name)) }
                            }
                            Text(
                                text = pluralStringResource(R.plurals.photo_count_short, person.photoCount, formatCount(person.photoCount)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { onStartSession(SessionFilter.Person(person.id).key) }) {
                                Icon(Icons.Rounded.Swipe, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.person_sort_photos))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.person_faces_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(state.faces, key = { it.faceId }) { face ->
                    FaceTile(
                        face = face,
                        selected = face.faceId in state.selected,
                        onClick = { viewModel.toggleFace(face.faceId) },
                    )
                }
            }
            if (moving) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
    }

    viewing?.let { photos -> FullScreenViewer(photos, 0, onDismiss = { viewing = null }) }

    if (renaming && person != null) {
        RenameDialog(
            initial = person.name,
            onSave = { name ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (pickingMerge) {
        PersonPickerDialog(
            title = stringResource(R.string.person_merge_with),
            people = state.others,
            onPick = { other ->
                pickingMerge = false
                viewModel.mergeWith(other)
            },
            onDismiss = { pickingMerge = false },
        )
    }
}
