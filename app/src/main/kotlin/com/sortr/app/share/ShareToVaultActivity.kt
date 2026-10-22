package com.sortr.app.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.launch.LaunchRequests
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.theme.SortrTheme
import com.sortr.app.ui.theme.palette
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.repository.VaultRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A photo another app shared with Sortr. */
data class SharedPhoto(val uri: String, val name: String?, val mimeType: String?)

sealed interface ShareState {
    data object Checking : ShareState
    data object NoVault : ShareState
    data object NothingToAdd : ShareState
    data class Confirm(val count: Int) : ShareState
    data class Adding(val done: Int, val total: Int) : ShareState
    data class Done(val added: Int, val alreadyThere: Int, val failed: Int) : ShareState
}

@HiltViewModel
class ShareToVaultViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val vaultAuth: VaultAuth,
) : ViewModel() {

    private var photos: List<SharedPhoto> = emptyList()
    private val _state = MutableStateFlow<ShareState>(ShareState.Checking)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    fun start(shared: List<SharedPhoto>) {
        if (_state.value != ShareState.Checking) return
        photos = shared
        viewModelScope.launch {
            _state.value = when {
                !vaultAuth.isSetUp.first() -> ShareState.NoVault
                shared.isEmpty() -> ShareState.NothingToAdd
                else -> ShareState.Confirm(shared.size)
            }
        }
    }

    fun add() {
        if (_state.value !is ShareState.Confirm) return
        viewModelScope.launch {
            var added = 0
            var alreadyThere = 0
            var failed = 0
            photos.forEachIndexed { index, photo ->
                _state.value = ShareState.Adding(index, photos.size)
                try {
                    if (vault.addShared(photo.uri, photo.name, photo.mimeType)) added++ else alreadyThere++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't add a shared photo to the vault", e)
                    failed++
                }
            }
            _state.value = ShareState.Done(added, alreadyThere, failed)
        }
    }

    private companion object {
        const val TAG = "ShareToVault"
    }
}

/**
 * Receives photos shared from other apps ("Sortr vault" in the share sheet) and encrypts them
 * into the vault. Only content addresses from other apps are accepted. Nothing is shown from
 * the vault here, so no PIN is needed; opening the vault still asks for it.
 */
@AndroidEntryPoint
class ShareToVaultActivity : ComponentActivity() {

    private val viewModel: ShareToVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(sharedPhotos(intent))
        setContent {
            SortrTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ShareSheet(
                    state = state,
                    onAdd = viewModel::add,
                    onOpenSortr = {
                        startActivity(LaunchRequests.openAppIntent(this))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun sharedPhotos(intent: Intent?): List<SharedPhoto> {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableStream())
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableStreams()
            else -> emptyList()
        }
        return uris
            // Only other apps' content; never files, and never Sortr's own data.
            .filter { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority?.startsWith(packageName) == false }
            .distinct()
            .map { uri -> SharedPhoto(uri.toString(), displayName(uri), contentResolver.getType(uri)) }
            .filter { it.mimeType == null || it.mimeType.startsWith("image/") }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun Intent.parcelableStream(): Uri? = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)

    private fun Intent.parcelableStreams(): List<Uri> =
        IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
}

@Composable
private fun ShareSheet(state: ShareState, onAdd: () -> Unit, onOpenSortr: () -> Unit, onClose: () -> Unit) {
    val palette = MaterialTheme.palette
    val busy = state is ShareState.Adding || state == ShareState.Checking
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy, onClick = onClose),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Taps on the card itself don't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val (icon, color) = when (state) {
                    is ShareState.Done -> if (state.added + state.alreadyThere > 0) Icons.Rounded.Check to palette.green else Icons.Rounded.ErrorOutline to palette.red
                    ShareState.NothingToAdd -> Icons.Rounded.ErrorOutline to palette.orange
                    else -> Icons.Rounded.Lock to palette.indigo
                }
                IconBadge(icon, color, size = 56.dp, shape = CircleShape)
                Spacer(Modifier.height(16.dp))
                when (state) {
                    ShareState.Checking -> Unit
                    ShareState.NoVault -> {
                        Title(stringResource(R.string.share_vault_no_vault_title))
                        Body(stringResource(R.string.share_vault_no_vault_body))
                        Buttons(stringResource(R.string.share_vault_open_sortr), onOpenSortr, onClose)
                    }
                    ShareState.NothingToAdd -> {
                        Title(stringResource(R.string.share_vault_nothing_title))
                        Body(stringResource(R.string.share_vault_nothing_body))
                        Buttons(stringResource(R.string.action_done), onClose, null)
                    }
                    is ShareState.Confirm -> {
                        Title(pluralStringResource(R.plurals.share_vault_confirm_title, state.count, formatCount(state.count)))
                        Body(stringResource(R.string.share_vault_confirm_body))
                        Buttons(stringResource(R.string.share_vault_add), onAdd, onClose)
                    }
                    is ShareState.Adding -> {
                        Title(stringResource(R.string.share_vault_adding, formatCount(state.done + 1), formatCount(state.total)))
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { (state.done + 1f) / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is ShareState.Done -> {
                        Title(
                            if (state.added > 0) {
                                pluralStringResource(R.plurals.share_vault_added, state.added, formatCount(state.added))
                            } else if (state.alreadyThere > 0) {
                                stringResource(R.string.share_vault_already_title)
                            } else {
                                stringResource(R.string.share_vault_failed_title)
                            },
                        )
                        val details = buildList {
                            if (state.alreadyThere > 0 && state.added > 0) {
                                add(pluralStringResource(R.plurals.share_vault_already, state.alreadyThere, formatCount(state.alreadyThere)))
                            }
                            if (state.failed > 0) add(pluralStringResource(R.plurals.share_vault_failed, state.failed, formatCount(state.failed)))
                            if (state.added > 0) add(stringResource(R.string.share_vault_original_stays))
                        }
                        if (details.isNotEmpty()) Body(details.joinToString(" "))
                        Buttons(stringResource(R.string.action_done), onClose, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit, onCancel: (() -> Unit)?) {
    Spacer(Modifier.height(20.dp))
    PillButton(text = primary, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
    if (onCancel != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
