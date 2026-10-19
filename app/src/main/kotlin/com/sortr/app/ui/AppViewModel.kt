package com.sortr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.faces.FaceScanScheduler
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val loading: Boolean = true,
    val onboardingDone: Boolean = false,
    val access: MediaAccess = MediaAccess.DENIED,
    /** False when photos are allowed but videos aren't. */
    val videoAccess: Boolean = true,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: AppSettings,
    private val gallerySync: GallerySync,
    private val faceScan: FaceScanScheduler,
) : ViewModel() {

    /** Null until the first permission check, so the UI doesn't flash the permission screen. */
    private val access = MutableStateFlow<MediaAccess?>(null)
    private val videoAccess = MutableStateFlow(true)
    private var watching = false

    val state: StateFlow<AppUiState> = combine(settings.onboardingDone, access, videoAccess) { done, access, videos ->
        AppUiState(
            loading = access == null,
            onboardingDone = done,
            access = access ?: MediaAccess.DENIED,
            videoAccess = videos,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /** Called on every resume and after a permission prompt, since access can change in Settings. */
    fun onAccessChecked(newAccess: MediaAccess, hasVideoAccess: Boolean) {
        access.value = newAccess
        videoAccess.value = hasVideoAccess
        if (newAccess == MediaAccess.DENIED) return
        gallerySync.requestSync()
        viewModelScope.launch { faceScan.schedule() }
        if (!watching) {
            watching = true
            viewModelScope.launch {
                gallerySync.mediaChanges().collect { gallerySync.requestSync() }
            }
            // New photos found by a sync get scanned for faces too.
            viewModelScope.launch {
                gallerySync.isSyncing.drop(1).filter { syncing -> !syncing }.collect { faceScan.schedule() }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone() }
    }
}
