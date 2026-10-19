package com.sortr.app.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatYear
import com.sortr.app.ui.common.yearOf
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SelectionMark
import com.sortr.app.ui.design.dragToSelect
import com.sortr.app.ui.theme.palette
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.Album
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject

/** How the album list is ordered; YEAR groups albums under the year of their newest item. */
enum class AlbumSort { MOST, LARGEST, NAME, RECENT, YEAR }

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: AppSettings,
    writeRequests: WriteRequestFactory,
    deleteRequests: DeleteRequestFactory,
    locator: MediaLocator,
) : MovingViewModel(library, writeRequests, deleteRequests, locator) {

    /** Remembered between visits, like the Photos app. */
    val sort: StateFlow<AlbumSort> = settings.albumSort
        .map { saved -> AlbumSort.entries.firstOrNull { it.name == saved } ?: AlbumSort.MOST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSort.MOST)

    fun setSort(value: AlbumSort) {
        viewModelScope.launch { settings.setAlbumSort(value.name) }
    }

    /** Null while loading. */
    val albums: StateFlow<List<Album>?> = combine(library.observeAlbums(), sort) { albums, sort ->
        when (sort) {
            AlbumSort.MOST -> albums.sortedByDescending { it.count }
            AlbumSort.LARGEST -> albums.sortedByDescending { it.sizeBytes }
            AlbumSort.NAME -> Collator.getInstance().let { collator -> albums.sortedWith { a, b -> collator.compare(a.name, b.name) } }
            AlbumSort.RECENT, AlbumSort.YEAR -> albums.sortedByDescending { it.newest }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves everything in [sources] into [target]. */
    fun merge(sources: Collection<String>, target: String) {
        viewModelScope.launch {
            val photos = sources.filter { it != target }.flatMap { library.observeAlbumPhotos(it).first() }
            startMove(photos, target)
        }
    }
}

@Composable
fun AlbumsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (relativePath: String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val moving by viewModel.moving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    MoveEffects(viewModel, snackbarHostState)
    fun stopSelecting() {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    LargeTitleScaffold(
        title = stringResource(R.string.albums_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (!selecting) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.albums_sort), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        AlbumSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null) },
                                onClick = {
                                    viewModel.setSort(option)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
            BarTextButton(
                text = stringResource(if (selecting) R.string.action_cancel else R.string.action_select),
                onClick = { if (selecting) stopSelecting() else selecting = true },
                enabled = !albums.isNullOrEmpty() && !moving,
            )
        },
        bottomBar = {
            if (selecting) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = if (selected.size < 2) {
                                stringResource(R.string.albums_select_hint)
                            } else {
                                pluralStringResource(R.plurals.count_selected, selected.size, formatCount(selected.size))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        PillButton(
                            text = stringResource(R.string.albums_merge),
                            onClick = { picking = true },
                            enabled = selected.size >= 2 && !moving,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val list = albums
        Column(Modifier.padding(padding)) {
            if (moving) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            when {
                list == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                list.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Collections,
                    iconColor = MaterialTheme.palette.blue,
                    title = stringResource(R.string.albums_empty_title),
                    body = stringResource(R.string.albums_empty_body),
                )
                else -> {
                    val gridState = rememberLazyGridState()
                    // A new order starts at the top, so the first year heading isn't left scrolled away.
                    var shownSort by rememberSaveable { mutableStateOf(sort) }
                    LaunchedEffect(sort) {
                        if (sort != shownSort) {
                            shownSort = sort
                            gridState.scrollToItem(0)
                        }
                    }
                    // By year: albums under the year of their newest item; otherwise one plain grid.
                    val groups = if (sort == AlbumSort.YEAR) list.groupBy { yearOf(it.newest) }.toList() else listOf(null to list)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .dragToSelect(gridState, list.map { it.relativePath }, selected) {
                                selected = it
                                if (it.isNotEmpty()) selecting = true
                            },
                    ) {
                        groups.forEach { (year, albumsOfYear) ->
                            if (year != null) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "year-$year") {
                                    Text(
                                        text = formatYear(year),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                    )
                                }
                            }
                            items(albumsOfYear, key = { it.relativePath }) { album ->
                                AlbumCell(
                                    album = album,
                                    selected = if (selecting) album.relativePath in selected else null,
                                    onClick = {
                                        if (selecting) {
                                            selected = if (album.relativePath in selected) selected - album.relativePath else selected + album.relativePath
                                        } else {
                                            onOpenAlbum(album.relativePath)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        val chosen = albums.orEmpty().filter { it.relativePath in selected }
        AlbumPickerSheet(
            title = stringResource(R.string.merge_title),
            body = stringResource(R.string.merge_body),
            albums = chosen,
            onPick = { target ->
                picking = false
                viewModel.merge(selected, target.relativePath)
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
                viewModel.merge(selected, AlbumPaths.newAlbum(name))
                stopSelecting()
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun AlbumCell(album: Album, selected: Boolean?, onClick: () -> Unit) {
    val clickModifier = if (selected != null) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(clickModifier) {
        Box {
            AlbumThumb(album, Modifier.fillMaxWidth())
            if (selected != null) {
                SelectionMark(selected, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = formatCount(album.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AlbumSort.labelRes: Int
    get() = when (this) {
        AlbumSort.MOST -> R.string.albums_sort_most
        AlbumSort.LARGEST -> R.string.albums_sort_largest
        AlbumSort.NAME -> R.string.albums_sort_name
        AlbumSort.RECENT -> R.string.albums_sort_recent
        AlbumSort.YEAR -> R.string.albums_sort_year
    }
