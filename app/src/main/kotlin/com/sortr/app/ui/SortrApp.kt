package com.sortr.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortr.app.ui.navigation.SortrNavHost
import com.sortr.app.ui.onboarding.OnboardingScreen
import com.sortr.app.ui.permission.MediaAccess
import com.sortr.app.ui.permission.MediaPermissions
import com.sortr.app.ui.permission.PermissionScreen

/** Top-level gate: onboarding, then photo access, then the app itself. */
@Composable
fun SortrApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onAccessChecked(MediaPermissions.currentAccess(context))
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onAccessChecked(MediaPermissions.currentAccess(context))
    }
    val requestAccess = { permissionLauncher.launch(MediaPermissions.requested) }
    val openAppSettings = { context.startActivity(MediaPermissions.appSettingsIntent(context)) }

    when {
        state.loading -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        !state.onboardingDone -> OnboardingScreen(
            onGetStarted = {
                viewModel.completeOnboarding()
                if (state.access == MediaAccess.DENIED) requestAccess()
            },
        )
        state.access == MediaAccess.DENIED -> PermissionScreen(
            onAllow = requestAccess,
            onOpenSettings = openAppSettings,
        )
        else -> SortrNavHost(
            access = state.access,
            onRequestAccess = requestAccess,
            onOpenAppSettings = openAppSettings,
        )
    }
}
