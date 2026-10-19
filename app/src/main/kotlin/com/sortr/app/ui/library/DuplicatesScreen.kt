package com.sortr.app.ui.library

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.sortr.app.R
import com.sortr.app.image.MediaThumbnail
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.AnalysisProgress
import com.sortr.domain.model.Decision
import com.sortr.domain.model.DuplicateGroup
import com.sortr.domain.model.Photo
import com.sortr.domain.model.PhotoStatus
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SwipeAction
import com.sortr.domain.repository.LibraryRepository
import com.sortr.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** A group as shown: [keeperId] is the best copy unless the user picked another one. */
data class GroupUi(val key: Long, val group: DuplicateGroup, val keeperId: Long) {
    val extras: List<Photo> get() = group.photos.filter { it.id != keeperId }
}

data class DuplicatesUiState(
    /** Null while loading. */
    val groups: List<GroupUi>? = null,
    val analysis: AnalysisProgress = AnalysisProgress(0, 0),
) {
    val extraCount: Int get() = groups.orEmpty().sumOf { it.extras.size }
    val reclaimableBytes: Long get() = groups.orEmpty().sumOf { g -> g.extras.sumOf { it.sizeBytes } }
}

sealed interface DuplicatesMessage {
    data class Trashed(val count: Int) : DuplicatesMessage
    data object Failed : DuplicatesMessage
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    library: LibraryRepository,
    private val photos: PhotoRepository,
) : ViewModel() {

    private val keepers = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val mutex = Mutex()
    private var lastDecisions: List<Decision> = emptyList()

    private val groups = combine(photos.observeSummary(), library.observeAnalysisProgress(), ::Pair)
        .debounce(REFRESH_DEBOUNCE_MS)
        .mapLatest { library.duplicateGroups() }

    val state: StateFlow<DuplicatesUiState> = combine(groups, keepers, library.observeAnalysisProgress()) { groups, keepers, analysis ->
        DuplicatesUiState(
            groups = groups.map { group ->
                // Keyed by the lowest id so a choice survives the list being recomputed.
                val key = group.photos.minOf { it.id }
                val chosen = keepers[key]?.takeIf { id -> group.photos.any { it.id == id } }
                GroupUi(key, group, chosen ?: group.bestId)
            },
            analysis = analysis,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState())

    private val _messages = Channel<DuplicatesMessage>(Channel.BUFFERED)
    val messages: Flow<DuplicatesMessage> = _messages.receiveAsFlow()

    fun choose(group: GroupUi, photoId: Long) {
        keepers.value = keepers.value + (group.key to photoId)
    }

    /** Keeps the chosen copy and moves the rest of the group to the Trash. */
    fun keepBest(group: GroupUi) = cleanUp(listOf(group))

    fun cleanUpAll() = cleanUp(state.value.groups.orEmpty())

    /** The user says these aren't duplicates: keeping every copy hides the group. */
    fun keepAll(group: GroupUi) {
        decide { photos.applyDecisions(group.group.photos.filter { it.status != PhotoStatus.KEPT }, SwipeAction.KEEP) }
    }

    fun undo() {
        viewModelScope.launch {
            mutex.withLock {
                val decisions = lastDecisions
                lastDecisions = emptyList()
                runCatching { photos.undoDecisions(decisions) }.onFailure { Log.w(TAG, "Undo failed", it) }
            }
        }
    }

    private fun cleanUp(groups: List<GroupUi>) {
        if (groups.isEmpty()) return
        decide {
            val keep = groups.mapNotNull { g -> g.group.photos.firstOrNull { it.id == g.keeperId && it.status != PhotoStatus.KEPT } }
            val trash = groups.flatMap { it.extras }
            val decisions = photos.applyDecisions(keep, SwipeAction.KEEP) + photos.applyDecisions(trash, SwipeAction.DELETE)
            _messages.send(DuplicatesMessage.Trashed(trash.size))
            decisions
        }
    }

    private fun decide(block: suspend () -> List<Decision>) {
        viewModelScope.launch {
            mutex.withLock {
                lastDecisions = try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Saving decisions failed", e)
                    _messages.send(DuplicatesMessage.Failed)
                    emptyList()
                }
            }
        }
    }

    private companion object {
        const val TAG = "Duplicates"
        const val REFRESH_DEBOUNCE_MS = 400L
    }
}

