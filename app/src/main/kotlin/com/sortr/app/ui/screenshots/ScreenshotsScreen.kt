package com.sortr.app.ui.screenshots

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.media.DeleteRequestFactory
import com.sortr.app.media.WriteRequestFactory
import com.sortr.app.ui.albums.MoveEffects
import com.sortr.app.ui.albums.MovingViewModel
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.SectionTitle
import com.sortr.app.ui.design.SmartTile
import com.sortr.app.ui.design.TileGrid
import com.sortr.app.ui.theme.palette
import com.sortr.data.media.MediaLocator
import com.sortr.domain.model.AlbumPaths
import com.sortr.domain.model.ScreenshotRules
import com.sortr.domain.model.ScreenshotSummary
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenshotsViewModel @Inject constructor(
    private val library: LibraryRepository,
    writeRequests: WriteRequestFactory,
    deleteRequests: DeleteRequestFactory,
    locator: MediaLocator,
) : MovingViewModel(library, writeRequests, deleteRequests, locator) {

    /** Null while loading. */
    val summary: StateFlow<ScreenshotSummary?> = library.observeScreenshots()
        .map<ScreenshotSummary, ScreenshotSummary?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves every screenshot that isn't in the screenshot album yet into it. */
    fun tidyUp() {
        val album = summary.value?.homeAlbum ?: return
        viewModelScope.launch { startMove(library.screenshotsOutside(album), album) }
    }
}

/** Screenshots' own page: what's left, older ones, and putting them all in one album. */
@Composable
fun ScreenshotsScreen(
    onBack: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    onOpenAlbum: (relativePath: String) -> Unit,
    viewModel: ScreenshotsViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val moving by viewModel.moving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmTidy by rememberSaveable { mutableStateOf(false) }

    MoveEffects(viewModel, snackbarHostState)

    LargeTitleScaffold(
        title = stringResource(R.string.screenshots_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val state = summary
        if (state == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@LargeTitleScaffold
        }
        val homeName = AlbumPaths.displayName(state.homeAlbum)
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item { ScreenshotsHero(state, onStart = { onStartSession(SessionFilter.Screenshots().key) }) }
            if (state.total == 0) return@LazyColumn
            item { SectionTitle(stringResource(R.string.home_section_browse)) }
            item {
                val palette = MaterialTheme.palette
                TileGrid(
                    listOf<@Composable (Modifier) -> Unit>(
                        { m ->
                            SmartTile(
                                title = stringResource(R.string.screenshots_tile_all),
                                icon = Icons.Rounded.PhotoLibrary,
                                color = palette.blue,
                                count = formatCount(state.toReview),
                                onClick = { onStartSession(SessionFilter.Screenshots().key) },
                                modifier = m,
                            )
                        },
                        { m ->
                            SmartTile(
                                title = stringResource(R.string.screenshots_tile_old, formatCount(ScreenshotRules.OLD_AFTER_DAYS)),
                                icon = Icons.Rounded.History,
                                color = palette.orange,
                                count = formatCount(state.oldToReview),
                                onClick = { onStartSession(SessionFilter.Screenshots(ScreenshotRules.OLD_AFTER_DAYS).key) },
                                modifier = m,
                            )
                        },
                    ),
                )
            }
            item { SectionTitle(stringResource(R.string.screenshots_section_tidy)) }
            item {
                GroupedSection {
                    row {
                        if (state.outsideHome > 0) {
                            ListRow(
                                title = stringResource(R.string.screenshots_tidy_title, homeName),
                                subtitle = pluralStringResource(R.plurals.screenshots_tidy_body, state.outsideHome, formatCount(state.outsideHome)),
                                icon = Icons.Rounded.Folder,
                                iconColor = MaterialTheme.palette.blue,
                                onClick = if (moving) null else ({ confirmTidy = true }),
                                trailing = if (moving) {
                                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 4.dp).size(18.dp)) }
                                } else {
                                    null
                                },
                            )
                        } else {
                            ListRow(
                                title = stringResource(R.string.screenshots_tidy_done_title),
                                subtitle = stringResource(R.string.screenshots_tidy_done_body, homeName),
                                icon = Icons.Rounded.Check,
                                iconColor = MaterialTheme.palette.green,
                                showChevron = false,
                            )
                        }
                    }
                }
            }
            val folders = state.folders.filter { it.relativePath.isNotEmpty() }
            if (folders.size > 1) {
                item { SectionTitle(stringResource(R.string.whatsapp_hub_folders)) }
                item {
                    GroupedSection {
                        folders.forEach { folder ->
                            row {
                                ListRow(
                                    title = AlbumPaths.displayName(folder.relativePath),
                                    // Albums can share a name, e.g. Pictures/Screenshots and DCIM/Screenshots.
                                    subtitle = folder.relativePath.trimEnd('/'),
                                    icon = Icons.Rounded.Folder,
                                    iconColor = MaterialTheme.palette.gray,
                                    value = formatCount(folder.count),
                                    onClick = { onOpenAlbum(folder.relativePath) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmTidy) {
        val count = summary?.outsideHome ?: 0
        val homeName = summary?.homeAlbum?.let(AlbumPaths::displayName).orEmpty()
        AlertDialog(
            onDismissRequest = { confirmTidy = false },
            title = { Text(pluralStringResource(R.plurals.screenshots_tidy_confirm_title, count, formatCount(count))) },
            text = { Text(stringResource(R.string.screenshots_tidy_confirm_body, homeName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTidy = false
                        viewModel.tidyUp()
                    },
                ) { Text(stringResource(R.string.screenshots_tidy_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTidy = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ScreenshotsHero(state: ScreenshotSummary, onStart: () -> Unit) {
    val context = LocalContext.current
    GroupedCard {
        Column(Modifier.padding(20.dp)) {
            if (state.total == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Screenshot, MaterialTheme.palette.blue, size = 44.dp, shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.screenshots_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.screenshots_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }
            // Never wrapped: some digit fonts (e.g. Arabic-Indic) would otherwise break the number.
            Text(formatCount(state.toReview), style = MaterialTheme.typography.displaySmall, maxLines = 1, softWrap = false)
            Text(
                text = pluralStringResource(R.plurals.screenshots_caption, state.toReview),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else 1f - state.toReview.toFloat() / state.total },
                trackColor = MaterialTheme.palette.fill,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.whatsapp_hub_of_total, formatCount(state.total), formatBytes(context, state.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.toReview > 0) {
                Spacer(Modifier.height(18.dp))
                PillButton(text = stringResource(R.string.home_hero_start), onClick = onStart, icon = Icons.Rounded.Style)
            }
        }
    }
}
