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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.SelectionBar
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.domain.model.SessionFilter
import kotlinx.coroutines.launch

@Composable
fun PersonScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val person = state.person
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val selecting = state.selected.isNotEmpty()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var pickingMerge by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    // The person can disappear, e.g. when every face was removed.
    LaunchedEffect(state.loaded, person) {
        if (state.loaded && person == null) currentOnBack()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is PersonEvent.FacesRemoved ->
                    context.resources.getQuantityString(R.plurals.person_faces_removed, event.count, formatCount(event.count))
                PersonEvent.Merged -> context.getString(R.string.person_merged)
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
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
                SelectionBar(count = state.selected.size) {
                    TintedButton(
                        text = stringResource(R.string.person_not_this_person),
                        onClick = viewModel::removeSelected,
                        color = MaterialTheme.palette.red,
                        compact = true,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
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
    }

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
