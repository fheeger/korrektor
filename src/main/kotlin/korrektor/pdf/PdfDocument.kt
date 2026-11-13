package korrektor.pdf

import com.sun.jna.Pointer
import korrektor.format.reason
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * An open PDF. Close it (`use`) when done: that also closes its pages. Page numbers are 0-based here,
 * as in PDFium. All calls are safe to make from several threads; they run one after the other.
 */
class PdfDocument private constructor(
    val path: Path,
    private var handle: Pointer?,
) : AutoCloseable {
    private val openPages = mutableListOf<PdfPage>()

    val pageCount: Int = Pdfium.call { PdfiumLib.getPageCount(live()) }

    /** Loads page [index] (0-based). The caller closes it, or closing the document does. */
    fun page(index: Int): PdfPage {
        require(index in 0 until pageCount) { "Page $index does not exist; the PDF has $pageCount pages" }
        return Pdfium.call {
            val page =
                PdfiumLib.loadPage(live(), index)
                    ?: throw PdfException("Page ${index + 1} of $path cannot be loaded")
            PdfPage(this, page, index).also { openPages += it }
        }
    }

    internal fun forget(page: PdfPage) {
        openPages -= page
    }

    internal fun live(): Pointer = handle ?: error("The PDF $path is closed")

    override fun close() {
        Pdfium.call {
            val open = handle ?: return@call
            openPages.toList().forEach { it.close() }
            handle = null
            PdfiumLib.closeDocument(open)
        }
    }

    companion object {
        /** Opens [path]. Throws a [PdfException] that says why if it is missing, not a PDF, damaged or protected. */
        fun open(
            path: Path,
            password: String? = null,
        ): PdfDocument {
            if (!Files.isRegularFile(path)) throw PdfException("The file $path does not exist")
            val handle =
                Pdfium.call {
                    val utf8Path = path.toAbsolutePath().toString().toByteArray(Charsets.UTF_8) + 0
                    val utf8Password = password?.toByteArray(Charsets.UTF_8)?.plus(0)
                    PdfiumLib.loadDocument(utf8Path, utf8Password) ?: throw failure(path, password != null)
                }
            return PdfDocument(path, handle)
        }

        /**
         * Why PDFium could not open [path]. PDFium has an error code for this, but on Windows it is kept in a
         * per-thread slot that the calls in between overwrite, so the file itself is looked at instead.
         */
        private fun failure(
            path: Path,
            passwordGiven: Boolean,
        ): PdfException {
            val reason =
                try {
                    when {
                        !containsMarker(path, PDF_MARKER, HEADER_BYTES) -> "$path is not a PDF file"
                        containsMarker(path, ENCRYPT_KEY, Long.MAX_VALUE) ->
                            if (passwordGiven) "The password for $path is wrong" else "$path needs a password"
                        else -> "$path is damaged, or uses something that is not supported"
                    }
                } catch (e: IOException) {
                    "$path cannot be read: ${e.reason()}"
                }
            return PdfException(reason)
        }

        /** Whether [marker] occurs in the first [limit] bytes of [path], read in blocks. */
        private fun containsMarker(
            path: Path,
            marker: String,
            limit: Long,
        ): Boolean {
            val needle = marker.toByteArray(Charsets.ISO_8859_1)
            Files.newInputStream(path).use { input ->
                val block = ByteArray(BLOCK_BYTES)
                var filled = 0 // valid bytes in the block: the kept tail of the last block, then new ones
                var total = 0L
                var found = false
                var atEnd = false
                while (!found && !atEnd && total < limit) {
                    val read = input.read(block, filled, minOf((block.size - filled).toLong(), limit - total).toInt())
                    if (read < 0) {
                        atEnd = true
                    } else {
                        total += read
                        filled += read
                        found = indexOf(block, filled, needle) >= 0
                        // Keep the end, in case the marker is cut in two by the block boundary.
                        val keep = minOf(needle.size - 1, filled)
                        block.copyInto(block, 0, filled - keep, filled)
                        filled = keep
                    }
                }
                return found
            }
        }

        private fun indexOf(
            block: ByteArray,
            length: Int,
            needle: ByteArray,
        ): Int =
            (0..length - needle.size).firstOrNull { start -> needle.indices.all { block[start + it] == needle[it] } }
                ?: -1

        private const val PDF_MARKER = "%PDF-"
        private const val ENCRYPT_KEY = "/Encrypt"
        private const val HEADER_BYTES = 1024L
        private const val BLOCK_BYTES = 64 * 1024
    }
}
