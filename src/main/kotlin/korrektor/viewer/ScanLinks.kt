package korrektor.viewer

/**
 * How the scan and the text are linked (S05, S06): the line to mark on the scan, and what a click on the scan does. A
 * click is a press and release without moving beyond the touch slop (dragging pans the page), and gives the page point
 * that was clicked, in points. [onGoToPage] is told the page the user typed in the page field (a number that is a page
 * of the book) and shows it; without it the viewer goes there itself. [onLeavePageField] is called when Escape is
 * pressed in the page field: the focus is to go back to the text.
 */
data class ScanLinks(
    val highlight: LineHighlight? = null,
    val onClick: (PagePoint) -> Unit = {},
    val onGoToPage: ((Int) -> Unit)? = null,
    val onLeavePageField: () -> Unit = {},
)
