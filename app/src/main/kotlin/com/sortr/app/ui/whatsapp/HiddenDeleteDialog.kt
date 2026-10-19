package com.sortr.app.ui.whatsapp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.theme.palette

/**
 * Sortr's own confirmation for deleting files from hidden WhatsApp folders. Android's delete
 * dialog only covers gallery items, so for these files Sortr asks instead, and says so.
 * [kind] picks the wording: emptying the Trash, or removing originals after a vault or album move.
 */
@Composable
fun HiddenDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    kind: HiddenDeleteKind = HiddenDeleteKind.TRASH,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(
                    if (kind == HiddenDeleteKind.TRASH) R.plurals.hidden_delete_title else R.plurals.hidden_remove_originals_title,
                    count,
                    formatCount(count),
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    when (kind) {
                        HiddenDeleteKind.TRASH -> R.string.hidden_delete_body
                        HiddenDeleteKind.VAULT_ORIGINALS -> R.string.hidden_remove_originals_body
                        HiddenDeleteKind.MOVED_ORIGINALS -> R.string.hidden_moved_originals_body
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.palette.red),
            ) { Text(stringResource(R.string.hidden_delete_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

enum class HiddenDeleteKind { TRASH, VAULT_ORIGINALS, MOVED_ORIGINALS }
