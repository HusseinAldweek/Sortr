package com.sortr.app.ui.swipe

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.design.BackButton
import com.sortr.app.ui.design.EmptyState
import com.sortr.app.ui.design.GroupedSection
import com.sortr.app.ui.design.ListRow
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.design.TintedButton
import com.sortr.app.ui.library.sessionTitle
import com.sortr.app.ui.theme.palette
import com.sortr.app.ui.vault.RemoveOriginalsCard
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SwipeAction
import kotlinx.coroutines.launch

@Composable
fun SwipeScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: () -> Unit,
    viewModel: SwipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val personName by viewModel.personName.collectAsStateWithLifecycle()
    val ready = state as? SwipeUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }
    var askVaultSetup by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                SwipeMessage.SAVE_FAILED -> R.string.swipe_error_save
                SwipeMessage.VAULT_FAILED -> R.string.swipe_vault_error
                SwipeMessage.UNDO_BLOCKED -> R.string.swipe_undo_vault_blocked
            }
            scope.launch { snackbarHostState.showSnackbar(context.getString(text)) }
        }
    }

    Scaffold(
        topBar = {
            SwipeTopBar(
                title = sessionTitle(viewModel.filter, personName),
                state = ready,
                onBack = onBack,
                onUndo = viewModel::onUndo,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val current = ready?.current
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ready.total == 0 -> EmptySession(onBack = onBack)
                current == null -> SessionDone(
                    counts = ready.counts,
                    snackbarHostState = snackbarHostState,
                    onOriginalsRemoved = viewModel::onOriginalsRemoved,
                    onOpenTrash = onOpenTrash,
                    onBack = onBack,
                )
                else -> SwipeContent(
                    current = current,
                    state = ready,
                    enabledActions = viewModel.enabledActions,
                    canCommit = { action -> viewModel.canCommit(current, action) },
                    onBlocked = {
                        if (current.isVideo) {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.swipe_video_private_blocked)) }
                        } else {
                            askVaultSetup = true
                        }
                    },
                    onCommit = { photo, action -> viewModel.onSwipe(photo, action) },
                    onOpen = { viewerPhoto = it },
                )
            }
        }
    }

    viewerPhoto?.let { photo ->
        FullScreenViewer(photo = photo, onDismiss = { viewerPhoto = null })
    }

    if (askVaultSetup) {
        AlertDialog(
            onDismissRequest = { askVaultSetup = false },
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.swipe_vault_setup_title)) },
            text = { Text(stringResource(R.string.swipe_vault_setup_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askVaultSetup = false
                        onOpenVault()
                    },
                ) { Text(stringResource(R.string.swipe_vault_setup_action)) }
            },
            dismissButton = {
                TextButton(onClick = { askVaultSetup = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTopBar(
    title: String,
    state: SwipeUiState.Ready?,
    onBack: () -> Unit,
    onUndo: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state != null && state.total > 0) {
                    val shown = (state.position + 1).coerceAtMost(state.total)
                    Text(
                        text = stringResource(R.string.swipe_progress, formatCount(shown), formatCount(state.total)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = {
            TextButton(onClick = onUndo, enabled = state?.canUndo == true) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.swipe_undo), style = MaterialTheme.typography.bodyLarge)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun SwipeContent(
    current: Photo,
    state: SwipeUiState.Ready,
    enabledActions: Set<SwipeAction>,
    canCommit: (SwipeAction) -> Boolean,
    onBlocked: (SwipeAction) -> Unit,
    onCommit: (Photo, SwipeAction) -> Unit,
    onOpen: (Photo) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val cardState = remember(current.id, state.revision) { SwipeCardState() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val commitWithHaptics: (Photo, SwipeAction) -> Unit = { photo, action ->
        view.performHapticFeedback(
            if (action == SwipeAction.DELETE) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONFIRM,
        )
        onCommit(photo, action)
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged { cardSize = it },
        ) {
            SwipeStack(
                current = current,
                upcoming = state.upcoming,
                cardState = cardState,
                cardSize = cardSize,
                enabledActions = enabledActions,
                canCommit = canCommit,
                onBlocked = onBlocked,
                onCommit = commitWithHaptics,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SwipeButtons(
            enabledActions = enabledActions,
            onAction = { action ->
                when {
                    cardSize == IntSize.Zero -> Unit
                    !canCommit(action) -> onBlocked(action)
                    else -> scope.launch {
                        if (cardState.flyOut(action, cardSize)) commitWithHaptics(current, action)
                    }
                }
            },
        )
    }
}

/** Same decisions as the swipes, for people who prefer tapping or use accessibility tools. */
@Composable
private fun SwipeButtons(
    enabledActions: Set<SwipeAction>,
    onAction: (SwipeAction) -> Unit,
) {
    // Buttons follow the physical swipe directions, so they stay put in right-to-left layouts.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ButtonOrder.filter { it in enabledActions }.forEach { action ->
                val label = stringResource(action.labelRes)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onAction(action) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(62.dp)
                            .semantics { contentDescription = label },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDone(
    counts: SessionCounts,
    snackbarHostState: SnackbarHostState,
    onOriginalsRemoved: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit,
) {
    EmptyState(
        icon = Icons.Rounded.Check,
        iconColor = MaterialTheme.palette.green,
        title = stringResource(R.string.swipe_done_title),
        body = stringResource(R.string.swipe_done_body),
    ) {
        val lines = buildList {
            if (counts.kept > 0) add(pluralStringResource(R.plurals.swipe_done_kept, counts.kept, formatCount(counts.kept)))
            if (counts.trashed > 0) add(pluralStringResource(R.plurals.swipe_done_trashed, counts.trashed, formatCount(counts.trashed)))
            if (counts.vaulted > 0) add(pluralStringResource(R.plurals.swipe_done_vaulted, counts.vaulted, formatCount(counts.vaulted)))
            if (counts.skipped > 0) add(pluralStringResource(R.plurals.swipe_done_skipped, counts.skipped, formatCount(counts.skipped)))
        }
        if (lines.isNotEmpty()) {
            GroupedSection(separatorIndent = 16.dp) {
                lines.forEach { line -> row { ListRow(title = line) } }
            }
            Spacer(Modifier.height(20.dp))
        }
        RemoveOriginalsCard(
            snackbarHostState = snackbarHostState,
            onRemoved = onOriginalsRemoved,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (counts.trashed > 0) {
            PillButton(text = stringResource(R.string.swipe_go_to_trash), onClick = onOpenTrash)
            Spacer(Modifier.height(8.dp))
        }
        TintedButton(
            text = stringResource(R.string.swipe_back_home),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptySession(onBack: () -> Unit) {
    EmptyState(
        icon = Icons.Rounded.CheckCircle,
        iconColor = MaterialTheme.palette.green,
        title = stringResource(R.string.swipe_empty_title),
        body = stringResource(R.string.swipe_empty_body),
    ) {
        TintedButton(
            text = stringResource(R.string.swipe_back_home),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
