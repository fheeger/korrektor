package korrektor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class KorrektorThemeTest {
    private val colors = KorrektorColors

    /** The WCAG contrast ratio of two colours: 1 for equal ones, 21 for black on white. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val lighter = maxOf(a.luminance(), b.luminance()).toDouble()
        val darker = minOf(a.luminance(), b.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun hueDegrees(color: Color): Float {
        val hsb =
            java.awt.Color.RGBtoHSB(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
                null,
            )
        return hsb[0] * 360f
    }

    @Test
    fun everyTextColourHasAContrastOfAtLeast4Point5OnItsGround() {
        val pairs =
            mapOf(
                "onPrimary on primary" to (colors.onPrimary to colors.primary),
                "onPrimaryContainer on primaryContainer" to (colors.onPrimaryContainer to colors.primaryContainer),
                "onSecondary on secondary" to (colors.onSecondary to colors.secondary),
                "onSecondaryContainer on secondaryContainer" to
                    (colors.onSecondaryContainer to colors.secondaryContainer),
                "onTertiary on tertiary" to (colors.onTertiary to colors.tertiary),
                "onTertiaryContainer on tertiaryContainer" to (colors.onTertiaryContainer to colors.tertiaryContainer),
                "onBackground on background" to (colors.onBackground to colors.background),
                "onSurface on surface" to (colors.onSurface to colors.surface),
                "onSurface on surfaceContainerHigh (dialogs)" to (colors.onSurface to colors.surfaceContainerHigh),
                "onSurface on surfaceContainerHighest" to (colors.onSurface to colors.surfaceContainerHighest),
                "onSurfaceVariant on surfaceVariant" to (colors.onSurfaceVariant to colors.surfaceVariant),
                "onSurfaceVariant on surface (labels)" to (colors.onSurfaceVariant to colors.surface),
                "primary on surface (text buttons)" to (colors.primary to colors.surface),
                "primary on surfaceContainerHigh (buttons in dialogs)" to
                    (colors.primary to colors.surfaceContainerHigh),
                "error on surface (error text)" to (colors.error to colors.surface),
                "error on surfaceContainerHigh (dialogs)" to (colors.error to colors.surfaceContainerHigh),
                "onError on error" to (colors.onError to colors.error),
                "onErrorContainer on errorContainer" to (colors.onErrorContainer to colors.errorContainer),
                "inverseOnSurface on inverseSurface" to (colors.inverseOnSurface to colors.inverseSurface),
            )
        val tooLow =
            pairs
                .filter { (_, pair) ->
                    contrast(pair.first, pair.second) < 4.5
                }.map { (name, pair) ->
                    "$name: %.2f".format(contrast(pair.first, pair.second))
                }
        assertTrue(tooLow.isEmpty(), "contrast below 4.5: $tooLow")
    }

    @Test
    fun thePrimaryColourIsABrownAndTheAccentsAreYellowish() {
        // Brown: an orange hue (15 to 45 degrees), dark. Mustard and yellow: a yellow hue (35 to 60 degrees).
        assertTrue(hueDegrees(colors.primary) in 15f..45f, "primary hue ${hueDegrees(colors.primary)}")
        assertTrue(colors.primary.luminance() < 0.15f, "a dark brown")
        assertTrue(hueDegrees(colors.secondary) in 35f..60f, "secondary hue ${hueDegrees(colors.secondary)}")
        assertTrue(
            hueDegrees(colors.secondaryContainer) in 35f..60f,
            "secondaryContainer hue ${hueDegrees(colors.secondaryContainer)}",
        )
    }

    @Test
    fun theGroundIsALightWarmColourNotAWhiteOrAViolet() {
        val ground = colors.background
        assertTrue(ground.luminance() > 0.9f, "light")
        assertTrue(ground.red >= ground.green && ground.green > ground.blue, "warm: red over green over blue")
        assertTrue(colors.surface == colors.background)
    }

    @Test
    fun theErrorColoursStayRed() {
        assertTrue(hueDegrees(colors.error).let { it < 10f || it > 350f }, "error hue ${hueDegrees(colors.error)}")
        assertTrue(colors.errorContainer.red > colors.errorContainer.blue)
    }

    @Test
    fun noNeutralOrContainerColourIsViolet() {
        // Material's default scheme is violet: hue about 260 to 300 degrees. None of ours may be.
        val all =
            listOf(
                colors.primary,
                colors.primaryContainer,
                colors.secondary,
                colors.secondaryContainer,
                colors.tertiary,
                colors.tertiaryContainer,
                colors.surface,
                colors.surfaceVariant,
                colors.surfaceContainer,
                colors.surfaceContainerHigh,
                colors.surfaceContainerHighest,
                colors.outline,
                colors.inversePrimary,
                colors.surfaceTint,
            )
        val violet = all.filter { hueDegrees(it) in 240f..320f && it.luminance() < 0.9f }
        assertTrue(violet.isEmpty(), "violet colours left: $violet")
    }
}
