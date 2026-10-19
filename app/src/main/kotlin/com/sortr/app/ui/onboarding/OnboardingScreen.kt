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
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sortr.app.R
import com.sortr.app.ui.design.IconBadge
import com.sortr.app.ui.design.PillButton
import com.sortr.app.ui.theme.palette

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                Spacer(Modifier.height(40.dp))
                IconBadge(
                    icon = Icons.Rounded.Style,
                    color = MaterialTheme.palette.blue,
                    size = 80.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
                OnboardingPoint(
                    icon = Icons.Rounded.Swipe,
                    color = MaterialTheme.palette.blue,
                    title = stringResource(R.string.onboarding_swipe_title),
                    body = stringResource(R.string.onboarding_swipe_body),
                )
                OnboardingPoint(
                    icon = Icons.Rounded.RestoreFromTrash,
                    color = MaterialTheme.palette.green,
                    title = stringResource(R.string.onboarding_safe_title),
                    body = stringResource(R.string.onboarding_safe_body),
                )
                OnboardingPoint(
                    icon = Icons.Rounded.CloudOff,
                    color = MaterialTheme.palette.indigo,
                    title = stringResource(R.string.onboarding_private_title),
                    body = stringResource(R.string.onboarding_private_body),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(text = stringResource(R.string.onboarding_get_started), onClick = onGetStarted)
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, color: Color, title: String, body: String) {
    Row(Modifier.padding(vertical = 14.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(34.dp),
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
