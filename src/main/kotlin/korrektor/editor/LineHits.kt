package korrektor.editor

import korrektor.format.Line
import korrektor.viewer.PagePoint
import kotlin.math.roundToInt

/**
 * The line whose box holds [point], or null. Where boxes overlap (a stray word between two lines, neighbouring
 * lines) the smallest box wins, and the first of equal ones; a line without a box is never hit.
 */
fun lineAt(
    point: PagePoint,
    lines: List<Line>,
): Line? =
    lines
        .filter { line ->
            line.box?.let { point.x in it.x..(it.x + it.width) && point.y in it.y..(it.y + it.height) } ==
                true
        }.minByOrNull { line -> line.box!!.let { it.width * it.height } }

/**
 * The character of [line] that is nearest to the page position [x]: the horizontal position in the line's box as a
 * fraction of the text length, rounded. A good guess for text of one font, and the user moves the cursor from there.
 */
fun cursorIndexAt(
    line: Line,
    x: Double,
): Int {
    val box = line.box ?: return 0
    val length = line.text.length
    return if (box.width <= 0.0) 0 else (((x - box.x) / box.width) * length).roundToInt().coerceIn(0, length)
}

/**
 * A click on the scan at [point] while the viewer is on [viewerPage]: if it is on a line, that line becomes the active
 * one and the cursor goes into its row at the clicked place. A click on no line, or while the editor has not got the
 * viewer's page yet, does nothing.
 */
fun BookEditor.clickOnScan(
    viewerPage: Int,
    point: PagePoint,
) {
    if (page != viewerPage || layer == null) return
    val line = lineAt(point, lines) ?: return
    requestFocus(line.id, cursorIndexAt(line, point.x))
}
