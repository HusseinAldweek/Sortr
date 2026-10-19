package com.sortr.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sortr.app.R

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(32.dp))
                OnboardingPoint(
                    icon = Icons.Rounded.Swipe,
                    title = stringResource(R.string.onboarding_swipe_title),
                    body = stringResource(R.string.onboarding_swipe_body),
                )
                OnboardingPoint(
                    icon = Icons.Rounded.RestoreFromTrash,
                    title = stringResource(R.string.onboarding_safe_title),
                    body = stringResource(R.string.onboarding_safe_body),
                )
                OnboardingPoint(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.onboarding_private_title),
                    body = stringResource(R.string.onboarding_private_body),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_get_started))
            }
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
