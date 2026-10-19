package com.sortr.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.faces.ScanStatus
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
import com.sortr.app.ui.library.color
import com.sortr.app.ui.library.icon
import com.sortr.app.ui.library.labelRes
import com.sortr.app.ui.people.PersonCell
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.vault.RemoveOriginalsEffects
import com.sortr.app.ui.vault.RemoveOriginalsViewModel
import com.sortr.app.ui.whatsapp.WhatsAppAccessViewModel
import com.sortr.app.ui.whatsapp.rememberFolderPicker
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.MediaSource
import com.sortr.domain.model.SessionFilter
import com.sortr.domain.model.SourceCount
import kotlin.math.roundToInt

/** Where Home can take the user. */
class HomeActions(
    val startSession: (filterKey: String) -> Unit,
    val openPeople: () -> Unit,
    val openPerson: (Long) -> Unit,
    val openUnsorted: () -> Unit,
    val openTrash: () -> Unit,
    val openVault: () -> Unit,
    val openSettings: () -> Unit,
    val openDates: () -> Unit,
    val openAlbums: () -> Unit,
    val openDuplicates: () -> Unit,
    val openStats: () -> Unit,
    val openCustom: () -> Unit,
    val openWhatsApp: () -> Unit,
)

/**
 * Home, kept calm: one card with what's left and a Start button, a short to-do list only when
 * something needs doing, then compact rows of ways in: Browse, People, Apps, Library.
 */
