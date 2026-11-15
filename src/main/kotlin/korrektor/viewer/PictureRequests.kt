package korrektor.viewer

import androidx.compose.foundation.v2.ScrollbarAdapter
import korrektor.pdf.PixelRegion
import kotlin.math.max
import kotlin.math.min

/** A picture to ask the source for: [region] of the page at [scale]. A new request replaces the old one. */
data class PictureRequest(
    val scale: Double,
    val region: PixelRegion,
)

/**
 * The picture to ask for next, or null if nothing is visible yet. [have] is the request that was made last: as long
 * as it is for the same scale and still covers everything visible, it is returned unchanged, so panning inside the
 * picture asks for nothing new.
 *
 * A page that has at most [wholePageLimit] pixels at the current scale (fit width on a normal screen, every page at
 * fit page) is requested whole: then scrolling, dragging and the scrollbars never wait for a picture, only a change
 * of the scale does. A larger page (a high zoom) gets the visible part plus a margin of one viewport on every side,
 * as far as the page goes, so that a normal movement stays inside the picture; the margin shrinks if the picture
 * would otherwise be too large for one image.
 */
fun ViewerState.nextRequest(
    have: PictureRequest?,
    wholePageLimit: Long = WHOLE_PAGE_PIXELS,
): PictureRequest? {
    val visible = visibleRegion
    return when {
        visible == null -> null
        have != null && have.scale == scale && have.region.covers(visible) -> have
        contentWidth.toLong() * contentHeight <= wholePageLimit ->
            PictureRequest(scale, PixelRegion(0, 0, contentWidth, contentHeight))
        else -> requestAround(visible)
    }
}

private fun ViewerState.requestAround(visible: PixelRegion): PictureRequest {
    var marginX = viewportWidth
    var marginY = viewportHeight
    // Without margin the request is the visible part itself; that is as small as it gets, and `renderRegion` decides
    // whether one image can hold it. The loop must end there, or a very large screen would never leave it.
    while ((marginX > 0 || marginY > 0) && area(visible, marginX, marginY) > MAX_REQUEST_PIXELS) {
        marginX /= 2
        marginY /= 2
    }
    val left = max(0, visible.x - marginX)
    val top = max(0, visible.y - marginY)
    val right = min(contentWidth, visible.x + visible.width + marginX)
    val bottom = min(contentHeight, visible.y + visible.height + marginY)
    return PictureRequest(scale, PixelRegion(left, top, right - left, bottom - top))
}

private fun area(
    visible: PixelRegion,
    marginX: Int,
    marginY: Int,
): Long = (visible.width + 2L * marginX) * (visible.height + 2L * marginY)

private fun PixelRegion.covers(other: PixelRegion) =
    x <= other.x && y <= other.y && x + width >= other.x + other.width && y + height >= other.y + other.height

/** A request is at most this many pixels (one image can have 36 million, the limit of `PdfPage`). */
private const val MAX_REQUEST_PIXELS = 30_000_000L

/** A page up to this many pixels at the current scale is rendered whole: 64 MB, fit width on a 2000 px screen. */
private const val WHOLE_PAGE_PIXELS = 16_000_000L

/**
 * The bridge between a scrollbar and the position in the page, for one direction: the scrollbar's offset is the
 * state's offset in that direction, the content is the zoomed page, and moving the scrollbar scrolls the page.
 */
class ViewerScrollbarAdapter(
    private val state: ViewerState,
    private val vertical: Boolean,
) : ScrollbarAdapter {
    override val scrollOffset: Double get() = if (vertical) state.offsetY else state.offsetX
    override val contentSize: Double get() = (if (vertical) state.contentHeight else state.contentWidth).toDouble()
    override val viewportSize: Double get() = (if (vertical) state.viewportHeight else state.viewportWidth).toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val delta = scrollOffset - this.scrollOffset
        if (vertical) state.scrollBy(0.0, delta) else state.scrollBy(delta, 0.0)
    }
}
