package com.sortr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.domain.model.LibrarySummary
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val summary: LibrarySummary = LibrarySummary(),
    val isSyncing: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    photos: PhotoRepository,
    gallerySync: GallerySync,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(photos.observeSummary(), gallerySync.isSyncing) { summary, syncing ->
            HomeUiState(summary = summary, isSyncing = syncing, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
