package com.sortr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.faces.FaceScanScheduler
import com.sortr.app.faces.ScanStatus
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.FaceIndexProgress
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.model.Person
import com.sortr.domain.model.QuickCounts
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SourceCount
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.LibraryRepository
import com.sortr.domain.repository.PeopleRepository
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FaceScanUiState(
    val modelState: FaceModelState = FaceModelState.READY,
    val progress: FaceIndexProgress = FaceIndexProgress(0, 0),
    val status: ScanStatus = ScanStatus.IDLE,
    val onlyWhileCharging: Boolean = false,
)

data class BrowseUiState(
    val counts: QuickCounts = QuickCounts(),
    /** Extra copies in groups of similar photos that are still to be sorted; null until counted. */
    val similarToReview: Int? = null,
    val sources: List<SourceCount> = emptyList(),
    val analysis: AnalysisProgress = AnalysisProgress(0, 0),
)

data class HomeUiState(
    val summary: LibrarySummary = LibrarySummary(),
    val isSyncing: Boolean = false,
    val vaultSetUp: Boolean = false,
    val people: List<Person> = emptyList(),
    val unsortedFaces: Int = 0,
    val faceScan: FaceScanUiState = FaceScanUiState(),
    val browse: BrowseUiState = BrowseUiState(),
    val notificationAsked: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    photos: PhotoRepository,
    gallerySync: GallerySync,
    vaultAuth: VaultAuth,
    people: PeopleRepository,
    library: LibraryRepository,
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

    // Grouping duplicates reads every hash, so while photos are being checked it's redone at
    // most every few seconds, always ending with the latest state.
    private val similarToReview = combine(photos.observeSummary(), library.observeAnalysisProgress(), ::Pair)
        .conflate()
        .transform<Pair<LibrarySummary, AnalysisProgress>, Int?> {
            emit(library.countToReview(SessionFilter.Duplicates))
            delay(SIMILAR_THROTTLE_MS)
        }
        .onStart { emit(null) }
        .distinctUntilChanged()

    private val browse = combine(
        library.observeQuickCounts(),
        similarToReview,
        library.observeSources(),
        library.observeAnalysisProgress(),
        ::BrowseUiState,
    )

    val state: StateFlow<HomeUiState> = combine(
        combine(photos.observeSummary(), gallerySync.isSyncing, vaultAuth.isSetUp, ::Triple),
        peopleInfo,
        faceScan,
        browse,
        settings.notificationPermissionAsked,
    ) { (summary, syncing, vaultSetUp), (people, unsorted), scan, browse, asked ->
        HomeUiState(
            summary = summary,
            isSyncing = syncing,
            vaultSetUp = vaultSetUp,
            people = people,
            unsortedFaces = unsorted,
            faceScan = scan,
            browse = browse,
            notificationAsked = asked,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { settings.setNotificationPermissionAsked() }
    }

    private companion object {
        const val HOME_PEOPLE_LIMIT = 15
        const val SIMILAR_THROTTLE_MS = 3_000L
    }
}
