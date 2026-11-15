package korrektor.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import korrektor.format.Box

/** The line to mark on the scan: its id (for the description) and its [box] in the coordinates of the format. */
data class LineHighlight(
    val lineId: String,
    val box: Box,
)

/** The colours of the highlight: a translucent yellow over the scan with a dark amber outline. */
private const val FILL_ARGB = 0x66FFD54F // #FFD54F at 40 % opacity
private const val OUTLINE_ARGB = 0xFFB26A00
private val HIGHLIGHT_FILL = Color(FILL_ARGB)
private val HIGHLIGHT_OUTLINE = Color(OUTLINE_ARGB)
private const val OUTLINE_WIDTH = 2f

/**
 * Draws the [highlight] over the picture with the same transform as the picture (`ViewerState.toScreen`), so that
 * it stays on its line while the user zooms and moves the page. The description says which line it is.
 */
@Composable
internal fun HighlightOverlay(
    highlight: LineHighlight,
    state: ViewerState,
) {
    val rect = state.toScreen(highlight.box)
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Highlighted line: ${highlight.lineId}" }) {
        val topLeft = Offset(rect.x.toFloat(), rect.y.toFloat())
        val size = Size(rect.width.toFloat(), rect.height.toFloat())
        drawRect(HIGHLIGHT_FILL, topLeft, size)
        drawRect(HIGHLIGHT_OUTLINE, topLeft, size, style = Stroke(OUTLINE_WIDTH))
    }
}
