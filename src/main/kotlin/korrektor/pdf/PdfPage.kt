package korrektor.pdf

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rectangle in PDF coordinates: points, origin at the bottom-left of the page, y upwards. */
data class PdfRect(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = top - bottom
}

private const val MAX_RENDER_WIDTH = 20_000

/** One image is at most this many pixels (about 270 MB as a BufferedImage with 4 bytes per pixel and a copy). */
private const val MAX_BITMAP_PIXELS = 36_000_000L

/** A part of a rendered page, in pixels: its top-left corner ([x], [y]) in the whole page and its size. */
data class PixelRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** The whole page at the chosen scale is at most this many pixels on a side (PDFium's coordinates are 32 bit). */
private const val MAX_FULL_SIZE = 1_000_000
private const val WHITE = 0xFFFFFFFFL
private const val BYTES_PER_PIXEL = 4
private const val RECT_FLOATS = 4
private const val RECT_LEFT = 0L
private const val RECT_TOP = 4L
private const val RECT_RIGHT = 8L
private const val RECT_BOTTOM = 12L
private const val BOUNDS_LEFT = 0L
private const val BOUNDS_BOTTOM = 4L
private const val BOUNDS_RIGHT = 8L
private const val BOUNDS_TOP = 12L

/** One page of a [PdfDocument]. Close it when done: that also closes its text page. */
class PdfPage internal constructor(
    private val document: PdfDocument,
    private var handle: Pointer?,
    /** The 0-based index of the page in the document. */
    val index: Int,
) : AutoCloseable {
    private var openText: PdfTextPage? = null

    /** The size of the page as it is shown, in points, after the page's rotation. */
    val width: Double
    val height: Double

    /** The rotation of the page in degrees clockwise: 0, 90, 180 or 270. */
    val rotation: Int

    /**
     * The visible area of the page in its own, unrotated coordinates: the crop box within the media box.
     * Boxes of characters are in the same coordinates.
     */
    val bounds: PdfRect

    init {
        val page = live()
        width = Pdfium.call { PdfiumLib.getPageWidth(page).toDouble() }
        height = Pdfium.call { PdfiumLib.getPageHeight(page).toDouble() }
        rotation = Pdfium.call { PdfiumLib.getPageRotation(page).coerceAtLeast(0) * QUARTER_TURN }
        bounds =
            Pdfium.call {
                Memory((RECT_FLOATS * Float.SIZE_BYTES).toLong()).use { rect ->
                    if (PdfiumLib.getPageBoundingBox(page, rect) == 0) {
                        throw PdfException("The size of page ${index + 1} cannot be read")
                    }
                    // FS_RECTF is left, top, right, bottom, with top above bottom.
                    PdfRect(
                        rect.getFloat(RECT_LEFT).toDouble(),
                        rect.getFloat(RECT_BOTTOM).toDouble(),
                        rect.getFloat(RECT_RIGHT).toDouble(),
                        rect.getFloat(RECT_TOP).toDouble(),
                    )
                }
            }
    }

    internal fun live(): Pointer = handle ?: error("Page ${index + 1} is closed")

    /** The text layer of the page. Closing the page closes it; asking again returns the same object. */
    fun text(): PdfTextPage =
        Pdfium.call {
            openText ?: run {
                val text =
                    PdfiumLib.loadTextPage(live())
                        ?: throw PdfException("The text of page ${index + 1} cannot be read")
                PdfTextPage(this, text, index).also { openText = it }
            }
        }

    internal fun forgetText() {
        openText = null
    }

    /**
     * The page as an image of [widthPixels] pixels width, black text on a white background, with the height
     * following the page's proportions.
     */
    fun render(widthPixels: Int): BufferedImage {
        require(widthPixels in 1..MAX_RENDER_WIDTH) { "The width must be between 1 and $MAX_RENDER_WIDTH pixels" }
        val heightPixels = max(1, (widthPixels * height / width).roundToInt())
        return renderInto(PixelRegion(0, 0, widthPixels, heightPixels), widthPixels, heightPixels)
    }

    /**
     * How many pixels of the page's largest picture (the scan) lie on one point of the page, or null if the page
     * has no picture. The square root of the pixel area over the point area, so it does not matter whether the
     * picture is turned. One screen pixel per scan pixel is a scale of this many screen pixels per point, and
     * zooming in beyond it shows nothing new. Only pictures drawn directly on the page are looked at.
     */
    fun largestImageDensity(): Double? =
        Pdfium.call {
            val page = live()
            (0 until PdfiumLib.countObjects(page))
                .mapNotNull { PdfiumLib.getObject(page, it) }
                .filter { PdfiumLib.objectType(it) == IMAGE_OBJECT }
                .mapNotNull { imageDensity(it) }
                .maxByOrNull { it.pixels }
                ?.density
        }

    private class ImageDensity(
        val pixels: Long,
        val density: Double,
    )

    private fun imageDensity(image: Pointer): ImageDensity? =
        Memory((RECT_FLOATS * Float.SIZE_BYTES).toLong()).use { bounds ->
            Memory((2 * Int.SIZE_BYTES).toLong()).use { size ->
                val sizeOk = PdfiumLib.imagePixelSize(image, size, size.share(Int.SIZE_BYTES.toLong())) != 0
                // The four numbers are written one after the other: left, bottom, right, top.
                val boundsOk =
                    PdfiumLib.objectBounds(
                        image,
                        bounds.share(BOUNDS_LEFT),
                        bounds.share(BOUNDS_BOTTOM),
                        bounds.share(BOUNDS_RIGHT),
                        bounds.share(BOUNDS_TOP),
                    ) != 0
                if (!sizeOk || !boundsOk) return@use null
                val pixels = size.getInt(0).toLong() * size.getInt(Int.SIZE_BYTES.toLong()).toLong()
                val width = (bounds.getFloat(BOUNDS_RIGHT) - bounds.getFloat(BOUNDS_LEFT)).toDouble()
                val height = (bounds.getFloat(BOUNDS_TOP) - bounds.getFloat(BOUNDS_BOTTOM)).toDouble()
                if (pixels <= 0 ||
                    width <= 0.0 ||
                    height <= 0.0
                ) {
                    null
                } else {
                    ImageDensity(pixels, sqrt(pixels / (width * height)))
                }
            }
        }

    /**
     * A part of the page as an image. The whole page, as the user sees it (rotation and crop box applied), is
     * [width] x [height] points times [scale] pixels, and the image shows [region] of it. Only the region is
     * allocated and drawn, so zooming far in does not need the memory of the whole page. Parts outside the page are
     * white.
     *
     * @throws IllegalArgumentException if the scale or the region's size is not positive, or the image or the whole
     *   page is too large.
     */
    fun renderRegion(
        scale: Double,
        region: PixelRegion,
    ): BufferedImage {
        require(scale > 0 && scale.isFinite()) { "The scale must be positive, not $scale" }
        require(
            region.width > 0 && region.height > 0,
        ) { "The image must have a size, not ${region.width} x ${region.height}" }
        require(region.width.toLong() * region.height <= MAX_BITMAP_PIXELS) {
            "An image of ${region.width} x ${region.height} pixels is too large (at most $MAX_BITMAP_PIXELS pixels)"
        }
        val fullWidth = max(1, (width * scale).roundToInt())
        val fullHeight = max(1, (height * scale).roundToInt())
        require(fullWidth <= MAX_FULL_SIZE && fullHeight <= MAX_FULL_SIZE) {
            "The page would be $fullWidth x $fullHeight pixels; at most $MAX_FULL_SIZE are possible"
        }
        return renderInto(region, fullWidth, fullHeight)
    }

    /** Draws the page, scaled to [fullWidth] x [fullHeight], so that [region] of it fills a new bitmap. */
    private fun renderInto(
        region: PixelRegion,
        fullWidth: Int,
        fullHeight: Int,
    ): BufferedImage =
        Pdfium.call {
            val page = live()
            val bitmap =
                PdfiumLib.createBitmap(region.width, region.height, 0)
                    ?: throw PdfException("An image of ${region.width} x ${region.height} pixels cannot be created")
            try {
                PdfiumLib.fillRect(bitmap, 0, 0, region.width, region.height, NativeLong(WHITE))
                PdfiumLib.renderPage(bitmap, page, -region.x, -region.y, fullWidth, fullHeight, 0, 0)
                copyToImage(bitmap, region.width, region.height)
            } finally {
                PdfiumLib.destroyBitmap(bitmap)
            }
        }

    private fun copyToImage(
        bitmap: Pointer,
        widthPixels: Int,
        heightPixels: Int,
    ): BufferedImage {
        val buffer = PdfiumLib.bitmapBuffer(bitmap) ?: throw PdfException("The image of page ${index + 1} is empty")
        val stride = PdfiumLib.bitmapStride(bitmap)
        check(stride >= widthPixels * BYTES_PER_PIXEL) { "Unexpected image layout" }
        val image = BufferedImage(widthPixels, heightPixels, BufferedImage.TYPE_INT_RGB)
        val row = IntArray(widthPixels)
        for (y in 0 until heightPixels) {
            // PDFium's bytes are blue, green, red, unused: read as little-endian integers that is 0x00RRGGBB.
            buffer.read(y.toLong() * stride, row, 0, widthPixels)
            image.setRGB(0, y, widthPixels, 1, row, 0, widthPixels)
        }
        return image
    }

    override fun close() {
        Pdfium.call {
            val open = handle ?: return@call
            openText?.close()
            handle = null
            PdfiumLib.closePage(open)
            document.forget(this)
        }
    }

    private companion object {
        const val QUARTER_TURN = 90
        const val IMAGE_OBJECT = 3 // FPDF_PAGEOBJ_IMAGE
    }
}
