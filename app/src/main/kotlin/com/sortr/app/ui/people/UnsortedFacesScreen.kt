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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.sortr.app.media.Sharing
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.SelectionToolbar
import com.sortr.app.ui.design.ToolbarItem
import com.sortr.app.ui.design.dragToSelect
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Decision
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

sealed interface UnsortedEvent {
    data class PhotosTrashed(val count: Int) : UnsortedEvent

    /** "Try again" finished; [left] faces are still unsorted. */
    data class Regrouped(val left: Int) : UnsortedEvent
}

@HiltViewModel
class UnsortedFacesViewModel @Inject constructor(
    private val repository: PeopleRepository,
    private val photoActions: FacePhotoActions,
    private val settings: AppSettings,
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

    private val _events = Channel<UnsortedEvent>(Channel.BUFFERED)
    val events: Flow<UnsortedEvent> = _events.receiveAsFlow()

    private val _regrouping = MutableStateFlow(false)
    val regrouping: StateFlow<Boolean> = _regrouping

    /**
     * Matches every face again against everyone, from the stored face data (no photos are
     * scanned). People you named or edited stay as they are.
     */
    fun tryAgain() {
        if (_regrouping.value) return
        _regrouping.value = true
        viewModelScope.launch {
            try {
                repository.regroup(settings.faceSimilarityThreshold.first())
                _events.send(UnsortedEvent.Regrouped(repository.observeUnsortedFaces().first().size))
            } finally {
                _regrouping.value = false
            }
        }
    }

    fun toggle(faceId: Long) {
        selected.update { if (faceId in it) it - faceId else it + faceId }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Replaces the selection, e.g. while dragging across faces. */
    fun setSelection(faceIds: Set<Long>) {
        selected.value = faceIds
    }

    private var lastTrash: List<Decision> = emptyList()

    /** Moves the photos of the selected faces to the Trash, with Undo. */
    fun trashSelected() {
        val faces = selectedFaces()
        if (faces.isEmpty()) return
        viewModelScope.launch {
            lastTrash = photoActions.trash(faces)
            selected.value = emptySet()
            _events.send(UnsortedEvent.PhotosTrashed(lastTrash.size))
        }
    }

    fun undoTrash() {
        val decisions = lastTrash
        lastTrash = emptyList()
        viewModelScope.launch { photoActions.undo(decisions) }
    }

    fun selectedFaces(): List<FaceRef> = state.value.faces.filter { it.faceId in state.value.selected }

    /** The photos behind the selected faces, for viewing. */
    suspend fun selectedPhotos(): List<Photo> = photoActions.photosOf(selectedFaces())

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
    val regrouping by viewModel.regrouping.collectAsStateWithLifecycle()
    val selecting = state.selected.isNotEmpty()
    var picking by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<List<Photo>?>(null) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(viewModel) {
        viewModel.grouped.collect { count ->
            val message = context.resources.getQuantityString(R.plurals.unsorted_grouped, count, formatCount(count))
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UnsortedEvent.PhotosTrashed -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.dup_trashed, event.count, formatCount(event.count)),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash()
                }
                is UnsortedEvent.Regrouped -> scope.launch {
                    snackbarHostState.showSnackbar(
                        context.resources.getQuantityString(R.plurals.unsorted_regrouped, event.left, formatCount(event.left)),
                    )
                }
            }
        }
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LargeTitleScaffold(
        title = stringResource(R.string.unsorted_faces_title),
        onBack = if (selecting) null else onBack,
        actions = {
            if (selecting) {
                BarTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::clearSelection)
            } else if (state.faces.isNotEmpty()) {
                BarTextButton(text = stringResource(R.string.unsorted_try_again), onClick = viewModel::tryAgain, enabled = !regrouping)
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionToolbar(
                    count = state.selected.size,
                    items = listOf(
                        ToolbarItem(Icons.Rounded.PersonAdd, stringResource(R.string.unsorted_add_to_person), { picking = true }),
                        ToolbarItem(Icons.Rounded.GroupAdd, stringResource(R.string.unsorted_new_person), { viewModel.groupSelected(null) }),
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
        if (regrouping) LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth().height(2.dp))
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .dragToSelect(gridState, state.faces.map { it.faceId }, state.selected, viewModel::setSelection),
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

    viewing?.let { photos -> FullScreenViewer(photos, 0, onDismiss = { viewing = null }) }

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
