package com.sortr.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFDF7FF),
    surface = Color(0xFFFDF7FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF2D0F9E),
    primaryContainer = Color(0xFF4426BE),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
)

@Composable
fun SortrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Fixed colours for the swipe decisions, the same in light and dark themes. */
object SwipeColors {
    val Keep = Color(0xFF2E7D32)
    val Delete = Color(0xFFC62828)
    val Private = Color(0xFF7B1FA2)
    val Skip = Color(0xFF616161)
}
