package com.sortr.app.ui.vault

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sortr.app.R
import com.sortr.domain.vault.PinPolicy
import java.text.NumberFormat

/**
 * Number pad for the vault PIN. The entry is cleared whenever [resetKey] changes, e.g. after a
 * wrong PIN. Digits are always stored as 0-9, but labelled in the phone's own numerals.
 */
@Composable
fun PinPad(
    enabled: Boolean,
    resetKey: Any,
    onSubmit: (String) -> Unit,
) {
    var pin by remember(resetKey) { mutableStateOf("") }
    val view = LocalView.current
    val context = LocalContext.current
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    fun press(digit: Char) {
        if (!enabled || pin.length >= PinPolicy.MAX_LENGTH) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        pin += digit
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val dotsDescription = context.resources.getQuantityString(R.plurals.pin_digits_entered, pin.length, pin.length)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .height(20.dp)
                .semantics { contentDescription = dotsDescription },
        ) {
            repeat(maxOf(PinPolicy.MIN_LENGTH, pin.length)) { index ->
                val filled = index < pin.length
                Box(
                    Modifier
                        .size(14.dp)
                        .then(
                            if (filled) {
                                Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        // Number pads are laid out the same way in every language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { digit -> DigitKey(numberFormat.format(digit.digitToInt()), enabled) { press(digit) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        enabled = enabled && pin.isNotEmpty(),
                        modifier = Modifier.size(KEY_SIZE),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.pin_delete_digit))
                    }
                    DigitKey(numberFormat.format(0), enabled) { press('0') }
                    FilledIconButton(
                        onClick = { onSubmit(pin) },
                        enabled = enabled && pin.length >= PinPolicy.MIN_LENGTH,
                        modifier = Modifier.size(KEY_SIZE),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
    }
}

private val KEY_SIZE = 72.dp