@Composable
fun HomeScreen(
    access: MediaAccess,
    videoAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    actions: HomeActions,
    viewModel: HomeViewModel = hiltViewModel(),
    originalsViewModel: RemoveOriginalsViewModel = hiltViewModel(),
    whatsAppViewModel: WhatsAppAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val originals by originalsViewModel.state.collectAsStateWithLifecycle()
    val whatsApp by whatsAppViewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val snackbarHostState = remember { SnackbarHostState() }
    val pickWhatsAppFolder = rememberFolderPicker(whatsAppViewModel, snackbarHostState)
    var showAccessHelp by rememberSaveable { mutableStateOf(false) }
    RemoveOriginalsEffects(originalsViewModel, snackbarHostState)

    // Ask once to show the scanning notification, when there's a scan to show.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanPending = !state.browse.analysis.isComplete ||
        (state.faceScan.modelState == FaceModelState.READY && !state.faceScan.progress.isComplete)
    LaunchedEffect(state.loaded, scanPending, state.notificationAsked) {
        if (state.loaded && scanPending && !state.notificationAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.onNotificationPermissionAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Everything that wants the user's attention, as short rows in one place.
    val whatsAppApp = whatsApp.missing.firstOrNull()?.takeIf { !whatsApp.promptDismissed }
    val todo = buildList<@Composable () -> Unit> {
        if (access == MediaAccess.PARTIAL) {
            add {
                ListRow(
                    title = stringResource(R.string.partial_access_title),
                    icon = Icons.Rounded.PhotoLibrary,
                    iconColor = MaterialTheme.palette.blue,
                    onClick = { showAccessHelp = true },
                )
            }
        }
        if (access == MediaAccess.FULL && !videoAccess) {
            add {
                ListRow(
                    title = stringResource(R.string.video_access_title),
                    icon = Icons.Rounded.Videocam,
                    iconColor = MaterialTheme.palette.pink,
                    onClick = onRequestAccess,
                )
            }
        }
        if (originals.pending > 0 || originals.encrypting > 0) {
            add {
                ListRow(
                    title = if (originals.encrypting > 0) {
                        pluralStringResource(R.plurals.vault_encrypting, originals.encrypting, formatCount(originals.encrypting))
                    } else {
                        pluralStringResource(R.plurals.vault_pending_title, originals.pending, formatCount(originals.pending))
                    },
                    subtitle = if (originals.encrypting == 0) stringResource(R.string.vault_pending_action) else null,
                    icon = Icons.Rounded.Lock,
                    iconColor = MaterialTheme.palette.indigo,
                    // Wait for encryption to finish so every original goes in one confirmation.
                    onClick = if (originals.encrypting == 0 && !originals.busy) originalsViewModel::start else null,
                    trailing = if (originals.encrypting > 0) {
                        { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
        if (whatsAppApp != null) {
            add {
                ListRow(
                    title = stringResource(R.string.whatsapp_prompt_title),
                    subtitle = stringResource(R.string.whatsapp_prompt_hint),
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    iconColor = MaterialTheme.palette.green,
                    onClick = { pickWhatsAppFolder(whatsAppApp) },
                    showChevron = false,
                    trailing = {
                        IconButton(onClick = whatsAppViewModel::dismissPrompt) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.action_not_now), tint = MaterialTheme.colorScheme.outline)
                        }
                    },
                )
            }
        }
    }

    LargeTitleScaffold(
        title = stringResource(R.string.app_name),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = actions.openSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings), tint = MaterialTheme.colorScheme.primary)
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                summary.total == 0 && (state.isSyncing || !state.loaded) -> item { LoadingGalleryCard() }
                summary.total == 0 -> item { NoPhotosCard() }
                else -> item { HeroCard(state, onStart = { actions.startSession(SessionFilter.AllPhotos.key) }) }
            }
            if (todo.isNotEmpty()) {
                item {
                    GroupedSection {
                        todo.forEach { content -> row { content() } }
                    }
                }
            }
            if (summary.total > 0) {
                browseSection(state, actions)
                peopleSection(state, actions)
                appsSection(state, actions)
            }
            librarySection(state, actions)
        }
    }

    if (showAccessHelp) {
        AlertDialog(
            onDismissRequest = { showAccessHelp = false },
            title = { Text(stringResource(R.string.partial_access_dialog_title)) },
            text = { Text(stringResource(R.string.partial_access_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessHelp = false
                        onOpenAppSettings()
                    },
                ) { Text(stringResource(R.string.permission_open_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAccessHelp = false
                        onRequestAccess()
                    },
                ) { Text(stringResource(R.string.partial_access_select_more)) }
            },
        )
    }
}

/** What's left, how far along, Start, and a quiet line while photos are being checked. */
@Composable
private fun HeroCard(state: HomeUiState, onStart: () -> Unit) {
    val summary = state.summary
    val sortedPercent = formatCount((summary.sortedFraction * 100).roundToInt())
    GroupedCard {
        Column(Modifier.padding(20.dp)) {
            if (summary.toReview == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Check, MaterialTheme.palette.green, size = 44.dp, shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.home_hero_done_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.home_hero_done_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Never wrapped: some digit fonts (e.g. Arabic-Indic) would otherwise break the number.
                Text(formatCount(summary.toReview), style = MaterialTheme.typography.displaySmall, maxLines = 1, softWrap = false)
                Text(
                    text = pluralStringResource(R.plurals.home_hero_caption, summary.toReview),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { summary.sortedFraction },
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    trackColor = MaterialTheme.palette.fill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.stats_sorted_percent, sortedPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                PillButton(text = stringResource(R.string.home_hero_start), onClick = onStart, icon = Icons.Rounded.Style)
            }
            ScanLine(state)
        }
    }
}

/** One quiet line under the button while photos are checked or faces found. */
@Composable
private fun ScanLine(state: HomeUiState) {
    val analysis = state.browse.analysis
    val scan = state.faceScan
    val (text, progress) = when {
        !analysis.isComplete -> stringResource(
            R.string.home_analysis_progress,
            formatCount(analysis.analysed),
            formatCount(analysis.total),
        ) to (if (analysis.total == 0) 0f else analysis.analysed.toFloat() / analysis.total)
        scan.modelState != FaceModelState.READY || scan.progress.isComplete -> return
        scan.status == ScanStatus.WAITING && scan.onlyWhileCharging -> stringResource(R.string.face_scan_waiting_charger) to null
        else -> stringResource(R.string.face_scan_progress, formatCount(scan.progress.indexed), formatCount(scan.progress.total)) to
            (if (scan.progress.total == 0) 0f else scan.progress.indexed.toFloat() / scan.progress.total)
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.palette.fill,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.browseSection(state: HomeUiState, actions: HomeActions) {
    item { SectionTitle(stringResource(R.string.home_section_browse)) }
    item {
        val counts = state.browse.counts
        val palette = MaterialTheme.palette
        TileGrid(
            columns = 3,
            tiles = listOf<@Composable (Modifier) -> Unit>(
                { m -> SmartTile(stringResource(R.string.tile_by_date), Icons.Rounded.CalendarMonth, palette.red, actions.openDates, m, formatCount(counts.years)) },
                { m -> SmartTile(stringResource(R.string.tile_albums), Icons.Rounded.Collections, palette.blue, actions.openAlbums, m, formatCount(counts.albums)) },
                { m ->
                    SmartTile(stringResource(R.string.tile_similar), Icons.Rounded.FilterNone, palette.indigo, actions.openDuplicates, m, state.browse.similarToReview?.let { formatCount(it) })
                },
                { m ->
                    SmartTile(
                        stringResource(R.string.tile_blurry), Icons.Rounded.BlurOn, palette.teal,
                        { actions.startSession(SessionFilter.Blurry.key) }, m, formatCount(counts.blurryToReview),
                    )
                },
                { m ->
                    SmartTile(
                        stringResource(R.string.tile_videos), Icons.Rounded.PlayCircle, palette.pink,
                        { actions.startSession(SessionFilter.Videos.key) }, m, formatCount(counts.videosToReview),
                    )
                },
                { m ->
                    SmartTile(
                        stringResource(R.string.tile_large), Icons.Rounded.SdStorage, palette.orange,
                        { actions.startSession(SessionFilter.Largest.key) }, m, formatCount(counts.largeToReview),
                    )
                },
            ),
        )
    }
    item {
        GroupedSection {
            row {
                ListRow(
                    title = stringResource(R.string.home_custom_session),
                    subtitle = stringResource(R.string.home_custom_session_body),
                    icon = Icons.Rounded.Tune,
                    iconColor = MaterialTheme.palette.gray,
                    onClick = actions.openCustom,
                )
            }
        }
    }
}

private fun LazyListScope.peopleSection(state: HomeUiState, actions: HomeActions) {
    if (state.people.isEmpty() && state.unsortedFaces == 0) return
    item {
        SectionTitle(
            text = stringResource(R.string.people_title),
            actionLabel = stringResource(R.string.home_people_see_all),
            onAction = actions.openPeople,
        )
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.people, key = { it.id }) { person ->
                PersonCell(person = person, avatarSize = 64.dp, onClick = { actions.openPerson(person.id) }, modifier = Modifier.width(84.dp))
            }
            if (state.unsortedFaces > 0) {
                item(key = "unsorted") {
                    RoundShortcut(
                        icon = Icons.Rounded.FaceRetouchingNatural,
                        color = MaterialTheme.palette.purple,
                        label = stringResource(R.string.unsorted_faces_short),
                        value = formatCount(state.unsortedFaces),
                        onClick = actions.openUnsorted,
                    )
                }
            }
        }
    }
}

/** Apps the photos came from, as a row of round icons. WhatsApp opens its own page. */
private fun LazyListScope.appsSection(state: HomeUiState, actions: HomeActions) {
    val sources = state.browse.sources.filter { it.source.partOf == null }
    if (sources.isEmpty()) return
    item { SectionTitle(stringResource(R.string.home_section_sources)) }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(sources, key = { it.source.name }) { source -> AppShortcut(source, actions) }
        }
    }
}

