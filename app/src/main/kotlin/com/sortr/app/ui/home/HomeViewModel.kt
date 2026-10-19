package com.sortr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.faces.ScanStatus
import com.sortr.app.faces.FaceScanScheduler
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.Person
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.PeopleRepository
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FaceScanUiState(
    val modelState: FaceModelState = FaceModelState.READY,
    val progress: FaceIndexProgress = FaceIndexProgress(0, 0),
    val status: ScanStatus = ScanStatus.IDLE,
    val onlyWhileCharging: Boolean = false,
)

data class HomeUiState(
    val summary: LibrarySummary = LibrarySummary(),
    val isSyncing: Boolean = false,
    val vaultSetUp: Boolean = false,
    val people: List<Person> = emptyList(),
    val unsortedFaces: Int = 0,
    val faceScan: FaceScanUiState = FaceScanUiState(),
    val notificationAsked: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    photos: PhotoRepository,
    gallerySync: GallerySync,
    vaultAuth: VaultAuth,
    people: PeopleRepository,
    scanScheduler: FaceScanScheduler,
    private val settings: AppSettings,
) : ViewModel() {

    private val faceScan = combine(
        people.modelState,
        people.observeIndexProgress(),
        scanScheduler.status(),
        settings.scanOnlyWhileCharging,
    ) { model, progress, status, charging -> FaceScanUiState(model, progress, status, charging) }

    private val peopleInfo = combine(
        people.observePeople(includeHidden = false).map { it.take(HOME_PEOPLE_LIMIT) },
        people.observeUnsortedFaces().map { it.size },
        ::Pair,
    )

    val state: StateFlow<HomeUiState> = combine(
        combine(photos.observeSummary(), gallerySync.isSyncing, vaultAuth.isSetUp, ::Triple),
        peopleInfo,
        faceScan,
        settings.notificationPermissionAsked,
    ) { (summary, syncing, vaultSetUp), (people, unsorted), scan, asked ->
        HomeUiState(
            summary = summary,
            isSyncing = syncing,
            vaultSetUp = vaultSetUp,
            people = people,
            unsortedFaces = unsorted,
            faceScan = scan,
            notificationAsked = asked,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { settings.setNotificationPermissionAsked() }
    }

    private companion object {
        const val HOME_PEOPLE_LIMIT = 15
    }
}
