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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.R
import com.sortr.app.ui.common.formatCount
import com.sortr.app.ui.viewer.FullScreenViewer
import com.sortr.domain.model.Photo
import com.sortr.domain.model.SwipeAction
import kotlinx.coroutines.launch

@Composable
fun SwipeScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SwipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? SwipeUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.swipe_error_save)
    var viewerPhoto by remember { mutableStateOf<Photo?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.saveErrors.collect { snackbarHostState.showSnackbar(saveError) }
    }

    Scaffold(
        topBar = {
            SwipeTopBar(
                title = stringResource(viewModel.filter.titleRes),
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
                    onOpenTrash = onOpenTrash,
                    onBack = onBack,
                )
                else -> SwipeContent(
                    current = current,
                    state = ready,
                    enabledActions = viewModel.enabledActions,
                    onCommit = { photo, action -> viewModel.onSwipe(photo.id, action) },
                    onOpen = { viewerPhoto = it },
                )
            }
        }
    }

    viewerPhoto?.let { photo ->
        FullScreenViewer(photo = photo, onDismiss = { viewerPhoto = null })
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
    TopAppBar(
        title = {
            Column {
                Text(title)
                if (state != null && state.total > 0) {
                    val shown = (state.position + 1).coerceAtMost(state.total)
                    Text(
                        text = stringResource(R.string.swipe_progress, formatCount(shown), formatCount(state.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            TextButton(onClick = onUndo, enabled = state?.canUndo == true) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.swipe_undo))
            }
        },
    )
}

@Composable
private fun SwipeContent(
    current: Photo,
    state: SwipeUiState.Ready,
    enabledActions: Set<SwipeAction>,
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
                onCommit = commitWithHaptics,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SwipeButtons(
            enabledActions = enabledActions,
            onAction = { action ->
                if (cardSize != IntSize.Zero) {
                    scope.launch {
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
                    FilledIconButton(
                        onClick = { onAction(action) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = action.color,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(action.icon, contentDescription = label, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
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
    onOpenTrash: () -> Unit,
    onBack: () -> Unit,
) {
    CenteredMessage(
        icon = { Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) },
        title = stringResource(R.string.swipe_done_title),
        body = stringResource(R.string.swipe_done_body),
    ) {
        val lines = buildList {
            if (counts.kept > 0) add(pluralStringResource(R.plurals.swipe_done_kept, counts.kept, formatCount(counts.kept)))
            if (counts.trashed > 0) add(pluralStringResource(R.plurals.swipe_done_trashed, counts.trashed, formatCount(counts.trashed)))
            if (counts.skipped > 0) add(pluralStringResource(R.plurals.swipe_done_skipped, counts.skipped, formatCount(counts.skipped)))
        }
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        if (counts.trashed > 0) {
            Button(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.swipe_go_to_trash))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.swipe_back_home))
        }
    }
}

@Composable
private fun EmptySession(onBack: () -> Unit) {
    CenteredMessage(
        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) },
        title = stringResource(R.string.swipe_empty_title),
        body = stringResource(R.string.swipe_empty_body),
    ) {
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.swipe_back_home))
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}
