package com.sortr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val photos: PhotoRepository,
) : ViewModel() {

    private val _resetDone = Channel<Unit>(Channel.CONFLATED)
    val resetDone: Flow<Unit> = _resetDone.receiveAsFlow()

    fun resetReviewed() {
        viewModelScope.launch {
            photos.resetReviewed()
            _resetDone.send(Unit)
        }
    }
}
