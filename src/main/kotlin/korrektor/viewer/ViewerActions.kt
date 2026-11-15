package korrektor.viewer

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

/** Goes to the page the user typed: whole numbers from 1 to the page count, spaces around are fine. */
fun ViewerState.goToText(text: String): Boolean = text.trim().toIntOrNull()?.let(::goTo) ?: false

/** One notch of the Ctrl+wheel zooms by this factor; one notch of the plain wheel scrolls this many pixels. */
private const val WHEEL_ZOOM_FACTOR = 1.1
private const val WHEEL_SCROLL_PIXELS = 80.0
