package com.sortr.app.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.sortr.app.R
import com.sortr.app.ui.common.formatBytes
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.viewer.ZoomableImage
import com.sortr.app.vault.VaultImage
import com.sortr.domain.model.VaultItem
import kotlinx.coroutines.launch

/** The vault: first-time setup, then the lock screen, then the photos. No screenshots anywhere. */
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onBackUp: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    SecureWindowEffect()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        !state.isSetUp -> VaultSetup(
            setup = state.setup,
            onBack = onBack,
            onStart = viewModel::startPinSetup,
            onPin = viewModel::onSetupPin,
        )
        !state.unlocked -> VaultLockScreen(onBack = onBack)
        else -> VaultContent(state = state, viewModel = viewModel, onBack = onBack, onBackUp = onBackUp)
    }
}

@Composable
private fun VaultSetup(
    setup: SetupState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onPin: (String) -> Unit,
) {
    LargeTitleScaffold(title = stringResource(R.string.vault_title), onBack = onBack) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            when (setup.step) {
                SetupStep.INTRO -> {
                    IconBadge(Icons.Rounded.Lock, MaterialTheme.palette.indigo, size = 72.dp, shape = CircleShape)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.vault_setup_body),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    GroupedCard {
                        Row(Modifier.padding(16.dp)) {
                            IconBadge(Icons.Rounded.Warning, MaterialTheme.palette.orange)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.vault_setup_warning_title), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.vault_setup_warning_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    PillButton(text = stringResource(R.string.vault_setup_continue), onClick = onStart)
                }
                SetupStep.CREATE, SetupStep.CONFIRM -> {
                    val creating = setup.step == SetupStep.CREATE
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(if (creating) R.string.vault_pin_create_title else R.string.vault_pin_confirm_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    val message = when (setup.error) {
                        SetupError.MISMATCH -> stringResource(R.string.vault_pin_mismatch)
                        SetupError.INVALID -> stringResource(R.string.vault_pin_invalid)
                        null -> if (creating) stringResource(R.string.vault_pin_create_body) else ""
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = if (setup.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    PinPad(enabled = true, resetKey = setup.attempt, onSubmit = onPin)
                }
            }
        }
    }
}

@Composable
private fun VaultContent(
    state: VaultUiState,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onBackUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var openItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openItem = state.items.firstOrNull { it.id == openItemId }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                VaultEvent.Restored -> R.string.vault_restored
                VaultEvent.RestoreFailed -> R.string.vault_restore_failed
                VaultEvent.Deleted -> R.string.vault_deleted
                VaultEvent.Failed -> R.string.vault_action_failed
            }
            if (event == VaultEvent.Restored || event == VaultEvent.Deleted) openItemId = null
            scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
        }
    }

    BackHandler(enabled = openItem != null) { openItemId = null }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = stringResource(R.string.vault_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::lock) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.vault_lock_now), tint = MaterialTheme.colorScheme.primary)
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 12.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.vault_item_count, state.items.size, formatCount(state.items.size)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        RemoveOriginalsCard(snackbarHostState = snackbarHostState)
                        if (state.showBackupReminder) {
                            BackupReminderCard(onBackUp = onBackUp, onDismiss = viewModel::dismissBackupReminder)
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyVault() }
                }
                items(state.items, key = { it.id }) { item ->
                    VaultTile(item, viewModel.imageLoader) { openItemId = item.id }
                }
            }
        }

        if (openItem != null) {
            VaultViewer(
                item = openItem,
                imageLoader = viewModel.imageLoader,
                onClose = { openItemId = null },
                onRestore = { viewModel.restore(openItem.id) },
                onDelete = { viewModel.delete(openItem.id) },
            )
        }
    }
}

@Composable
private fun VaultTile(item: VaultItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    AsyncImage(
        model = VaultImage(item.id, thumbnail = true),
        contentDescription = stringResource(R.string.vault_photo_description, formatDate(item.dateTaken)),
        imageLoader = imageLoader,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun BackupReminderCard(onBackUp: () -> Unit, onDismiss: () -> Unit) {
    GroupedCard {
        Row(Modifier.padding(16.dp)) {
            IconBadge(Icons.Rounded.Backup, MaterialTheme.palette.blue)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.vault_backup_reminder_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.vault_backup_reminder_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TintedButton(text = stringResource(R.string.vault_backup_now), onClick = onBackUp, compact = true)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_backup_later)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyVault() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
    ) {
        IconBadge(Icons.Rounded.Lock, MaterialTheme.palette.indigo, size = 72.dp, shape = CircleShape)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.vault_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full screen view of one vault photo, drawn in the vault's own (secure) window. */
@Composable
private fun VaultViewer(
    item: VaultItem,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val platformContext = LocalPlatformContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val request = remember(item.id) {
        val maxPx = with(density) {
            (maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() * 2).coerceAtMost(4096)
        }
        ImageRequest.Builder(platformContext)
            .data(VaultImage(item.id, thumbnail = false))
            .size(maxPx, maxPx)
            .build()
    }

    var dismissProgress by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - dismissProgress)),
    ) {
        ZoomableImage(
            model = request,
            contentDescription = stringResource(R.string.vault_photo_description, formatDate(item.dateTaken)),
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
            onDismiss = onClose,
            onDismissProgress = { dismissProgress = it },
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.viewer_close), tint = Color.White)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.swipe_card_details, formatDate(item.dateTaken), formatBytes(context, item.originalSize)),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                TextButton(onClick = { confirmRestore = true }, colors = colors) {
                    Icon(Icons.Rounded.RestorePage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vault_restore))
                }
                TextButton(onClick = { confirmDelete = true }, colors = colors) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vault_delete))
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.vault_restore_confirm_title)) },
            text = { Text(stringResource(R.string.vault_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; onRestore() }) { Text(stringResource(R.string.vault_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vault_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vault_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.vault_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
