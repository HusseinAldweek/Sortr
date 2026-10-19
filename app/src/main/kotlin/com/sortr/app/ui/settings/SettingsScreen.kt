package com.sortr.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FaceRetouchingOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.vault.VaultAuthDialog
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.VaultBackupException
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Vault actions that need the PIN or fingerprint first. */
private enum class VaultAction { EXPORT, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    startBackup: Boolean,
    onBack: () -> Unit,
    onOpenVault: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val pinFeedback by viewModel.pinFeedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmRescan by rememberSaveable { mutableStateOf(false) }
    var authFor by rememberSaveable { mutableStateOf<VaultAction?>(null) }
    var askExportPassword by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var changingPin by rememberSaveable { mutableStateOf(false) }
    var pinBusy by remember { mutableStateOf(false) }
    var startBackupHandled by rememberSaveable { mutableStateOf(false) }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) viewModel.export(uri) else viewModel.cancelExport() }

    val openBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri = uri
    }

    fun proceed(action: VaultAction) {
        when (action) {
            VaultAction.EXPORT -> askExportPassword = true
            VaultAction.IMPORT -> openBackupFile.launch(arrayOf("*/*"))
        }
    }

    fun begin(action: VaultAction) {
        scope.launch {
            if (action == VaultAction.EXPORT && viewModel.isVaultEmpty()) {
                viewModel.onVaultEmpty()
            } else if (viewModel.isVaultUnlocked) {
                proceed(action)
            } else {
                authFor = action
            }
        }
    }

    LaunchedEffect(startBackup, state.vaultSetUp) {
        if (startBackup && state.vaultSetUp && !startBackupHandled) {
            startBackupHandled = true
            begin(VaultAction.EXPORT)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.ResetDone -> context.getString(R.string.settings_reset_done)
                SettingsEvent.VaultEmpty -> context.getString(R.string.backup_vault_empty)
                is SettingsEvent.ExportDone ->
                    context.resources.getQuantityString(R.plurals.backup_export_done, event.count, formatCount(event.count))
                SettingsEvent.ExportFailed -> context.getString(R.string.backup_export_failed)
                is SettingsEvent.ImportDone -> {
                    val imported = context.resources.getQuantityString(R.plurals.backup_import_done, event.imported, formatCount(event.imported))
                    if (event.skipped == 0) {
                        imported
                    } else {
                        val skipped = context.resources.getQuantityString(R.plurals.backup_import_skipped, event.skipped, formatCount(event.skipped))
                        context.getString(R.string.backup_import_summary, imported, skipped)
                    }
                }
                is SettingsEvent.ImportFailed -> context.getString(
                    when (event.reason) {
                        VaultBackupException.Reason.WRONG_PASSWORD -> R.string.backup_wrong_password
                        VaultBackupException.Reason.NOT_A_BACKUP -> R.string.backup_not_a_backup
                        VaultBackupException.Reason.DAMAGED -> R.string.backup_damaged
                        VaultBackupException.Reason.UNSUPPORTED_VERSION -> R.string.backup_unsupported
                        null -> R.string.backup_import_failed
                    },
                )
                SettingsEvent.PinChanged -> context.getString(R.string.change_pin_done)
                SettingsEvent.Regrouped -> context.getString(R.string.settings_people_regrouped)
                SettingsEvent.RescanStarted -> context.getString(R.string.settings_rescan_started)
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_section_sorting))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reset_reviewed)) },
                supportingContent = { Text(stringResource(R.string.settings_reset_reviewed_body)) },
                leadingContent = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                modifier = Modifier.clickable { confirmReset = true },
            )

            SectionTitle(stringResource(R.string.settings_section_people))
            PeopleSettings(
                state = state,
                onThresholdChosen = viewModel::setFaceThreshold,
                onChargingChanged = viewModel::setScanOnlyWhileCharging,
                onRescan = { confirmRescan = true },
            )

            SectionTitle(stringResource(R.string.settings_section_vault))
            if (!state.vaultSetUp) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_vault_set_up)) },
                    supportingContent = { Text(stringResource(R.string.settings_vault_set_up_body)) },
                    leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenVault),
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_change_pin)) },
                    leadingContent = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.clearPinFeedback()
                        changingPin = true
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_backup)) },
                    supportingContent = {
                        Text(
                            state.lastBackupAt?.let { stringResource(R.string.settings_last_backup, formatDate(it)) }
                                ?: stringResource(R.string.settings_never_backed_up),
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Backup, contentDescription = null) },
                    modifier = Modifier.clickable { begin(VaultAction.EXPORT) },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import_backup)) },
                    supportingContent = { Text(stringResource(R.string.settings_import_backup_body)) },
                    leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    modifier = Modifier.clickable { begin(VaultAction.IMPORT) },
                )
            }

            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_body)) },
                leadingContent = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, versionName)) },
                leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_reviewed_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        viewModel.resetReviewed()
                    },
                ) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmRescan) {
        AlertDialog(
            onDismissRequest = { confirmRescan = false },
            title = { Text(stringResource(R.string.settings_rescan_confirm_title)) },
            text = { Text(stringResource(R.string.settings_rescan_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRescan = false
                        viewModel.rescanFaces()
                    },
                ) { Text(stringResource(R.string.settings_rescan_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRescan = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    authFor?.let { action ->
        VaultAuthDialog(
            onAuthenticated = {
                authFor = null
                proceed(action)
            },
            onDismiss = { authFor = null },
        )
    }

    if (askExportPassword) {
        BackupPasswordDialog(
            confirm = true,
            onSubmit = { password ->
                askExportPassword = false
                viewModel.setExportPassword(password)
                createBackupFile.launch("sortr-vault-${LocalDate.now()}.sortrvault")
            },
            onDismiss = { askExportPassword = false },
        )
    }

    importUri?.let { uri ->
        BackupPasswordDialog(
            confirm = false,
            onSubmit = { password ->
                importUri = null
                viewModel.import(uri, password)
            },
            onDismiss = { importUri = null },
        )
    }

    if (changingPin) {
        ChangePinDialog(
            feedback = pinFeedback,
            busy = pinBusy,
            onSubmit = { current, new ->
                pinBusy = true
                viewModel.changePin(current, new) { changed ->
                    pinBusy = false
                    if (changed) changingPin = false
                }
            },
            onDismiss = { changingPin = false },
        )
    }

    progress?.let { BackupProgressDialog(it) }
}

@Composable
private fun PeopleSettings(
    state: SettingsUiState,
    onThresholdChosen: (Float) -> Unit,
    onChargingChanged: (Boolean) -> Unit,
    onRescan: () -> Unit,
) {
    when (state.faceModelState) {
        FaceModelState.MISSING, FaceModelState.UNSUPPORTED -> {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_faces_off)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (state.faceModelState == FaceModelState.MISSING) R.string.face_model_missing else R.string.face_model_unsupported,
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Rounded.FaceRetouchingOff, contentDescription = null) },
            )
            return
        }
        FaceModelState.READY -> Unit
    }

    // Moves freely while dragging; people are regrouped once the finger lifts.
    var threshold by remember(state.faceThreshold) { mutableFloatStateOf(state.faceThreshold) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_face_strictness)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_face_strictness_body))
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = { if (threshold != state.faceThreshold) onThresholdChosen(threshold) },
                    valueRange = STRICTNESS_MIN..STRICTNESS_MAX,
                    steps = STRICTNESS_STEPS,
                    enabled = !state.regrouping,
                    // At the ends the handle sits near the screen edge, where a drag would
                    // otherwise trigger the system back gesture.
                    modifier = Modifier.systemGestureExclusion(),
                )
                Row {
                    Text(stringResource(R.string.settings_face_strictness_looser), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.settings_face_strictness_stricter), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_scan_charging)) },
        supportingContent = { Text(stringResource(R.string.settings_scan_charging_body)) },
        leadingContent = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null) },
        trailingContent = { Switch(checked = state.scanOnlyWhileCharging, onCheckedChange = onChargingChanged) },
        modifier = Modifier.clickable { onChargingChanged(!state.scanOnlyWhileCharging) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_rescan)) },
        supportingContent = { Text(stringResource(R.string.settings_rescan_body)) },
        leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onRescan),
    )
}

private const val STRICTNESS_MIN = 0.45f
private const val STRICTNESS_MAX = 0.8f
/** 0.05 apart: 0.45, 0.50, … 0.80. */
private const val STRICTNESS_STEPS = 6

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}
