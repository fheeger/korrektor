package korrektor.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The boxes of [page], read in the background once the overlay is switched on; none while they are not there. */
@Composable
internal fun rememberBoxes(
    source: PageBoxSource,
    page: Int,
    state: ViewerState,
): PageBoxes {
    var boxes by remember(source, page) { mutableStateOf(PageBoxes.NONE) }
    LaunchedEffect(source, page, state.showWordBoxes) {
        // A page whose boxes cannot be read just has none: the overlay is an aid, not part of the page.
        if (state.showWordBoxes) boxes = tryOrFail({}) { source.boxes(page) } ?: PageBoxes.NONE
    }
    return boxes
}

/**
 * Draws a thin outline around every word and a stronger one around every line that is in the viewport, with the same
 * transform as the picture, so that they stay on the words while zooming and moving. The description tells how
 * many are drawn (and is what the tests read).
 */
@Composable
internal fun BoxOverlay(
    boxes: PageBoxes,
    state: ViewerState,
) {
    val lines = state.visibleBoxes(boxes.lines)
    val words = state.visibleBoxes(boxes.words)
    val lineColor = MaterialTheme.colorScheme.error
    val wordColor = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Word boxes: ${words.size}, line boxes: ${lines.size}" },
    ) {
        for (rect in words) drawOutline(rect, wordColor, WORD_STROKE)
        for (rect in lines) drawOutline(rect, lineColor, LINE_STROKE)
    }
}

private const val WORD_STROKE = 1f
private const val LINE_STROKE = 2f

private fun DrawScope.drawOutline(
    rect: ScreenRect,
    color: Color,
    width: Float,
) = drawRect(
    color,
    Offset(rect.x.toFloat(), rect.y.toFloat()),
    Size(rect.width.toFloat(), rect.height.toFloat()),
    style = Stroke(width),
)