@Composable
private fun AppShortcut(source: SourceCount, actions: HomeActions) {
    RoundShortcut(
        icon = source.source.icon,
        color = source.source.color,
        label = stringResource(source.source.labelRes),
        value = if (source.toReview > 0) formatCount(source.toReview) else null,
        done = source.toReview == 0,
        onClick = {
            if (source.source == MediaSource.WHATSAPP) {
                actions.openWhatsApp()
            } else {
                actions.startSession(SessionFilter.Source(source.source).key)
            }
        },
    )
}

/** A round icon with a name and a small count underneath, for horizontal rows. */
@Composable
private fun RoundShortcut(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String?,
    onClick: () -> Unit,
    done: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        IconBadge(icon, color, size = 56.dp, shape = CircleShape)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.palette.green, modifier = Modifier.size(14.dp))
        } else if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Trash, vault and stats as three small tiles side by side. */
private fun LazyListScope.librarySection(state: HomeUiState, actions: HomeActions) {
    item { SectionTitle(stringResource(R.string.home_section_library)) }
    item {
        val context = LocalContext.current
        val summary = state.summary
        val palette = MaterialTheme.palette
        TileGrid(
            columns = 3,
            tiles = listOf<@Composable (Modifier) -> Unit>(
                { m ->
                    SmartTile(
                        title = stringResource(R.string.home_trash),
                        icon = Icons.Rounded.Delete,
                        color = palette.red,
                        onClick = actions.openTrash,
                        modifier = m,
                        caption = if (summary.trashedCount > 0) formatBytes(context, summary.trashedBytes) else stringResource(R.string.home_trash_empty),
                    )
                },
                { m ->
                    SmartTile(
                        title = stringResource(R.string.vault_title_short),
                        icon = Icons.Rounded.Lock,
                        color = palette.indigo,
                        onClick = actions.openVault,
                        modifier = m,
                        caption = stringResource(if (state.vaultSetUp) R.string.vault_tile_locked else R.string.vault_tile_set_up),
                    )
                },
                { m ->
                    SmartTile(
                        title = stringResource(R.string.home_stats),
                        icon = Icons.Rounded.BarChart,
                        color = palette.green,
                        onClick = actions.openStats,
                        modifier = m,
                        caption = stringResource(R.string.stats_sorted_percent, formatCount((summary.sortedFraction * 100).roundToInt())),
                    )
                },
            ),
        )
    }
}

@Composable
private fun LoadingGalleryCard() {
    GroupedCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.home_loading_gallery), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NoPhotosCard() {
    GroupedCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.PhotoLibrary, MaterialTheme.palette.blue, size = 44.dp, shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.home_no_photos_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_no_photos_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
