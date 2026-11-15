package korrektor.viewer

import kotlin.math.pow

/** Zooms by [factor] around an anchor (the wheel): the page point under the pointer stays under it. */
fun ViewerState.zoomBy(
    factor: Double,
    anchorX: Double,
    anchorY: Double,
) = zoomTo(scale * factor, anchorX, anchorY)

/**
 * One turn of the mouse wheel by [notches] (positive: down, like a scroll delta) at ([x], [y]): with Ctrl it zooms
 * by about 10 % per notch around the pointer, up for negative notches; with Shift it scrolls sideways; otherwise it
 * scrolls the page up and down.
 */
fun ViewerState.wheel(
    notches: Double,
    ctrl: Boolean,
    x: Double,
    y: Double,
    shift: Boolean = false,
) {
    when {
        ctrl -> zoomBy(WHEEL_ZOOM_FACTOR.pow(-notches), x, y)
        shift -> scrollBy(notches * WHEEL_SCROLL_PIXELS, 0.0)
        else -> scrollBy(0.0, notches * WHEEL_SCROLL_PIXELS)
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

/** Goes to the page the user typed: whole numbers from 1 to the page count, spaces around are fine. */
fun ViewerState.goToText(text: String): Boolean = text.trim().toIntOrNull()?.let(::goTo) ?: false

/** One notch of the Ctrl+wheel zooms by this factor; one notch of the plain wheel scrolls this many pixels. */
private const val WHEEL_ZOOM_FACTOR = 1.1
private const val WHEEL_SCROLL_PIXELS = 80.0
