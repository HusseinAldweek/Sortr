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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.media.ShareItem
import com.sortr.app.media.Sharing
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.common.yearOf
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.MediaTile
import com.sortr.app.ui.design.SelectionToolbar
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.design.ToolbarItem
import com.sortr.app.ui.design.dragToSelect
import com.sortr.app.ui.library.countSummary
import com.sortr.app.ui.navigation.AlbumRoute
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.Decision
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.LibraryRepository
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val photoRepository: PhotoRepository,
    writeRequests: WriteRequestFactory,
    deleteRequests: DeleteRequestFactory,
    locator: MediaLocator,
) : MovingViewModel(library, writeRequests, deleteRequests, locator) {

    private val path = MutableStateFlow(savedStateHandle.toRoute<AlbumRoute>().relativePath)
    val relativePath: StateFlow<String> = path.asStateFlow()

    /** Null while loading. Newest first. */
    val photos: StateFlow<List<Photo>?> = path.flatMapLatest { library.observeAlbumPhotos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = library.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _trashed = Channel<Int>(Channel.BUFFERED)
    /** How many photos the last Delete moved to the Trash, for the Undo message. */
    val trashed: Flow<Int> = _trashed.receiveAsFlow()

    private var renameTarget: String? = null
    private var lastTrash: List<Decision> = emptyList()

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

    /** Splits the album into one album per year: "Camera 2023", "Camera 2024", ... */
    fun organizeByYear() {
        val name = AlbumPaths.cleanName(AlbumPaths.displayName(path.value)) ?: return
        viewModelScope.launch {
            renameTarget = null
            val photos = library.observeAlbumPhotos(path.value).first()
            startPlan(photos.map { it to yearAlbum(name, yearOf(it.dateTaken)) })
        }
    }

    /** Moves the photos to Sortr's Trash, like swiping left. */
    fun trash(photos: List<Photo>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            lastTrash = photoRepository.applyDecisions(photos.filter { it.status != PhotoStatus.TRASHED }, SwipeAction.DELETE)
            _trashed.send(lastTrash.size)
        }
    }

    fun undoTrash() {
        val decisions = lastTrash
        lastTrash = emptyList()
        viewModelScope.launch { photoRepository.undoDecisions(decisions) }
    }

    override fun onMoved(target: String) {
        if (target == renameTarget) path.value = target
        renameTarget = null
    }

    companion object {
        fun yearAlbum(name: String, year: Int): String = AlbumPaths.newAlbum("$name $year")
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var organizing by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()

    MoveEffects(viewModel, snackbarHostState)
    LaunchedEffect(viewModel) {
        viewModel.trashed.collect { count ->
            val result = snackbarHostState.showSnackbar(
                message = context.resources.getQuantityString(R.plurals.dup_trashed, count, formatCount(count)),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash()
        }
    }

    val items = photos.orEmpty()
    val selecting = selectMode || selected.isNotEmpty()
    fun stopSelecting() {
        selectMode = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { stopSelecting() }
    val selectedPhotos = items.filter { it.id in selected }
    // Newest year first, as the photos are already sorted.
    val years = remember(items) { items.groupBy { yearOf(it.dateTaken) }.toList() }
    val albumName = AlbumPaths.displayName(relativePath)

    LargeTitleScaffold(
        title = albumName,
        onBack = if (selecting) null else onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (selecting) {
                BarTextButton(text = stringResource(R.string.action_select_all), onClick = { selected = items.map { it.id }.toSet() })
                BarTextButton(text = stringResource(R.string.action_cancel), onClick = ::stopSelecting, bold = true)
            } else if (items.isNotEmpty()) {
                BarTextButton(text = stringResource(R.string.action_select), onClick = { selectMode = true }, enabled = !moving)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.album_organize_by_year)) },
                            enabled = !moving && years.size > 1,
                            onClick = {
                                menu = false
                                organizing = true
                            },
                        )
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
                SelectionToolbar(
                    count = selected.size,
                    items = listOf(
                        ToolbarItem(Icons.Rounded.Share, stringResource(R.string.action_share), {
                            Sharing.share(context, selectedPhotos.map { ShareItem(it.uri.toUri(), it.mimeType) })
                        }, enabled = selected.isNotEmpty()),
                        ToolbarItem(
                            Icons.AutoMirrored.Rounded.DriveFileMove,
                            stringResource(R.string.album_move_to),
                            { picking = true },
                            enabled = selected.isNotEmpty() && !moving,
                        ),
                        ToolbarItem(
                            Icons.Rounded.Delete,
                            stringResource(R.string.action_delete),
                            {
                                viewModel.trash(selectedPhotos)
                                stopSelecting()
                            },
                            enabled = selected.isNotEmpty(),
                            tint = MaterialTheme.palette.red,
                        ),
                    ),
                )
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
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .dragToSelect(gridState, list.map { it.id }, selected) { if (!moving) selected = it },
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        val toReview = list.count { it.status == PhotoStatus.UNREVIEWED || it.status == PhotoStatus.SKIPPED }
                        AlbumHeader(
                            summary = countSummary(list.size, toReview),
                            canSort = toReview > 0,
                            onSort = { onStartSession(SessionFilter.Album(relativePath).key) },
                        )
                    }
                    years.forEach { (year, yearPhotos) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "year-$year") {
                            val ids = yearPhotos.map { it.id }
                            val allSelected = ids.all { it in selected }
                            YearHeader(
                                year = year,
                                count = yearPhotos.size,
                                selecting = selecting,
                                allSelected = allSelected,
                                onToggle = { selected = if (allSelected) selected - ids.toSet() else selected + ids },
                            )
                        }
                        items(yearPhotos, key = { it.id }) { photo ->
                            MediaTile(
                                photo = photo,
                                selected = if (selecting) photo.id in selected else null,
                                onClick = {
                                    if (selecting) {
                                        selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                                    } else {
                                        viewerIndex = list.indexOf(photo)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    viewerIndex?.let { index -> FullScreenViewer(items, index, onDismiss = { viewerIndex = null }) }

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
            initial = albumName,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (organizing) {
        val newest = years.firstOrNull()?.first
        val oldest = years.lastOrNull()?.first
        AlertDialog(
            onDismissRequest = { organizing = false },
            title = { Text(stringResource(R.string.album_organize_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.album_organize_body,
                        formatCount(years.size),
                        "$albumName ${oldest?.let(::formatYear).orEmpty()}",
                        "$albumName ${newest?.let(::formatYear).orEmpty()}",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        organizing = false
                        viewModel.organizeByYear()
                    },
                ) { Text(stringResource(R.string.album_organize_action)) }
            },
            dismissButton = {
                TextButton(onClick = { organizing = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AlbumHeader(summary: String, canSort: Boolean, onSort: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
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

/** "2024 · 312 items", with Select or Deselect for the whole year while selecting. */
@Composable
private fun YearHeader(year: Int, count: Int, selecting: Boolean, allSelected: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 6.dp)
            // Room for the Select button, so the grid doesn't jump when selecting starts.
            .heightIn(min = 48.dp),
    ) {
        Text(formatYear(year), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            pluralStringResource(R.plurals.count_items, count, formatCount(count)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selecting) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (allSelected) R.string.action_deselect else R.string.action_select))
            }
        }
    }
}
