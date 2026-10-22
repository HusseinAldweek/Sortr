package com.sortr.app.ui.recent

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.design.BarTextButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.MediaTile
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SegmentedControl
import com.sortr.app.ui.design.SelectionToolbar
import com.sortr.app.ui.design.ToolbarItem
import com.sortr.app.ui.design.dragToSelect
import com.sortr.app.ui.swipe.color
import com.sortr.app.ui.swipe.icon
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Photo
import com.sortr.domain.model.RecentDecision
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.PhotoRepository
import com.sortr.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Which decisions the list shows. Private ones show under All. */
enum class RecentFilter(val action: SwipeAction?) { ALL(null), KEPT(SwipeAction.KEEP), DELETED(SwipeAction.DELETE), SKIPPED(SwipeAction.SKIP) }

data class RecentUiState(
    /** Null while loading. */
    val items: List<RecentDecision>? = null,
    val filter: RecentFilter = RecentFilter.ALL,
    /** Action ids. */
    val selected: Set<Long> = emptySet(),
)

sealed interface RecentEvent {
    /** [blocked] vault photos couldn't be taken back because their originals are already gone. */
    data class Undone(val count: Int, val blocked: Int) : RecentEvent
}

@HiltViewModel
class RecentlySortedViewModel @Inject constructor(
    private val photos: PhotoRepository,
    private val vault: VaultRepository,
    private val gallerySync: GallerySync,
) : ViewModel() {

    private val filter = MutableStateFlow(RecentFilter.ALL)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val since = System.currentTimeMillis() - WINDOW_DAYS * 24 * 60 * 60 * 1000L

    val state: StateFlow<RecentUiState> = combine(photos.observeRecentDecisions(since, LIMIT), filter, selected) { all, filter, selection ->
        val shown = if (filter.action == null) all else all.filter { it.decision.action == filter.action }
        val ids = shown.mapTo(HashSet()) { it.decision.actionId }
        RecentUiState(items = shown, filter = filter, selected = selection intersect ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentUiState())

    private val _events = Channel<RecentEvent>(Channel.BUFFERED)
    val events: Flow<RecentEvent> = _events.receiveAsFlow()

    fun setFilter(value: RecentFilter) {
        filter.value = value
        selected.value = emptySet()
    }

    fun toggle(actionId: Long) {
        selected.value = selected.value.let { if (actionId in it) it - actionId else it + actionId }
    }

    fun setSelection(actionIds: Set<Long>) {
        selected.value = actionIds
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun selectedPhotos(): List<Photo> = chosen().map { it.decision.photo }

    /** Puts the selected photos back the way they were before the decision. */
    fun undoSelected() {
        val chosen = chosen()
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            try {
                val (vaulted, others) = chosen.partition { it.decision.action == SwipeAction.PRIVATE }
                // A vault photo can only come back while its original is still in the gallery.
                val inGallery = if (vaulted.isEmpty()) emptySet() else gallerySync.existingIds(vaulted.map { it.decision.photo.id })
                val undoable = others + vaulted.filter { it.decision.photo.id in inGallery }
                photos.undoDecisions(undoable.map { it.decision })
                undoable.filter { it.decision.action == SwipeAction.PRIVATE }.forEach { vault.cancel(it.decision.photo.id) }
                selected.value = emptySet()
                _events.send(RecentEvent.Undone(undoable.size, blocked = vaulted.size - inGallery.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Undo failed", e)
            }
        }
    }

    private fun chosen(): List<RecentDecision> = state.value.items.orEmpty().filter { it.decision.actionId in state.value.selected }

    private companion object {
        const val TAG = "RecentlySorted"
        const val WINDOW_DAYS = 30
        const val LIMIT = 2_000
    }
}

/** Decisions from the last 30 days, grouped by day, which can be undone in any order. */
@Composable
fun RecentlySortedScreen(onBack: () -> Unit, viewModel: RecentlySortedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val selecting = state.selected.isNotEmpty()
    var viewing by remember { mutableStateOf<List<Photo>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecentEvent.Undone -> {
                    val resources = context.resources
                    val text = buildString {
                        append(resources.getQuantityString(R.plurals.recent_undone, event.count, formatCount(event.count)))
                        if (event.blocked > 0) {
                            append(' ')
                            append(resources.getQuantityString(R.plurals.recent_undo_blocked, event.blocked, formatCount(event.blocked)))
                        }
                    }
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LargeTitleScaffold(
        title = stringResource(R.string.recent_title),
        onBack = if (selecting) null else onBack,
        actions = {
            if (selecting) BarTextButton(text = stringResource(R.string.action_cancel), onClick = viewModel::clearSelection)
        },
        bottomBar = {
            if (selecting) {
                SelectionToolbar(
                    count = state.selected.size,
                    items = listOf(
                        ToolbarItem(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.action_undo), viewModel::undoSelected),
                        ToolbarItem(Icons.Rounded.Visibility, stringResource(R.string.action_view), { viewing = viewModel.selectedPhotos() }),
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val items = state.items
        Column(Modifier.padding(padding)) {
            SegmentedControl(
                options = RecentFilter.entries,
                selected = state.filter,
                label = { stringResource(it.labelRes) },
                onSelect = viewModel::setFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            when {
                items == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.History,
                    iconColor = MaterialTheme.palette.blue,
                    title = stringResource(R.string.recent_empty_title),
                    body = stringResource(R.string.recent_empty_body),
                )
                else -> {
                    val days = items.groupBy { dayOf(it.decidedAt) }.toList()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .dragToSelect(gridState, items.map { it.decision.actionId }, state.selected, viewModel::setSelection),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
                            Text(
                                text = stringResource(R.string.recent_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                            )
                        }
                        days.forEach { (day, decisions) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "day-$day") {
                                Text(
                                    text = dayLabel(day),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                                )
                            }
                            items(decisions, key = { it.decision.actionId }) { recent ->
                                DecisionTile(
                                    recent = recent,
                                    selected = if (selecting) recent.decision.actionId in state.selected else null,
                                    onClick = { viewModel.toggle(recent.decision.actionId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { photos -> FullScreenViewer(photos, 0, onDismiss = { viewing = null }) }
}

/** A photo with a small badge for what was decided. */
@Composable
private fun DecisionTile(recent: RecentDecision, selected: Boolean?, onClick: () -> Unit) {
    val action = recent.decision.action
    Box {
        MediaTile(
            photo = recent.decision.photo,
            selected = selected,
            onClick = onClick,
            contentDescription = stringResource(
                R.string.recent_photo_description,
                stringResource(action.pastLabelRes),
                formatDate(recent.decision.photo.dateTaken),
            ),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .size(24.dp)
                .background(action.color, CircleShape),
        ) {
            Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

private fun dayOf(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.recent_today)
        today.minusDays(1) -> stringResource(R.string.recent_yesterday)
        else -> formatDate(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
}

private val RecentFilter.labelRes: Int
    get() = when (this) {
        RecentFilter.ALL -> R.string.recent_filter_all
        RecentFilter.KEPT -> R.string.recent_filter_kept
        RecentFilter.DELETED -> R.string.recent_filter_deleted
        RecentFilter.SKIPPED -> R.string.recent_filter_skipped
    }

private val SwipeAction.pastLabelRes: Int
    get() = when (this) {
        SwipeAction.KEEP -> R.string.recent_kept
        SwipeAction.DELETE -> R.string.recent_deleted
        SwipeAction.PRIVATE -> R.string.recent_private
        SwipeAction.SKIP -> R.string.recent_skipped
    }
