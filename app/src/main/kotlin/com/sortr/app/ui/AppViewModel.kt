package com.sortr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(
    val loading: Boolean = true,
    val onboardingDone: Boolean = false,
    val access: MediaAccess = MediaAccess.DENIED,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: AppSettings,
    private val gallerySync: GallerySync,
) : ViewModel() {

    /** Null until the first permission check, so the UI doesn't flash the permission screen. */
    private val access = MutableStateFlow<MediaAccess?>(null)
    private var watchingMediaChanges = false

    val state: StateFlow<AppUiState> = combine(settings.onboardingDone, access) { done, access ->
        AppUiState(
            loading = access == null,
            onboardingDone = done,
            access = access ?: MediaAccess.DENIED,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    /** Called on every resume and after a permission prompt, since access can change in Settings. */
    fun onAccessChecked(newAccess: MediaAccess) {
        access.value = newAccess
        if (newAccess == MediaAccess.DENIED) return
        gallerySync.requestSync()
        if (!watchingMediaChanges) {
            watchingMediaChanges = true
            viewModelScope.launch {
                gallerySync.mediaChanges().collect { gallerySync.requestSync() }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone() }
    }
}
