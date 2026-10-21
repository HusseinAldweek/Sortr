package com.sortr.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.Person
import com.sortr.domain.model.Photo
import com.sortr.domain.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val unsortedCount: Int = 0,
    val showHidden: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val progress: FaceIndexProgress = FaceIndexProgress(0, 0),
    val modelState: FaceModelState = FaceModelState.READY,
    val loaded: Boolean = false,
) {
    val selectedPeople: List<Person> get() = people.filter { it.id in selected }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val repository: PeopleRepository,
    private val photoActions: FacePhotoActions,
) : ViewModel() {

    private val showHidden = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<PeopleUiState> = combine(
        showHidden.flatMapLatest { repository.observePeople(includeHidden = it) },
        repository.observeUnsortedFaces().map { it.size },
        combine(showHidden, selected, ::Pair),
        repository.observeIndexProgress(),
        repository.modelState,
    ) { people, unsorted, (hidden, selection), progress, model ->
        val ids = people.mapTo(HashSet()) { it.id }
        PeopleUiState(
            people = people,
            unsortedCount = unsorted,
            showHidden = hidden,
            selected = selection intersect ids,
            progress = progress,
            modelState = model,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    fun toggleSelection(personId: Long) {
        selected.update { if (personId in it) it - personId else it + personId }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun toggleShowHidden() {
        showHidden.update { !it }
    }

    /** The person the others are merged into: a named one if any, else the one with the most faces. */
    fun mergeTarget(people: List<Person>): Person? =
        people.sortedWith(compareBy<Person> { it.name == null }.thenByDescending { it.faceCount }).firstOrNull()

    fun mergeSelected() {
        val chosen = state.value.selectedPeople
        val target = mergeTarget(chosen) ?: return
        viewModelScope.launch {
            repository.merge(target.id, chosen.map { it.id } - target.id)
            selected.value = emptySet()
        }
    }

    /** Every photo any of the selected people is in. */
    suspend fun photosOfSelected(): List<Photo> =
        photoActions.photosOf(state.value.selected.flatMap { repository.observeFaces(it).first() })

    /** Hides the selection, or shows it again if every selected person is already hidden. */
    fun toggleHiddenForSelected() {
        val chosen = state.value.selectedPeople
        if (chosen.isEmpty()) return
        val hide = !chosen.all { it.hidden }
        viewModelScope.launch {
            chosen.forEach { repository.setHidden(it.id, hide) }
            selected.value = emptySet()
        }
    }
}
