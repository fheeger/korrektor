package korrektor.pdf

import com.sun.jna.Memory
import com.sun.jna.Pointer

/**
 * One character of a page's text layer as PDFium reports it.
 * [unicode] is PDFium's value: a code point, or half of a surrogate pair for characters outside the BMP
 * (on Windows PDFium reports UTF-16 code units). [generated] is true for characters PDFium inserted itself,
 * such as the space or line break between two pieces of text. [box] is in PDF coordinates (see [PdfRect]).
 */
data class PdfChar(
    val unicode: Int,
    val box: PdfRect,
    val generated: Boolean,
    val hyphen: Boolean,
    val fontSize: Double,
)

private const val DOUBLE_BYTES = 8L
private const val BOX_VALUES = 4

/** The text layer of a [PdfPage]: its characters in the order PDFium gives them. Closing the page closes it. */
class PdfTextPage internal constructor(
    private val page: PdfPage,
    private var handle: Pointer?,
    private val pageIndex: Int,
) : AutoCloseable {
    /** The number of characters, including those PDFium generated. */
    val charCount: Int = Pdfium.call { PdfiumLib.countChars(live()).coerceAtLeast(0) }

    // Room for the four numbers of one box; the calls run under the lock, so one buffer is enough.
    private val boxMemory = Memory(DOUBLE_BYTES * BOX_VALUES)
    private val leftValue = boxMemory.share(0)
    private val rightValue = boxMemory.share(DOUBLE_BYTES)
    private val bottomValue = boxMemory.share(DOUBLE_BYTES * 2)
    private val topValue = boxMemory.share(DOUBLE_BYTES * (BOX_VALUES - 1))

    private fun live(): Pointer = handle ?: error("The text of page ${pageIndex + 1} is closed")

    /** Character [index] (0-based, below [charCount]). */
    fun char(index: Int): PdfChar {
        require(index in 0 until charCount) { "Character $index does not exist; the page has $charCount" }
        return Pdfium.call {
            val text = live()
            if (PdfiumLib.getCharBox(text, index, leftValue, rightValue, bottomValue, topValue) == 0) {
                // PDFium has no box for it (a generated character, for instance): an empty box at the origin.
                listOf(leftValue, rightValue, bottomValue, topValue).forEach { it.setDouble(0, 0.0) }
            }
            PdfChar(
                unicode = PdfiumLib.getUnicode(text, index),
                box =
                    PdfRect(
                        leftValue.getDouble(0),
                        bottomValue.getDouble(0),
                        rightValue.getDouble(0),
                        topValue.getDouble(0),
                    ),
                generated = PdfiumLib.isGenerated(text, index) == 1,
                hyphen = PdfiumLib.isHyphen(text, index) == 1,
                fontSize = PdfiumLib.getFontSize(text, index),
            )
        }
    }

    override fun close() {
        Pdfium.call {
            val open = handle ?: return@call
            handle = null
            PdfiumLib.closeTextPage(open)
            boxMemory.close()
            page.forgetText()
        }
    }
}
