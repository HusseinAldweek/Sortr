package com.sortr.app.ui.albums

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.media.WriteRequestFactory
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * "Move to…" for screens that aren't an album, like a person's page: give it photos with
 * [choose], the user picks an album or makes a new one, and the photos move there.
 */
@HiltViewModel
class MoveToAlbumViewModel @Inject constructor(
    library: LibraryRepository,
    writeRequests: WriteRequestFactory,
    deleteRequests: DeleteRequestFactory,
    locator: MediaLocator,
) : MovingViewModel(library, writeRequests, deleteRequests, locator) {

    val albums: StateFlow<List<Album>> = library.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var chosen: List<Photo> = emptyList()
    private val _choosing = MutableStateFlow(false)

    /** True while the album picker is open for the photos given to [choose]. */
    val choosing: StateFlow<Boolean> = _choosing.asStateFlow()

    /** Opens the album picker for [photos]. Photos in the Trash or the vault stay where they are. */
    fun choose(photos: List<Photo>) {
        if (moving.value) return
        chosen = photos.filter { it.status != PhotoStatus.TRASHED && it.status != PhotoStatus.VAULTED }
        _choosing.value = chosen.isNotEmpty()
    }

    fun moveTo(relativePath: String) {
        _choosing.value = false
        startMove(chosen, relativePath)
        chosen = emptyList()
    }

    fun cancel() {
        _choosing.value = false
        chosen = emptyList()
    }
}

/** The album picker, the new-album dialog, Android's dialogs and the result message for [viewModel]. */
@Composable
fun MoveToAlbumDialogs(viewModel: MoveToAlbumViewModel, snackbarHostState: SnackbarHostState) {
    val choosing by viewModel.choosing.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }

    MoveEffects(viewModel, snackbarHostState)
    if (!choosing) return
    if (naming) {
        AlbumNameDialog(
            title = stringResource(R.string.album_new_title),
            onConfirm = { name ->
                naming = false
                viewModel.moveTo(AlbumPaths.newAlbum(name))
            },
            onDismiss = {
                naming = false
                viewModel.cancel()
            },
        )
    } else {
        AlbumPickerSheet(
            title = stringResource(R.string.album_move_title),
            albums = albums,
            onPick = { album -> viewModel.moveTo(album.relativePath) },
            onNewAlbum = { naming = true },
            onDismiss = viewModel::cancel,
        )
    }
}
