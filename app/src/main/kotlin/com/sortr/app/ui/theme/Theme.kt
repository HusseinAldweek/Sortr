package com.sortr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * A calm, iOS-like look: a light grey page with white rounded cards in light mode, black with
 * dark grey cards in dark mode, one blue accent, bold large titles and generous spacing.
 */

/** Accent colours for icon badges and decisions, tuned separately for light and dark. */
@Immutable
data class Palette(
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val purple: Color,
    val indigo: Color,
    val pink: Color,
    val teal: Color,
    val gray: Color,
    /** Hairlines between rows in a grouped list. */
    val separator: Color,
    /** Background of a selected or pressed tile. */
    val fill: Color,
)

private val LightPalette = Palette(
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    purple = Color(0xFFAF52DE),
    indigo = Color(0xFF5856D6),
    pink = Color(0xFFFF2D55),
    teal = Color(0xFF30B0C7),
    gray = Color(0xFF8E8E93),
    separator = Color(0xFFD1D1D6),
    fill = Color(0x1F787880),
)

private val DarkPalette = Palette(
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    purple = Color(0xFFBF5AF2),
    indigo = Color(0xFF5E5CE6),
    pink = Color(0xFFFF375F),
    teal = Color(0xFF40C8E0),
    gray = Color(0xFF8E8E93),
    separator = Color(0xFF38383A),
    fill = Color(0x3D787880),
)

private val LightColors = lightColorScheme(
    primary = LightPalette.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = Color(0xFF00316B),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EE),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = LightPalette.indigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8E8FF),
    onTertiaryContainer = Color(0xFF1A1966),
    error = LightPalette.red,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF6B0A05),
    background = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE9E9EE),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    inversePrimary = DarkPalette.blue,
    outline = Color(0xFFC7C7CC),
    outlineVariant = LightPalette.separator,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = DarkPalette.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3A70),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = DarkPalette.indigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B2A6E),
    onTertiaryContainer = Color(0xFFE2E1FF),
    error = DarkPalette.red,
    onError = Color.White,
    errorContainer = Color(0xFF5C1410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color.Black,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    inversePrimary = LightPalette.blue,
    outline = Color(0xFF48484A),
    outlineVariant = DarkPalette.separator,
    scrim = Color.Black,
)

/** Sizes follow iOS text styles: large title 34, title 22, headline 17, body 17, footnote 13. */
private val SortrTypography = Typography().run {
    copy(
        // No custom letter spacing: Android ignores it for Arabic script, so text measured with
        // it doesn't fit once drawn (numbers broke onto two lines in Arabic).
        displaySmall = displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val SortrShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun SortrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SortrTypography,
            shapes = SortrShapes,
            content = content,
        )
    }
}

/** The current light or dark [Palette]. */
val MaterialTheme.palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Fixed colours for the swipe decisions, the same in light and dark themes. */
object SwipeColors {
    val Keep = Color(0xFF34C759)
    val Delete = Color(0xFFFF3B30)
    val Private = Color(0xFF5856D6)
    val Skip = Color(0xFF8E8E93)
}
