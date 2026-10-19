package com.sortr.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.vault.Biometrics
import com.sortr.app.vault.VaultLock
import com.sortr.app.vault.findFragmentActivity
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.vault.PinCheck
import com.sortr.domain.vault.PinPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

sealed interface PinFeedback {
    data class Wrong(val attemptsLeft: Int) : PinFeedback
    data class LockedOut(val untilMillis: Long) : PinFeedback
}

data class UnlockUiState(
    val feedback: PinFeedback? = null,
    /** Bumped after each check so the PIN pad clears. */
    val attempt: Int = 0,
    val checking: Boolean = false,
)

@HiltViewModel
class VaultUnlockViewModel @Inject constructor(
    private val vaultAuth: VaultAuth,
    private val vaultLock: VaultLock,
) : ViewModel() {

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private val _unlocked = Channel<Unit>(Channel.CONFLATED)
    val unlocked: Flow<Unit> = _unlocked.receiveAsFlow()

    fun submitPin(pin: String) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val result = vaultAuth.verify(pin)
            _state.update {
                it.copy(
                    checking = false,
                    attempt = it.attempt + 1,
                    feedback = when (result) {
                        PinCheck.Correct -> null
                        is PinCheck.Wrong -> PinFeedback.Wrong(result.attemptsBeforeLockout)
                        is PinCheck.LockedOut -> PinFeedback.LockedOut(result.untilMillis)
                    },
                )
            }
            if (result == PinCheck.Correct) {
                vaultLock.unlock()
                _unlocked.send(Unit)
            }
        }
    }

    fun onBiometricSuccess() {
        vaultLock.unlock()
        _unlocked.trySend(Unit)
    }
}

/** Full screen lock shown in the vault. Offers the fingerprint straight away when there is one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultLockScreen(
    onBack: () -> Unit,
    viewModel: VaultUnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val promptBiometric = rememberBiometricPrompt(
        onSuccess = viewModel::onBiometricSuccess,
        onUsePin = {},
    )
    val biometricAvailable = promptBiometric != null

    LaunchedEffect(Unit) { promptBiometric?.invoke() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.vault_unlock_title), style = MaterialTheme.typography.headlineSmall)
            val feedback = pinFeedbackText(state.feedback)
            Text(
                text = feedback.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            PinPad(
                enabled = !state.checking && !isLockedOut(state.feedback),
                resetKey = state.attempt,
                onSubmit = viewModel::submitPin,
            )
            if (biometricAvailable) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { promptBiometric?.invoke() }) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.vault_use_biometric))
                }
            }
        }
    }
}

/**
 * Asks for the fingerprint, falling back to a PIN dialog. Used outside the vault, e.g. before
 * exporting a backup from Settings.
 */
@Composable
fun VaultAuthDialog(
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: VaultUnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    var showPin by remember { mutableStateOf(false) }
    var pin by remember(state.attempt) { mutableStateOf("") }

    val promptBiometric = rememberBiometricPrompt(
        onSuccess = viewModel::onBiometricSuccess,
        onUsePin = { showPin = true },
    )

    LaunchedEffect(viewModel) { viewModel.unlocked.collect { currentOnAuthenticated() } }
    LaunchedEffect(Unit) {
        if (promptBiometric != null) promptBiometric() else showPin = true
    }

    if (!showPin) return
    val lockedOut = isLockedOut(state.feedback)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_pin_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it in '0'..'9' }.take(PinPolicy.MAX_LENGTH) },
                    singleLine = true,
                    enabled = !lockedOut,
                    isError = state.feedback != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                pinFeedbackText(state.feedback)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.submitPin(pin) },
                enabled = !state.checking && !lockedOut && PinPolicy.isValid(pin),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Returns a function that shows the fingerprint prompt, or null if there's no strong biometric. */
@Composable
private fun rememberBiometricPrompt(onSuccess: () -> Unit, onUsePin: () -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val activity = context.findFragmentActivity() ?: return null
    if (!remember { Biometrics.isAvailable(context) }) return null
    val title = stringResource(R.string.biometric_title)
    val usePin = stringResource(R.string.biometric_use_pin)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnUsePin by rememberUpdatedState(onUsePin)
    return {
        Biometrics.authenticate(
            activity = activity,
            title = title,
            usePinLabel = usePin,
            onSuccess = { currentOnSuccess() },
            onUsePin = { currentOnUsePin() },
            // Errors like "too many attempts" already show in the system prompt; fall back to the PIN.
            onError = { currentOnUsePin() },
        )
    }
}

@Composable
private fun isLockedOut(feedback: PinFeedback?): Boolean {
    if (feedback !is PinFeedback.LockedOut) return false
    return rememberMillisUntil(feedback.untilMillis) > 0
}

@Composable
fun pinFeedbackText(feedback: PinFeedback?): String? = when (feedback) {
    null -> null
    is PinFeedback.Wrong -> if (feedback.attemptsLeft > 0) {
        pluralStringResource(R.plurals.vault_pin_wrong, feedback.attemptsLeft, formatCount(feedback.attemptsLeft))
    } else {
        stringResource(R.string.vault_pin_wrong_plain)
    }
    is PinFeedback.LockedOut -> {
        val remaining = rememberMillisUntil(feedback.untilMillis)
        if (remaining <= 0) null else stringResource(R.string.vault_locked_out, formatDuration(remaining))
    }
}

/** Milliseconds left until [until], updated every second. */
@Composable
private fun rememberMillisUntil(until: Long): Long {
    var now by remember(until) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (now < until) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return until - now
}

@Composable
private fun formatDuration(millis: Long): String {
    val seconds = ceil(millis / 1000.0).toInt()
    return if (seconds < 60) {
        pluralStringResource(R.plurals.duration_seconds, seconds, formatCount(seconds))
    } else {
        val minutes = ceil(seconds / 60.0).toInt()
        pluralStringResource(R.plurals.duration_minutes, minutes, formatCount(minutes))
    }
}
