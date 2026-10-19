package com.sortr.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sortr.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val resetDoneMessage = stringResource(R.string.settings_reset_done)
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    LaunchedEffect(viewModel) {
        viewModel.resetDone.collect { snackbarHostState.showSnackbar(resetDoneMessage) }
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}
