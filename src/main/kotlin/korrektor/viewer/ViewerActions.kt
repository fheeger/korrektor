package korrektor.viewer

import korrektor.format.Box
import kotlin.math.pow

/** Zooms by [factor] around an anchor (the wheel): the page point under the pointer stays under it. */
fun ViewerState.zoomBy(
    factor: Double,
    anchorX: Double,
    anchorY: Double,
) = zoomTo(scale * factor, anchorX, anchorY)

/**
 * One turn of the mouse wheel at ([x], [y]), as the scroll delta Compose reports it in notches: [deltaY] is positive
 * for down, [deltaX] positive for right (Compose turns Shift+wheel into a sideways delta, and a tilt wheel or a
 * touchpad swipe gives one too). With Ctrl it zooms by about 10 % per notch of [deltaY] around the pointer, up for
 * negative notches; otherwise it scrolls the page by [WHEEL_SCROLL_PIXELS] per notch.
 */
fun ViewerState.wheel(
    ctrl: Boolean,
    x: Double,
    y: Double,
    deltaX: Double = 0.0,
    deltaY: Double = 0.0,
) {
    if (ctrl) {
        zoomBy(WHEEL_ZOOM_FACTOR.pow(-deltaY), x, y)
    } else {
        scrollBy(
            deltaX * WHEEL_SCROLL_PIXELS,
            deltaY * WHEEL_SCROLL_PIXELS,
        )
    }
}

fun ViewerState.fitWidth() = useMode(ZoomMode.FIT_WIDTH)

fun ViewerState.fitPage() = useMode(ZoomMode.FIT_PAGE)

/** Whether there is a page before the current one. */
val ViewerState.hasPrevious: Boolean get() = page > 1

/** Whether there is a page after the current one. */
val ViewerState.hasNext: Boolean get() = page < pageCount

fun ViewerState.previous() = goTo(page - 1)

fun ViewerState.next() = goTo(page + 1)

/** Asks the page field to take the focus and select its content (Ctrl+G). */
fun ViewerState.focusPageField() {
    pageFieldRequests++
}

/** The page number in [text] (whole numbers from 1 to the page count, spaces around are fine), or null. */
fun ViewerState.pageOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }

/** One notch of the Ctrl+wheel zooms by this factor; one notch of the plain wheel scrolls this many pixels. */
private const val WHEEL_ZOOM_FACTOR = 1.1
private const val WHEEL_SCROLL_PIXELS = 80.0

/**
 * Scrolls the least that brings [box] (in points of the page) fully into the viewport, with a margin of a fifth of
 * the viewport on the side it came from, so that a line is not left right at the edge. A box that is already in
 * view changes nothing (sideways it is enough that part of it is); a box larger than the viewport is aligned at its
 * top left; in a direction in which the page is smaller than the viewport (it is centred) nothing moves. The scroll
 * stops at the edges of the page.
 */
fun ViewerState.reveal(box: Box) {
    if (viewportWidth <= 0 || viewportHeight <= 0 || pageSize == null) return
    val rect = toScreen(box)
    // Sideways a box that is partly in view stays: a line is often wider than the panel when zoomed in, and a
    // click on the visible part of it must not make the scan jump to its beginning.
    val partlyInView = rect.x < viewportWidth && rect.x + rect.width > 0
    val dx = if (contentWidth > viewportWidth && !partlyInView) shiftInto(rect.x, rect.width, viewportWidth) else 0.0
    val dy = if (contentHeight > viewportHeight) shiftInto(rect.y, rect.height, viewportHeight) else 0.0
    if (dx != 0.0 || dy != 0.0) scrollBy(dx, dy)
}

/** How far the view must move so that the span from [start] of [length] lies in 0..[viewport], with a margin. */
private fun shiftInto(
    start: Double,
    length: Double,
    viewport: Int,
): Double {
    val margin = viewport / REVEAL_MARGIN_DIVISOR
    return when {
        start >= 0 && start + length <= viewport -> 0.0 // already in view
        length > viewport - 2 * margin -> start // too long to keep margins: its start at the edge
        start < 0 -> start - margin
        else -> start + length - viewport + margin
    }
}

/** The margin kept around a revealed box is this fraction (one over) of the viewport. */
private const val REVEAL_MARGIN_DIVISOR = 5
