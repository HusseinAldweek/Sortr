package com.sortr.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.faces.ScanStatus
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.people.PersonCell
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.theme.SwipeColors
import com.sortr.app.ui.vault.RemoveOriginalsCard
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.Person
import com.sortr.domain.model.SessionFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    access: MediaAccess,
    onRequestAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartSession: (filterKey: String) -> Unit,
    onOpenPeople: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenUnsorted: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Ask once to show the scanning notification, when there's a scan to show.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanPending = state.faceScan.modelState == FaceModelState.READY && !state.faceScan.progress.isComplete
    LaunchedEffect(state.loaded, scanPending, state.notificationAsked) {
        if (state.loaded && scanPending && !state.notificationAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.onNotificationPermissionAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.isSyncing && summary.total > 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (access == MediaAccess.PARTIAL) {
                    item {
                        PartialAccessBanner(onSelectMore = onRequestAccess, onAllowAll = onOpenAppSettings)
                    }
                }
                item { RemoveOriginalsCard(snackbarHostState = snackbarHostState) }
                if (summary.total > 0) {
                    item { FaceScanCard(state.faceScan) }
                }

                when {
                    summary.total == 0 && (state.isSyncing || !state.loaded) -> item { LoadingGalleryCard() }
                    summary.total == 0 -> item { NoPhotosCard() }
                    else -> {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.home_summary,
                                    pluralStringResource(R.plurals.home_photo_count, summary.total, formatCount(summary.total)),
                                    pluralStringResource(R.plurals.home_left_to_review, summary.toReview, formatCount(summary.toReview)),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.people.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SectionHeader(stringResource(R.string.people_title), Modifier.weight(1f))
                                    TextButton(onClick = onOpenPeople, modifier = Modifier.padding(top = 8.dp)) {
                                        Text(stringResource(R.string.home_people_see_all))
                                    }
                                }
                            }
                            item { PeopleRow(state.people, onOpenPerson) }
                        }
                        item { SectionHeader(stringResource(R.string.home_sessions_header)) }
                        item {
                            NavigationCard(
                                icon = Icons.Rounded.PhotoLibrary,
                                iconTint = MaterialTheme.colorScheme.primary,
                                title = stringResource(R.string.session_all_photos),
                                subtitle = if (summary.toReview > 0) {
                                    pluralStringResource(R.plurals.session_to_review, summary.toReview, formatCount(summary.toReview))
                                } else {
                                    stringResource(R.string.session_all_caught_up)
                                },
                                onClick = { onStartSession(SessionFilter.AllPhotos.key) },
                            )
                        }
                        if (state.unsortedFaces > 0) {
                            item {
                                NavigationCard(
                                    icon = Icons.Rounded.FaceRetouchingNatural,
                                    iconTint = MaterialTheme.colorScheme.tertiary,
                                    title = stringResource(R.string.unsorted_faces_title),
                                    subtitle = pluralStringResource(R.plurals.face_count, state.unsortedFaces, formatCount(state.unsortedFaces)),
                                    onClick = onOpenUnsorted,
                                )
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.home_shortcuts_header)) }
                item {
                    NavigationCard(
                        icon = Icons.Rounded.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.home_trash),
                        subtitle = if (summary.trashedCount > 0) {
                            pluralStringResource(
                                R.plurals.home_trash_summary,
                                summary.trashedCount,
                                formatCount(summary.trashedCount),
                                formatBytes(context, summary.trashedBytes),
                            )
                        } else {
                            stringResource(R.string.home_trash_empty)
                        },
                        onClick = onOpenTrash,
                    )
                }
                item {
                    NavigationCard(
                        icon = Icons.Rounded.Lock,
                        iconTint = SwipeColors.Private,
                        title = stringResource(R.string.vault_title),
                        subtitle = stringResource(
                            if (state.vaultSetUp) R.string.home_vault_locked else R.string.home_vault_not_set_up,
                        ),
                        onClick = onOpenVault,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PeopleRow(people: List<Person>, onOpenPerson: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(people, key = { it.id }) { person ->
            PersonCell(
                person = person,
                avatarSize = 64.dp,
                onClick = { onOpenPerson(person.id) },
                modifier = Modifier.width(84.dp),
            )
        }
    }
}

/** Face scanning status. Shows nothing once every photo has been scanned. */
@Composable
private fun FaceScanCard(scan: FaceScanUiState) {
    val message = when {
        scan.modelState == FaceModelState.MISSING -> stringResource(R.string.face_model_missing)
        scan.modelState == FaceModelState.UNSUPPORTED -> stringResource(R.string.face_model_unsupported)
        scan.progress.isComplete -> return
        scan.status == ScanStatus.WAITING && scan.onlyWhileCharging -> stringResource(R.string.face_scan_waiting_charger)
        else -> null
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message ?: stringResource(R.string.face_scan_progress, formatCount(scan.progress.indexed), formatCount(scan.progress.total)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (scan.modelState == FaceModelState.READY) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (scan.progress.total == 0) 0f else scan.progress.indexed.toFloat() / scan.progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NavigationCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingGalleryCard() {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.home_loading_gallery), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NoPhotosCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.home_no_photos_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_no_photos_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PartialAccessBanner(onSelectMore: () -> Unit, onAllowAll: () -> Unit) {
    var showAllowAllHelp by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.partial_access_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.partial_access_body), style = MaterialTheme.typography.bodyMedium)
            // Buttons take the banner's text colour; the default primary can clash with the container.
            val buttonColors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSelectMore, colors = buttonColors) {
                    Text(stringResource(R.string.partial_access_select_more), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showAllowAllHelp = true }, colors = buttonColors) {
                    Text(stringResource(R.string.partial_access_allow_all), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showAllowAllHelp) {
        AlertDialog(
            onDismissRequest = { showAllowAllHelp = false },
            title = { Text(stringResource(R.string.partial_access_dialog_title)) },
            text = { Text(stringResource(R.string.partial_access_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAllowAllHelp = false
                        onAllowAll()
                    },
                ) { Text(stringResource(R.string.permission_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showAllowAllHelp = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
