package com.sortr.app.ui.whatsapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortr.app.R
import com.sortr.app.ui.design.GroupedCard
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.theme.palette
import com.sortr.data.media.HiddenFolder
import com.sortr.data.media.HiddenFolderApp
import com.sortr.data.media.HiddenFolders
import com.sortr.domain.repository.AppSettings
import com.sortr.domain.repository.GallerySync
import com.sortr.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhatsAppAccessState(
    val folders: List<HiddenFolder> = emptyList(),
    /** Which WhatsApp apps have media on this phone, judged from what the gallery shows. */
    val appsInUse: Set<HiddenFolderApp> = emptySet(),
    val promptDismissed: Boolean = true,
) {
    /** Apps with media whose hidden folder isn't included yet. */
    val missing: List<HiddenFolderApp>
        get() = HiddenFolderApp.entries.filter { app -> app in appsInUse && folders.none { it.app == app } }
}

enum class WhatsAppMessage { ADDED, WRONG_FOLDER }

@HiltViewModel
class WhatsAppAccessViewModel @Inject constructor(
    private val hiddenFolders: HiddenFolders,
    private val gallerySync: GallerySync,
    private val settings: AppSettings,
    library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<WhatsAppAccessState> = combine(
        hiddenFolders.folders,
        library.observeAlbums().map { albums ->
            buildSet {
                if (albums.any { it.relativePath.contains("com.whatsapp/") || it.relativePath.startsWith("WhatsApp/") }) {
                    add(HiddenFolderApp.WHATSAPP)
                }
                if (albums.any { it.relativePath.contains("com.whatsapp.w4b/") || it.relativePath.startsWith("WhatsApp Business/") }) {
                    add(HiddenFolderApp.WHATSAPP_BUSINESS)
                }
            }
        },
        settings.whatsAppPromptDismissed,
        ::WhatsAppAccessState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsAppAccessState())

    private val _messages = Channel<WhatsAppMessage>(Channel.BUFFERED)
    val messages: Flow<WhatsAppMessage> = _messages.receiveAsFlow()

    fun initialUri(app: HiddenFolderApp): Uri = hiddenFolders.initialUri(app)

    fun onPicked(uri: Uri) {
        viewModelScope.launch {
            if (hiddenFolders.add(uri)) {
                gallerySync.requestSync()
                _messages.send(WhatsAppMessage.ADDED)
            } else {
                _messages.send(WhatsAppMessage.WRONG_FOLDER)
            }
        }
    }

    fun remove(folder: HiddenFolder) {
        viewModelScope.launch {
            hiddenFolders.remove(folder.treeUri)
            gallerySync.requestSync()
        }
    }

    fun dismissPrompt() {
        viewModelScope.launch { settings.dismissWhatsAppPrompt() }
    }
}

/** Opens Android's folder picker on WhatsApp's media folder and hands the result to [viewModel]. */
@Composable
fun rememberFolderPicker(viewModel: WhatsAppAccessViewModel, snackbarHostState: SnackbarHostState): (HiddenFolderApp) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onPicked(uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                WhatsAppMessage.ADDED -> R.string.whatsapp_added
                WhatsAppMessage.WRONG_FOLDER -> R.string.whatsapp_wrong_folder
            }
            snackbarHostState.showSnackbar(context.getString(text))
        }
    }
    return { app -> launcher.launch(viewModel.initialUri(app)) }
}

@Composable
private fun appName(app: HiddenFolderApp): String = stringResource(
    when (app) {
        HiddenFolderApp.WHATSAPP -> R.string.whatsapp_name
        HiddenFolderApp.WHATSAPP_BUSINESS -> R.string.whatsapp_business_name
    },
)

/** Settings section: include or stop including WhatsApp's hidden media. */
@Composable
fun WhatsAppSettingsSection(
    snackbarHostState: SnackbarHostState,
    viewModel: WhatsAppAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick = rememberFolderPicker(viewModel, snackbarHostState)
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val palette = MaterialTheme.palette
    // WhatsApp is always offered; WhatsApp Business only when it's on the phone.
    val offered = HiddenFolderApp.entries.filter { it == HiddenFolderApp.WHATSAPP || it in state.appsInUse || state.folders.any { f -> f.app == it } }

    GroupedSection(
        header = stringResource(R.string.whatsapp_section),
        footer = stringResource(R.string.whatsapp_section_footer),
    ) {
        offered.forEach { app ->
            val folder = state.folders.firstOrNull { it.app == app }
            row {
                ListRow(
                    title = stringResource(R.string.whatsapp_include_title, appName(app)),
                    subtitle = if (folder == null) stringResource(R.string.whatsapp_include_body) else null,
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    iconColor = palette.green,
                    value = if (folder != null) stringResource(R.string.whatsapp_included) else null,
                    onClick = { if (folder != null) removing = folder.treeUri else pick(app) },
                )
            }
        }
    }

    state.folders.firstOrNull { it.treeUri == removing }?.let { folder ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.whatsapp_remove_title, appName(folder.app))) },
            text = { Text(stringResource(R.string.whatsapp_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        viewModel.remove(folder)
                    },
                ) { Text(stringResource(R.string.whatsapp_remove_action)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Card offering WhatsApp's hidden media. On Home it shows once WhatsApp media is seen in the
 * gallery, until the user includes it or taps Not now. With [alwaysOffer] (WhatsApp's own page)
 * it shows whenever WhatsApp's hidden media isn't included yet, without Not now.
 */
@Composable
fun WhatsAppPromptCard(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    alwaysOffer: Boolean = false,
    viewModel: WhatsAppAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick = rememberFolderPicker(viewModel, snackbarHostState)
    val app = state.missing.firstOrNull()
        ?: HiddenFolderApp.WHATSAPP.takeIf { alwaysOffer && state.folders.none { it.app == HiddenFolderApp.WHATSAPP } }
    if ((state.promptDismissed && !alwaysOffer) || app == null) return

    GroupedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.AutoMirrored.Rounded.Chat, MaterialTheme.palette.green)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.whatsapp_prompt_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.whatsapp_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.whatsapp_prompt_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TintedButton(text = stringResource(R.string.whatsapp_prompt_action), onClick = { pick(app) }, compact = true)
                if (!alwaysOffer) {
                    TextButton(onClick = viewModel::dismissPrompt) { Text(stringResource(R.string.action_not_now)) }
                }
            }
        }
    }
}
