package com.sortr.app.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortr.app.ui.navigation.PersonRoute
import com.sortr.domain.model.Decision
import com.sortr.domain.model.FaceRef
import com.sortr.domain.model.Person
import com.sortr.domain.model.Photo
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

data class PersonUiState(
    val person: Person? = null,
    val faces: List<FaceRef> = emptyList(),
    val selected: Set<Long> = emptySet(),
    /** Everyone else, for "Merge with…". */
    val others: List<Person> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface PersonEvent {
    data class FacesRemoved(val count: Int) : PersonEvent
    data class PhotosTrashed(val count: Int) : PersonEvent
    data object Merged : PersonEvent
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PeopleRepository,
    private val photoActions: FacePhotoActions,
) : ViewModel() {

    val personId: Long = savedStateHandle.toRoute<PersonRoute>().personId

    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<PersonUiState> = combine(
        repository.observePerson(personId),
        repository.observeFaces(personId),
        repository.observePeople(includeHidden = true),
        selected,
    ) { person, faces, everyone, selection ->
        val ids = faces.mapTo(HashSet()) { it.faceId }
        PersonUiState(
            person = person,
            faces = faces,
            selected = selection intersect ids,
            others = everyone.filter { it.id != personId },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState())

    private val _events = Channel<PersonEvent>(Channel.BUFFERED)
    val events: Flow<PersonEvent> = _events.receiveAsFlow()

    fun toggleFace(faceId: Long) {
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
            _events.send(PersonEvent.PhotosTrashed(lastTrash.size))
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

    fun rename(name: String?) {
        viewModelScope.launch { repository.rename(personId, name) }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { repository.setHidden(personId, hidden) }
    }

    fun mergeWith(other: Person) {
        viewModelScope.launch {
            repository.merge(personId, listOf(other.id))
            _events.send(PersonEvent.Merged)
        }
    }

    /** "Not this person" for the selected faces. */
    fun removeSelected() {
        val faces = state.value.selected
        if (faces.isEmpty()) return
        viewModelScope.launch {
            repository.removeFaces(personId, faces)
            selected.value = emptySet()
            _events.send(PersonEvent.FacesRemoved(faces.size))
        }
    }
}
