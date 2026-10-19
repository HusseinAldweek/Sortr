package com.sortr.app.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortr.app.R
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.MediaTile
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.library.countSummary
import com.sortr.app.ui.navigation.AlbumRoute
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    writeRequests: WriteRequestFactory,
) : MovingViewModel(library, writeRequests) {

    private val path = MutableStateFlow(savedStateHandle.toRoute<AlbumRoute>().relativePath)
    val relativePath: StateFlow<String> = path.asStateFlow()

    /** Null while loading. */
    val photos: StateFlow<List<Photo>?> = path.flatMapLatest { library.observeAlbumPhotos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = library.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var renameTarget: String? = null

    fun move(photos: List<Photo>, target: String) {
        renameTarget = null
        startMove(photos, target)
    }

    /** Moves everything into a folder called [name]; the screen then follows the album there. */
    fun rename(name: String) {
        val target = AlbumPaths.renamed(path.value, name)
        if (target == path.value) return
        viewModelScope.launch {
            renameTarget = target
            startMove(library.observeAlbumPhotos(path.value).first(), target)
        }
    }

    override fun onMoved(target: String) {
        if (target == renameTarget) path.value = target
        renameTarget = null
    }
}

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val relativePath by viewModel.relativePath.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val moving by viewModel.moving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

    MoveEffects(viewModel, snackbarHostState)
    fun stopSelecting() {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { stopSelecting() }
    val items = photos.orEmpty()
    val selectedPhotos = items.filter { it.id in selected }

    LargeTitleScaffold(
        title = AlbumPaths.displayName(relativePath),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            BarTextButton(
                text = stringResource(if (selecting) R.string.action_cancel else R.string.action_select),
                onClick = { if (selecting) stopSelecting() else selecting = true },
                enabled = items.isNotEmpty() && !moving,
            )
            if (!selecting && items.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            enabled = !moving,
                            onClick = {
                                menu = false
                                renaming = true
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selecting) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.count_selected, selected.size, formatCount(selected.size)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        BarTextButton(
                            text = stringResource(R.string.action_select_all),
                            onClick = { selected = items.map { it.id }.toSet() },
                        )
                        Spacer(Modifier.width(8.dp))
                        PillButton(
                            text = stringResource(R.string.album_move_to),
                            onClick = { picking = true },
                            enabled = selected.isNotEmpty() && !moving,
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val list = photos
        Column(Modifier.padding(padding)) {
            if (moving) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            when {
                list == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                list.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Collections,
                    iconColor = MaterialTheme.palette.blue,
                    title = stringResource(R.string.album_empty_title),
                    body = stringResource(R.string.album_empty_body),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val toReview = list.count { it.status == PhotoStatus.UNREVIEWED || it.status == PhotoStatus.SKIPPED }
                        AlbumHeader(
                            summary = countSummary(list.size, toReview),
                            canSort = toReview > 0,
                            onSort = { onStartSession(SessionFilter.Album(relativePath).key) },
                        )
                    }
                    items(list, key = { it.id }) { photo ->
                        MediaTile(
                            photo = photo,
                            selected = if (selecting) photo.id in selected else null,
                            onClick = {
                                if (selecting) {
                                    selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                                } else {
                                    viewerPhoto = photo
                                }
                            },
                            onLongClick = {
                                if (!moving) {
                                    selecting = true
                                    selected = selected + photo.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    viewerPhoto?.let { FullScreenViewer(photo = it, onDismiss = { viewerPhoto = null }) }

    if (picking) {
        AlbumPickerSheet(
            title = stringResource(R.string.album_move_title),
            albums = albums.filter { it.relativePath != relativePath },
            onPick = { target ->
                picking = false
                viewModel.move(selectedPhotos, target.relativePath)
                stopSelecting()
            },
            onNewAlbum = {
                picking = false
                naming = true
            },
            onDismiss = { picking = false },
        )
    }
    if (naming) {
        AlbumNameDialog(
            title = stringResource(R.string.album_new_title),
            onConfirm = { name ->
                naming = false
                viewModel.move(selectedPhotos, AlbumPaths.newAlbum(name))
                stopSelecting()
            },
            onDismiss = { naming = false },
        )
    }
    if (renaming) {
        AlbumNameDialog(
            title = stringResource(R.string.album_rename_title),
            body = stringResource(R.string.album_rename_body),
            initial = AlbumPaths.displayName(relativePath),
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun AlbumHeader(summary: String, canSort: Boolean, onSort: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canSort) {
            TintedButton(text = stringResource(R.string.album_sort_this), onClick = onSort, icon = Icons.Rounded.Style, compact = true)
        }
    }
}
