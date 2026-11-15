package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import korrektor.settings.AppSettings
import kotlin.math.roundToInt

/**
 * How the window of an open project is divided: the position of the divider between the scan and the text, as the
 * fraction of the width that the scan gets. It is an app setting, not a project setting: every project uses the same
 * position. A saved value that is missing, not a number or out of range means the default.
 */
class LayoutState(
    private val settings: AppSettings,
) {
    var splitFraction: Float by mutableFloatStateOf(initial(settings))
        private set

    /** Moves the divider; it cannot leave [MIN_SPLIT]..[MAX_SPLIT]. */
    fun moveDivider(fraction: Float) {
        splitFraction = fraction.coerceIn(MIN_SPLIT, MAX_SPLIT)
    }

    /** Writes the position to the settings (when a drag ends), to four decimals: a float's tail has no meaning. */
    fun save() = settings.rememberSplitFraction((splitFraction * ROUNDING).roundToInt() / ROUNDING.toDouble())

    companion object {
        const val DEFAULT_SPLIT = 0.55f
        const val MIN_SPLIT = 0.2f
        const val MAX_SPLIT = 0.8f
        private const val ROUNDING = 10_000

        private fun initial(settings: AppSettings): Float =
            settings.splitFraction()?.toFloat()?.takeIf { it in MIN_SPLIT..MAX_SPLIT } ?: DEFAULT_SPLIT
    }
}
