package korrektor.importer

import korrektor.format.Box
import korrektor.format.CanonicalJson
import korrektor.pdf.PdfPage
import korrektor.pdf.PdfRect
import kotlin.math.abs
import kotlin.math.min

/**
 * The page as the user sees it, and the way from PDF coordinates to the coordinates of the project format
 * (`docs/project-format.md`, Coordinates): points, origin at the top-left corner of the **visible page**
 * (the crop box within the media box, after the page's rotation), x to the right, y downwards.
 *
 * [bounds] is the visible area in the page's own, unrotated coordinates (origin bottom-left), and [rotation]
 * the page's rotation clockwise in degrees: 0, 90, 180 or 270. Both come from the [PdfPage].
 */
class PageGeometry(
    private val bounds: PdfRect,
    private val rotation: Int,
) {
    init {
        require(rotation in ROTATIONS) { "A page is rotated by 0, 90, 180 or 270 degrees, not $rotation" }
        require(bounds.width > 0 && bounds.height > 0) { "The visible area of the page is empty: $bounds" }
    }

    private val turned = rotation == QUARTER || rotation == THREE_QUARTERS

    /** The width of the visible page in points, with one decimal. */
    val width: Double = CanonicalJson.round1(if (turned) bounds.height else bounds.width)

    /** The height of the visible page in points, with one decimal. */
    val height: Double = CanonicalJson.round1(if (turned) bounds.width else bounds.height)

    /**
     * The box of a rectangle given in PDF coordinates, in the coordinates of the format, every value with one
     * decimal (the model does not round, the codec does, so a box with more decimals would change when saved).
     * The box is not clamped to the page.
     */
    fun toFormat(rect: PdfRect): Box {
        val (u1, v1) = display(rect.left, rect.bottom)
        val (u2, v2) = display(rect.right, rect.top)
        return Box(
            CanonicalJson.round1(min(u1, u2)),
            CanonicalJson.round1(min(v1, v2)),
            CanonicalJson.round1(abs(u2 - u1)),
            CanonicalJson.round1(abs(v2 - v1)),
        )
    }

    /** A point of the page in PDF coordinates, as (x to the right, y downwards) of the displayed page. */
    private fun display(
        x: Double,
        y: Double,
    ): Pair<Double, Double> {
        val px = x - bounds.left
        val py = y - bounds.bottom
        return when (rotation) {
            0 -> px to bounds.height - py
            QUARTER -> py to px
            HALF -> bounds.width - px to py
            else -> bounds.height - py to bounds.width - px
        }
    }

    companion object {
        private const val QUARTER = 90
        private const val HALF = 180
        private const val THREE_QUARTERS = 270
        private val ROTATIONS = setOf(0, QUARTER, HALF, THREE_QUARTERS)

        fun of(page: PdfPage) = PageGeometry(page.bounds, page.rotation)
    }
}
