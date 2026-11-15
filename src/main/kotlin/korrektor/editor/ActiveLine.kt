package korrektor.editor

import korrektor.format.Line
import korrektor.viewer.LineHighlight

/** The line with the cursor on the page shown, or null if there is none. */
val BookEditor.activeLine: Line? get() = lines.firstOrNull { it.id == activeLineId }

/**
 * What the scan should mark for the line with the cursor, when the viewer is on [viewerPage]: its box. Nothing if the
 * editor is not on that page yet (the page was just changed and the text is still being read: the old line must not
 * be drawn over the new picture), if no line is active, or if the line has no box (an added line has no place on the
 * scan).
 */
fun BookEditor.highlightFor(viewerPage: Int): LineHighlight? {
    val line = activeLine
    val box = line?.box
    return if (page == viewerPage && line != null && box != null) LineHighlight(line.id, box) else null
}
