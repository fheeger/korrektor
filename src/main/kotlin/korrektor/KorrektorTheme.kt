// The hex values are the definition of the colour scheme; giving each one a name would add nothing.
@file:Suppress("MagicNumber")

package korrektor

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The colours of the app: a brown primary with mustard and yellow accents on a warm cream ground, as the user
 * asked for (yellow and brown instead of Material's default violet). Every text colour is paired with its ground
 * for a contrast of at least 4.5 : 1 (checked by a test). The error colours stay red.
 */
val KorrektorColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF6B4423),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF2DDB0),
        onPrimaryContainer = Color(0xFF2B1A06),
        inversePrimary = Color(0xFFE8BF8B),
        secondary = Color(0xFF7A5C00),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFE17C),
        onSecondaryContainer = Color(0xFF271D00),
        tertiary = Color(0xFF7D5A2A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF1DBC0),
        onTertiaryContainer = Color(0xFF2A1A06),
        background = Color(0xFFFFF8E7),
        onBackground = Color(0xFF2A2116),
        surface = Color(0xFFFFF8E7),
        onSurface = Color(0xFF2A2116),
        surfaceVariant = Color(0xFFEADFC8),
        onSurfaceVariant = Color(0xFF4D4333),
        inverseSurface = Color(0xFF3F362A),
        inverseOnSurface = Color(0xFFF8EFDD),
        outline = Color(0xFF807560),
        outlineVariant = Color(0xFFD2C5AC),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        surfaceBright = Color(0xFFFFF8E7),
        surfaceDim = Color(0xFFE0D6BE),
        surfaceContainerLowest = Color(0xFFFFFCF3),
        surfaceContainerLow = Color(0xFFFBF1DC),
        surfaceContainer = Color(0xFFF6ECD4),
        surfaceContainerHigh = Color(0xFFF0E6CD),
        surfaceContainerHighest = Color(0xFFEBE0C7),
    )

/** The Material 3 theme of the app with [KorrektorColors]. */
@Composable
fun KorrektorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KorrektorColors, content = content)
}
