package korrektor.viewer

/** How the zoom was set when the view was saved: a fit mode, or a scale in screen pixels per point. */
data class SavedZoom(
    val mode: ZoomMode,
    /** The scale of a fixed zoom; null for the fit modes. */
    val scale: Double? = null,
)

/** What the app remembers of a project's view between sessions: the page and the zoom. */
data class SavedView(
    val page: Int,
    val zoom: SavedZoom,
)

/** The view as it is now. A fixed zoom is saved as its scale, which does not depend on the size of the window. */
fun ViewerState.saved(): SavedView = SavedView(page, SavedZoom(mode, if (mode == ZoomMode.FIXED) scale else null))

/**
 * Brings the viewer back to a saved view. A page beyond the book (the project changed since) becomes the last page,
 * and one below 1 the first. This works before the viewer knows its window or the page: a fixed scale is kept as it
 * is and limited to what the page allows once that is known.
 */
fun ViewerState.restore(view: SavedView) {
    goTo(view.page.coerceIn(1, pageCount))
    when (view.zoom.mode) {
        ZoomMode.FIT_WIDTH -> fitWidth()
        ZoomMode.FIT_PAGE -> fitPage()
        ZoomMode.FIXED -> view.zoom.scale?.let { zoomTo(it) }
    }
}
