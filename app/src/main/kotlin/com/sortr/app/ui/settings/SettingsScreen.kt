package com.sortr.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FaceRetouchingOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.goal.GoalSettingsSection
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.common.formatDate
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.LargeTitleScaffold
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.ScreenPadding
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.vault.VaultAuthDialog
import com.sortr.app.ui.whatsapp.WhatsAppSettingsSection
import com.sortr.domain.model.FaceModelState
import com.sortr.domain.model.VaultBackupException
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Vault actions that need the PIN or fingerprint first. */
private enum class VaultAction { EXPORT, IMPORT }

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

    LargeTitleScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val palette = MaterialTheme.palette
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
        ) {
            GroupedSection(
                header = stringResource(R.string.settings_section_sorting),
                footer = stringResource(R.string.settings_reset_reviewed_body),
            ) {
                row {
                    ListRow(
                        title = stringResource(R.string.settings_reset_reviewed),
                        icon = Icons.Rounded.RestartAlt,
                        iconColor = palette.blue,
                        onClick = { confirmReset = true },
                    )
                }
            }

            GoalSettingsSection(snackbarHostState = snackbarHostState)

            WhatsAppSettingsSection(snackbarHostState = snackbarHostState)

            PeopleSettings(
                state = state,
                onThresholdChosen = viewModel::setFaceThreshold,
                onChargingChanged = viewModel::setScanOnlyWhileCharging,
                onRescan = { confirmRescan = true },
            )

            GroupedSection(header = stringResource(R.string.settings_section_vault)) {
                if (!state.vaultSetUp) {
                    row {
                        ListRow(
                            title = stringResource(R.string.settings_vault_set_up),
                            subtitle = stringResource(R.string.settings_vault_set_up_body),
                            icon = Icons.Rounded.Lock,
                            iconColor = palette.indigo,
                            onClick = onOpenVault,
                        )
                    }
                } else {
                    row {
                        ListRow(
                            title = stringResource(R.string.settings_change_pin),
                            icon = Icons.Rounded.Password,
                            iconColor = palette.gray,
                            onClick = {
                                viewModel.clearPinFeedback()
                                changingPin = true
                            },
                        )
                    }
                    row {
                        ListRow(
                            title = stringResource(R.string.settings_export_backup),
                            subtitle = state.lastBackupAt?.let { stringResource(R.string.settings_last_backup, formatDate(it)) }
                                ?: stringResource(R.string.settings_never_backed_up),
                            icon = Icons.Rounded.Backup,
                            iconColor = palette.blue,
                            onClick = { begin(VaultAction.EXPORT) },
                        )
                    }
                    row {
                        ListRow(
                            title = stringResource(R.string.settings_import_backup),
                            subtitle = stringResource(R.string.settings_import_backup_body),
                            icon = Icons.Rounded.Download,
                            iconColor = palette.green,
                            onClick = { begin(VaultAction.IMPORT) },
                        )
                    }
                }
            }

            GroupedSection(header = stringResource(R.string.settings_section_about)) {
                row {
                    ListRow(
                        title = stringResource(R.string.settings_privacy_title),
                        subtitle = stringResource(R.string.settings_privacy_body),
                        icon = Icons.Rounded.CloudOff,
                        iconColor = palette.teal,
                    )
                }
                row {
                    ListRow(
                        title = stringResource(R.string.app_name),
                        icon = Icons.Rounded.Info,
                        iconColor = palette.gray,
                        value = stringResource(R.string.settings_version, versionName),
                    )
                }
            }
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
    val palette = MaterialTheme.palette
    val header = stringResource(R.string.settings_section_people)
    when (state.faceModelState) {
        FaceModelState.MISSING, FaceModelState.UNSUPPORTED -> {
            GroupedSection(header = header) {
                row {
                    ListRow(
                        title = stringResource(R.string.settings_faces_off),
                        subtitle = stringResource(
                            if (state.faceModelState == FaceModelState.MISSING) R.string.face_model_missing else R.string.face_model_unsupported,
                        ),
                        icon = Icons.Rounded.FaceRetouchingOff,
                        iconColor = palette.gray,
                    )
                }
            }
            return
        }
        FaceModelState.READY -> Unit
    }

    // Moves freely while dragging; people are regrouped once the finger lifts.
    var threshold by remember(state.faceThreshold) { mutableFloatStateOf(state.faceThreshold) }
    GroupedSection(header = header) {
        row {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Tune, palette.purple)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.settings_face_strictness), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    stringResource(R.string.settings_face_strictness_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                    Text(
                        stringResource(R.string.settings_face_strictness_looser),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.settings_face_strictness_stricter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        row {
            ListRow(
                title = stringResource(R.string.settings_scan_charging),
                subtitle = stringResource(R.string.settings_scan_charging_body),
                icon = Icons.Rounded.BatteryChargingFull,
                iconColor = palette.green,
                onClick = { onChargingChanged(!state.scanOnlyWhileCharging) },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = state.scanOnlyWhileCharging,
                        onCheckedChange = onChargingChanged,
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.green, checkedBorderColor = palette.green),
                    )
                },
            )
        }
        row {
            ListRow(
                title = stringResource(R.string.settings_rescan),
                subtitle = stringResource(R.string.settings_rescan_body),
                icon = Icons.Rounded.Refresh,
                iconColor = palette.orange,
                onClick = onRescan,
            )
        }
    }
}

private const val STRICTNESS_MIN = 0.45f
private const val STRICTNESS_MAX = 0.8f
/** 0.05 apart: 0.45, 0.50, … 0.80. */
private const val STRICTNESS_STEPS = 6
