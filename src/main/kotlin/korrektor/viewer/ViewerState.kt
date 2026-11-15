package korrektor.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.pdf.PixelRegion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** How the scale of the viewer is chosen. */
enum class ZoomMode {
    /** The page is as wide as the viewport. Follows the viewport and the page. */
    FIT_WIDTH,

    /** The whole page is visible. Follows the viewport and the page. */
    FIT_PAGE,

    /** A scale the user chose with the steps or the wheel; it stays until changed. */
    FIXED,
}

/**
 * What the viewer shows of the current page: the scale (screen pixels per point of the page), the zoom mode, and
 * which part of the zoomed page is in the viewport (`offsetX`, `offsetY`: the page pixel at the viewport's top-left
 * corner). Plain Kotlin on Compose state, no UI, so the arithmetic is tested with exact numbers.
 *
 * A page that is smaller than the viewport in one direction is centred in it and cannot be moved in that direction.
 * At most [maxScale], the scale shows the scan's own pixels: beyond that a zoom would show nothing new.
 */
class ViewerState {
    /** The size of the current page in points, or null until it is known. */
    var pageSize: PageSize? by mutableStateOf(null)
        private set

    /** The viewport in screen pixels. */
    var viewportWidth: Int by mutableIntStateOf(0)
        private set
    var viewportHeight: Int by mutableIntStateOf(0)
        private set

    /** The scale at which one scan pixel is one screen pixel, if the page has a scan (`PageSource.nativeScale`). */
    var nativeScale: Double? by mutableStateOf(null)
        private set

    var mode: ZoomMode by mutableStateOf(ZoomMode.FIT_WIDTH)
        private set
    private var fixedScale: Double by mutableDoubleStateOf(ACTUAL_SIZE)

    /** The page pixel at the left and top edge of the viewport, never negative. */
    var offsetX: Double by mutableDoubleStateOf(0.0)
        private set
    var offsetY: Double by mutableDoubleStateOf(0.0)
        private set

    private val ready: Boolean get() = pageSize != null && viewportWidth > 0 && viewportHeight > 0

    /** Screen pixels per point of the page. */
    val scale: Double
        get() {
            val page = pageSize
            if (!ready || page == null) return fixedScale
            val fitWidth = viewportWidth / page.width
            return when (mode) {
                ZoomMode.FIT_WIDTH -> fitWidth
                ZoomMode.FIT_PAGE -> min(fitWidth, viewportHeight / page.height)
                ZoomMode.FIXED -> fixedScale.coerceIn(minScale, max(maxScale, fixedScale))
            }
        }

    /** The page's size in pixels at the current scale. */
    val contentWidth: Int get() = pageSize?.let { max(1, (it.width * scale).roundToInt()) } ?: 0
    val contentHeight: Int get() = pageSize?.let { max(1, (it.height * scale).roundToInt()) } ?: 0

    /** The screen position of the page's left and top edge: negative when scrolled, positive when centred. */
    val originX: Double get() = origin(contentWidth, viewportWidth, offsetX)
    val originY: Double get() = origin(contentHeight, viewportHeight, offsetY)

    /** The largest scale the user can zoom to: the scan's own resolution, but at least actual size. */
    val maxScale: Double get() = max(nativeScale ?: (MAX_ZOOM_WITHOUT_SCAN * ACTUAL_SIZE), ACTUAL_SIZE)

    /** The smallest scale: a tenth of the scale at which the whole page is visible. */
    val minScale: Double
        get() {
            val page = pageSize
            if (!ready || page == null) return MIN_FLOOR
            return FLOOR_OF_FIT_PAGE * min(viewportWidth / page.width, viewportHeight / page.height)
        }

    /** The zoom in percent of actual size (1 point = 1/72 inch on a 96 dpi screen), for the toolbar. */
    val percent: Int get() = (scale / ACTUAL_SIZE * PERCENT).roundToInt()

    /** The part of the zoomed page that is in the viewport, or null while page or viewport are not known. */
    val visibleRegion: PixelRegion?
        get() =
            if (!ready) {
                null
            } else {
                PixelRegion(
                    offsetX.roundToInt(),
                    offsetY.roundToInt(),
                    min(viewportWidth, contentWidth),
                    min(viewportHeight, contentHeight),
                )
            }

    /** The viewport changed (the window was resized): a fit mode follows, a fixed scale stays. */
    fun setViewport(
        width: Int,
        height: Int,
    ) {
        val old = scale
        viewportWidth = width
        viewportHeight = height
        keepAnchor(old, 0.0, 0.0)
    }

    /** Another page is shown: its size, and its scan's resolution if it has one. The view starts at the top left. */
    fun setPage(
        size: PageSize,
        native: Double?,
    ) {
        pageSize = size
        nativeScale = native
        offsetX = 0.0
        offsetY = 0.0
    }

    fun fitWidth() = switchMode(ZoomMode.FIT_WIDTH)

