package korrektor.pdf

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

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
private const val WHITE = 0xFFFFFFFFL
private const val BYTES_PER_PIXEL = 4
private const val RECT_FLOATS = 4
private const val RECT_LEFT = 0L
private const val RECT_TOP = 4L
private const val RECT_RIGHT = 8L
private const val RECT_BOTTOM = 12L

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
     * following the page's proportions. A first version: S04 makes it fast for the page viewer.
     */
    fun render(widthPixels: Int): BufferedImage {
        require(widthPixels in 1..MAX_RENDER_WIDTH) { "The width must be between 1 and $MAX_RENDER_WIDTH pixels" }
        val heightPixels = max(1, (widthPixels * height / width).roundToInt())
        return Pdfium.call {
            val page = live()
            val bitmap =
                PdfiumLib.createBitmap(widthPixels, heightPixels, 0)
                    ?: throw PdfException("An image of $widthPixels x $heightPixels pixels cannot be created")
            try {
                PdfiumLib.fillRect(bitmap, 0, 0, widthPixels, heightPixels, NativeLong(WHITE))
                PdfiumLib.renderPage(bitmap, page, 0, 0, widthPixels, heightPixels, 0, 0)
                copyToImage(bitmap, widthPixels, heightPixels)
            } finally {
                PdfiumLib.destroyBitmap(bitmap)
            }
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
    }
}
