package com.sortr.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.vault.PinFeedback
import com.sortr.app.ui.vault.pinFeedbackText
import com.sortr.domain.vault.PinPolicy

const val MIN_BACKUP_PASSWORD = 8

/**
 * Asks for the backup password. With [confirm] (when exporting) it must be typed twice and be
 * at least [MIN_BACKUP_PASSWORD] characters.
 */
@Composable
fun BackupPasswordDialog(
    confirm: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val tooShort = confirm && password.length < MIN_BACKUP_PASSWORD
    val mismatch = confirm && password != repeat
    val error = when {
        !showErrors -> null
        tooShort -> stringResource(R.string.backup_password_too_short, MIN_BACKUP_PASSWORD)
        mismatch -> stringResource(R.string.backup_password_mismatch)
        else -> null
    }
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = stringResource(if (visible) R.string.backup_hide_password else R.string.backup_show_password),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirm) R.string.backup_password_title else R.string.backup_password_enter_title)) },
        text = {
            Column {
                if (confirm) {
                    Text(stringResource(R.string.backup_password_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = toggle,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (tooShort || mismatch) {
                        showErrors = true
                    } else {
                        onSubmit(password.toCharArray())
                    }
                },
                enabled = password.isNotEmpty(),
            ) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun ChangePinDialog(
    feedback: PinFeedback?,
    busy: Boolean,
    onSubmit: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val newInvalid = !PinPolicy.isValid(new)
    val mismatch = new != repeat
    val localError = when {
        !showErrors -> null
        newInvalid -> stringResource(R.string.vault_pin_invalid)
        mismatch -> stringResource(R.string.vault_pin_mismatch)
        else -> null
    }
    val error = localError ?: pinFeedbackText(feedback)

    @Composable
    fun pinField(value: String, label: Int, onChange: (String) -> Unit) = OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it in '0'..'9' }.take(PinPolicy.MAX_LENGTH)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_pin_title)) },
        text = {
            Column {
                pinField(current, R.string.change_pin_current) { current = it }
                Spacer(Modifier.height(8.dp))
                pinField(new, R.string.change_pin_new) { new = it }
                Spacer(Modifier.height(8.dp))
                pinField(repeat, R.string.change_pin_confirm) { repeat = it }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newInvalid || mismatch) showErrors = true else onSubmit(current, new)
                },
                enabled = !busy && current.isNotEmpty() && new.isNotEmpty(),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Blocking progress while a backup is written or read; it can't be dismissed. */
@Composable
fun BackupProgressDialog(progress: BackupProgress) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(if (progress.exporting) R.string.backup_export_title else R.string.backup_import_title)) },
        text = {
            Column {
                val label = when {
                    progress.total == 0 -> stringResource(R.string.backup_preparing)
                    progress.exporting -> stringResource(R.string.backup_exporting, formatCount(progress.done), formatCount(progress.total))
                    else -> stringResource(R.string.backup_importing, formatCount(progress.done), formatCount(progress.total))
                }
                Text(label)
                Spacer(Modifier.height(16.dp))
                if (progress.total == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
