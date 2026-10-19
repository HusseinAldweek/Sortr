package com.sortr.app.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.SelectionBar
import com.sortr.app.ui.design.TintedButton
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnsortedUiState(
    val faces: List<FaceRef> = emptyList(),
    val people: List<Person> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

@HiltViewModel
class UnsortedFacesViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<UnsortedUiState> = combine(
        repository.observeUnsortedFaces(),
        repository.observePeople(includeHidden = false),
        selected,
    ) { faces, people, selection ->
        val ids = faces.mapTo(HashSet()) { it.faceId }
        UnsortedUiState(faces = faces, people = people, selected = selection intersect ids, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnsortedUiState())

    /** Emits the number of faces grouped. */
    private val _grouped = Channel<Int>(Channel.BUFFERED)
    val grouped: Flow<Int> = _grouped.receiveAsFlow()

    fun toggle(faceId: Long) {
        selected.update { if (faceId in it) it - faceId else it + faceId }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Adds the selected faces to [person], or to a new person when it's null. */
    fun groupSelected(person: Person?) {
        val faces = state.value.selected
        if (faces.isEmpty()) return
        viewModelScope.launch {
            repository.moveFaces(faces, person?.id)
            selected.value = emptySet()
            _grouped.send(faces.size)
        }
    }
}

@Composable
fun UnsortedFacesScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: UnsortedFacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selecting = state.selected.isNotEmpty()
    var picking by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.grouped.collect { count ->
            val message = context.resources.getQuantityString(R.plurals.unsorted_grouped, count, formatCount(count))
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LargeTitleScaffold(
        title = stringResource(R.string.unsorted_faces_title),
        onBack = if (selecting) null else onBack,
        actions = {
            if (selecting) {
                BarTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::clearSelection)
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBar(count = state.selected.size) {
                    TintedButton(text = stringResource(R.string.unsorted_add_to_person), onClick = { picking = true }, compact = true)
                    TintedButton(text = stringResource(R.string.unsorted_new_person), onClick = { viewModel.groupSelected(null) }, compact = true)
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    if (state.loaded && state.faces.isEmpty()) {
                        Spacer(Modifier.height(48.dp))
                        Icon(
                            Icons.Rounded.FaceRetouchingNatural,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.unsorted_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.unsorted_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onStartSession(SessionFilter.UnsortedFaces.key) }) {
                            Icon(Icons.Rounded.Swipe, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.unsorted_sort_photos))
                        }
                    }
                }
            }
            items(state.faces, key = { it.faceId }) { face ->
                FaceTile(face = face, selected = face.faceId in state.selected, onClick = { viewModel.toggle(face.faceId) })
            }
        }
    }

    if (picking) {
        PersonPickerDialog(
            title = stringResource(R.string.unsorted_add_to_person),
            people = state.people,
            onPick = { person ->
                picking = false
                viewModel.groupSelected(person)
            },
            onDismiss = { picking = false },
        )
    }
}
