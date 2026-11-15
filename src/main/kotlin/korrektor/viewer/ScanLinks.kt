package korrektor.viewer

/**
 * How the scan and the text are linked (S05): the line to mark on the scan, and what a click on the scan does. A click
 * is a press and release without moving beyond the touch slop (dragging pans the page), and gives the page point that
 * was clicked, in points.
 */
data class ScanLinks(
    val highlight: LineHighlight? = null,
    val onClick: (PagePoint) -> Unit = {},
)