    fun fitPage() = switchMode(ZoomMode.FIT_PAGE)

    private fun switchMode(newMode: ZoomMode) {
        val old = scale
        mode = newMode
        keepAnchor(old, 0.0, 0.0)
    }

    /**
     * Zooms to [target] (within the limits), keeping the page point under ([anchorX], [anchorY]) in the viewport
     * where it is; the centre of the viewport by default.
     */
    fun zoomTo(
        target: Double,
        anchorX: Double = viewportWidth / 2.0,
        anchorY: Double = viewportHeight / 2.0,
    ) {
        val old = scale
        mode = ZoomMode.FIXED
        fixedScale = target.coerceIn(minScale, max(maxScale, old))
        keepAnchor(old, anchorX, anchorY)
    }

    /** The next step above the current scale, up to the scan's resolution; nothing if there is none. */
    fun zoomIn() = steps.firstOrNull { it > scale + EPSILON }?.let { zoomTo(it) }

    /** The next step below the current scale; nothing if there is none. */
    fun zoomOut() = steps.lastOrNull { it < scale - EPSILON }?.let { zoomTo(it) }

    /** Moves the view by ([dx], [dy]) page pixels, stopping at the edges of the page. */
    fun scrollBy(
        dx: Double,
        dy: Double,
    ) {
        offsetX = clamp(offsetX + dx, contentWidth, viewportWidth)
        offsetY = clamp(offsetY + dy, contentHeight, viewportHeight)
    }

    /** The scales the buttons move along: the fixed steps below the maximum, and the maximum itself. */
    private val steps: List<Double>
        get() =
            (
                STEPS
                    .map {
                        it * ACTUAL_SIZE
                    }.filter { it < maxScale - EPSILON } + maxScale
            ).filter { it >= minScale - EPSILON }

    /**
     * After the scale changed from [oldScale]: the page point that was at ([anchorX], [anchorY]) in the viewport is
     * there again, as far as the page's edges allow. The page's position is read with the old scale's content size,
     * which is why the old scale is passed in.
     */
    private fun keepAnchor(
        oldScale: Double,
        anchorX: Double,
        anchorY: Double,
    ) {
        val page = pageSize ?: return
        if (!ready) return
        val oldWidth = max(1, (page.width * oldScale).roundToInt())
        val oldHeight = max(1, (page.height * oldScale).roundToInt())
        val pointX = (anchorX - origin(oldWidth, viewportWidth, offsetX)) / oldScale
        val pointY = (anchorY - origin(oldHeight, viewportHeight, offsetY)) / oldScale
        val newScale = scale
        offsetX = clamp(pointX * newScale - anchorX, contentWidth, viewportWidth)
        offsetY = clamp(pointY * newScale - anchorY, contentHeight, viewportHeight)
    }

    companion object {
        /** Screen pixels per point at actual size: 96 dpi on a screen, 72 points per inch. */
        const val ACTUAL_SIZE = 96.0 / 72.0

        /** The zoom steps, as factors of actual size. */
        val STEPS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0)

        /** The limit for a page without a scan, as a factor of actual size. */
        private const val MAX_ZOOM_WITHOUT_SCAN = 8.0
        private const val FLOOR_OF_FIT_PAGE = 0.1
        private const val MIN_FLOOR = 0.01
        private const val PERCENT = 100.0
        private const val EPSILON = 1e-9
    }
}

/** Zooms by [factor] around an anchor (the wheel): the page point under the pointer stays under it. */
fun ViewerState.zoomBy(
    factor: Double,
    anchorX: Double,
    anchorY: Double,
) = zoomTo(scale * factor, anchorX, anchorY)

/**
 * One turn of the mouse wheel by [notches] (positive: down, like a scroll delta) at ([x], [y]): with Ctrl it zooms
 * by about 10 % per notch around the pointer, up for negative notches; without it scrolls the page.
 */
fun ViewerState.wheel(
    notches: Double,
    ctrl: Boolean,
    x: Double,
    y: Double,
) {
    if (ctrl) zoomBy(WHEEL_ZOOM_FACTOR.pow(-notches), x, y) else scrollBy(0.0, notches * WHEEL_SCROLL_PIXELS)
}

/** The screen position of the page's edge: negative when scrolled, positive when the page is centred. */
private fun origin(
    content: Int,
    viewport: Int,
    offset: Double,
): Double = if (content < viewport) (viewport - content) / 2.0 else -offset

private fun clamp(
    offset: Double,
    content: Int,
    viewport: Int,
): Double = if (content <= viewport) 0.0 else offset.coerceIn(0.0, (content - viewport).toDouble())

/** One notch of the Ctrl+wheel zooms by this factor; one notch of the plain wheel scrolls this many pixels. */
private const val WHEEL_ZOOM_FACTOR = 1.1
private const val WHEEL_SCROLL_PIXELS = 80.0
