package com.sortr.app.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.domain.model.FaceModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenUnsorted: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selecting = state.selected.isNotEmpty()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmMerge by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) {
                            pluralStringResource(R.plurals.trash_selected, state.selected.size, formatCount(state.selected.size))
                        } else {
                            stringResource(R.string.people_title)
                        },
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.trash_clear_selection))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = { confirmMerge = true }, enabled = state.selected.size >= 2) {
                            Text(stringResource(R.string.people_merge))
                        }
                        val allHidden = state.selectedPeople.all { it.hidden }
                        IconButton(onClick = viewModel::toggleHiddenForSelected) {
                            Icon(
                                imageVector = if (allHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = stringResource(if (allHidden) R.string.people_unhide else R.string.people_hide),
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.people_show_hidden)) },
                                    leadingIcon = { Checkbox(checked = state.showHidden, onCheckedChange = null) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.toggleShowHidden()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (!state.progress.isComplete && state.modelState == FaceModelState.READY) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ScanProgressLine(indexed = state.progress.indexed, total = state.progress.total)
                }
            }
            if (state.unsortedCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    UnsortedFacesCard(count = state.unsortedCount, onClick = onOpenUnsorted)
                }
            }
            if (state.loaded && state.people.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PeopleEmptyState(scanning = !state.progress.isComplete)
                }
            }
            items(state.people, key = { it.id }) { person ->
                PersonCell(
                    person = person,
                    avatarSize = 84.dp,
                    selected = person.id in state.selected,
                    onClick = { if (selecting) viewModel.toggleSelection(person.id) else onOpenPerson(person.id) },
                    onLongClick = { viewModel.toggleSelection(person.id) },
                )
            }
        }
    }

    if (confirmMerge) {
        val chosen = state.selectedPeople
        val target = viewModel.mergeTarget(chosen)
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text(pluralStringResource(R.plurals.people_merge_title, chosen.size, formatCount(chosen.size))) },
            text = {
                Text(
                    if (target?.name != null) {
                        stringResource(R.string.people_merge_body_named, target.name!!)
                    } else {
                        stringResource(R.string.people_merge_body)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmMerge = false
                        viewModel.mergeSelected()
                    },
                ) { Text(stringResource(R.string.people_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun ScanProgressLine(indexed: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.face_scan_progress, formatCount(indexed), formatCount(total)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else indexed.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UnsortedFacesCard(count: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.FaceRetouchingNatural, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.unsorted_faces_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(R.plurals.face_count, count, formatCount(count)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PeopleEmptyState(scanning: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 24.dp, end = 24.dp),
    ) {
        Icon(Icons.Rounded.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (scanning) R.string.people_empty_scanning else R.string.people_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