@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is DuplicatesMessage.Trashed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.dup_trashed, message.count, formatCount(message.count)),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
                DuplicatesMessage.Failed -> snackbarHostState.showSnackbar(context.getString(R.string.swipe_error_save))
            }
        }
    }

    LargeTitleScaffold(
        title = stringResource(R.string.dup_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val groups = state.groups
        when {
            groups == null -> CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            groups.isEmpty() -> EmptyState(
                icon = Icons.Rounded.FilterNone,
                iconColor = MaterialTheme.palette.indigo,
                title = stringResource(R.string.dup_empty_title),
                body = if (state.analysis.isComplete) {
                    stringResource(R.string.dup_empty_body)
                } else {
                    stringResource(R.string.dup_still_checking, formatCount(state.analysis.analysed), formatCount(state.analysis.total))
                },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                item {
                    SummaryCard(
                        state = state,
                        onCleanAll = { confirmAll = true },
                        onReview = { onStartSession(SessionFilter.Duplicates.key) },
                    )
                }
                items(groups, key = { it.key }) { group ->
                    GroupCard(
                        group = group,
                        onChoose = { viewModel.choose(group, it.id) },
                        onOpen = { viewerPhoto = it },
                        onKeepBest = { viewModel.keepBest(group) },
                        onKeepAll = { viewModel.keepAll(group) },
                    )
                }
            }
        }
    }

    viewerPhoto?.let { FullScreenViewer(photo = it, onDismiss = { viewerPhoto = null }) }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.dup_clean_all_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.dup_clean_all_confirm_body, state.extraCount, formatCount(state.extraCount))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAll = false
                        viewModel.cleanUpAll()
                    },
                ) { Text(stringResource(R.string.dup_clean_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: DuplicatesUiState, onCleanAll: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    val groups = state.groups.orEmpty()
    GroupedCard {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = pluralStringResource(R.plurals.dup_summary, groups.size, formatCount(groups.size), formatBytes(context, state.reclaimableBytes)),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.analysis.isComplete) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dup_still_checking, formatCount(state.analysis.analysed), formatCount(state.analysis.total)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            PillButton(text = stringResource(R.string.dup_clean_all), onClick = onCleanAll)
            Spacer(Modifier.height(8.dp))
            TintedButton(
                text = stringResource(R.string.dup_review_one_by_one),
                onClick = onReview,
                icon = Icons.Rounded.Style,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupUi,
    onChoose: (Photo) -> Unit,
    onOpen: (Photo) -> Unit,
    onKeepBest: () -> Unit,
    onKeepAll: () -> Unit,
) {
    GroupedCard {
        LazyRow(
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(group.group.photos, key = { it.id }) { photo ->
                CopyTile(
                    photo = photo,
                    isKeeper = photo.id == group.keeperId,
                    onClick = { onChoose(photo) },
                    onLongClick = { onOpen(photo) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, bottom = 12.dp),
        ) {
            TextButton(onClick = onKeepAll) { Text(stringResource(R.string.dup_not_duplicates)) }
            Spacer(Modifier.weight(1f))
            TintedButton(
                text = pluralStringResource(R.plurals.dup_keep_best, group.extras.size, formatCount(group.extras.size)),
                onClick = onKeepBest,
                color = MaterialTheme.palette.red,
                compact = true,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyTile(photo: Photo, isKeeper: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val size = formatBytes(context, photo.sizeBytes)
    val description = stringResource(
        if (isKeeper) R.string.dup_photo_best_description else R.string.dup_photo_description,
        formatDate(photo.dateTaken),
        size,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(116.dp)
                .clip(shape)
                .then(if (isKeeper) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { selected = isKeeper }
                .combinedClickable(role = Role.RadioButton, onLongClick = onLongClick, onClick = onClick),
        ) {
            AsyncImage(
                model = MediaThumbnail(photo.uri.toUri()),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isKeeper) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.dup_best), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
