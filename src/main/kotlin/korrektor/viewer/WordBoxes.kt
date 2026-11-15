package korrektor.viewer

import korrektor.format.Box

/** The boxes of one page's text, in the coordinates of the format: points, origin top-left of the visible page. */
class PageBoxes(
    val lines: List<Box>,
    val words: List<Box>,
) {
    companion object {
        val NONE = PageBoxes(emptyList(), emptyList())
    }
}

/** Where the viewer gets the boxes of a page from. A page without text has none. */
fun interface PageBoxSource {
    suspend fun boxes(page: Int): PageBoxes
}

/** A box on the screen, in pixels of the viewer's area. */
data class ScreenRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/**
 * Where [box] of the page is on the screen: the same transform as the picture. A point of the page lies at
 * `origin + point × scale`, with the origin moved by the scrolling, or by the centring of a page smaller than the
 * viewport. The format and the viewer both measure from the top left of the visible page, so nothing else is needed.
 */
fun ViewerState.toScreen(box: Box): ScreenRect =
    ScreenRect(originX + box.x * scale, originY + box.y * scale, box.width * scale, box.height * scale)

/** The boxes that are at least partly in the viewport, on the screen. Boxes outside are not drawn. */
fun ViewerState.visibleBoxes(boxes: List<Box>): List<ScreenRect> =
    boxes
        .map { toScreen(it) }
        .filter { it.x < viewportWidth && it.x + it.width > 0 && it.y < viewportHeight && it.y + it.height > 0 }

/** A point of the page in points, origin top left (the coordinates of the format). */
data class PagePoint(
    val x: Double,
    val y: Double,
)

/** The page point under the screen position ([screenX], [screenY]): the inverse of [toScreen]. */
fun ViewerState.pagePointAt(
    screenX: Double,
    screenY: Double,
): PagePoint = PagePoint((screenX - originX) / scale, (screenY - originY) / scale)
