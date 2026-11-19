package korrektor.export

import korrektor.editor.PageEdits
import korrektor.format.AtomicFile
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.reason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

/** An export that could not be done; the message says why, for the user. */
class ExportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Writes the corrected text of a book to a file (G1). It only reads: the project, the editor and the history stay as
 * they are. Reading, rendering and writing all happen on [io], so the UI keeps working.
 *
 * @param readLayer the corrected layer of a page as it is now (`BookEditor.currentLayer`: what the user sees, also
 * what is not saved yet); a seam for tests.
 * @param write replaces the target file as a whole (`AtomicFile.write`); a seam for tests.
 */
class BookExport(
    private val readLayer: (Int) -> PageLayer,
    private val io: CoroutineDispatcher,
    private val write: (Path, ByteArray) -> Unit = AtomicFile::write,
) {
    /**
     * Exports the pages [pages] (positions in the book, from 1) to [target]. [onProgress] gets the number of pages
     * done so far, on the IO thread. Throws [ExportException] if a page cannot be read or the file cannot be written,
     * and `CancellationException` if the coroutine is cancelled: in both cases the file is as it was before, because
     * it is replaced only when all of it is ready. Returns the number of pages exported.
     */
    suspend fun run(
        target: Path,
        metadata: Metadata?,
        pages: IntRange,
        options: ExportOptions,
        onProgress: (Int) -> Unit = {},
    ): Int =
        withContext(io) {
            val job = coroutineContext
            val text = StringBuilder()
            val exportPages =
                pages.asSequence().map { number ->
                    job.ensureActive()
                    exportPage(number).also { onProgress(number - pages.first + 1) }
                }
            renderExport(metadata, exportPages, options, text)
            job.ensureActive()
            try {
                write(target, text.toString().toByteArray(Charsets.UTF_8))
            } catch (e: IOException) {
                throw ExportException("The file could not be written: ${e.reason()}", e)
            }
            pages.count()
        }

    private fun exportPage(number: Int): ExportPage {
        val layer =
            try {
                readLayer(number)
            } catch (e: FormatException) {
                throw ExportException("Page $number could not be read, so nothing was exported: ${e.message}", e)
            } catch (e: IOException) {
                throw ExportException("Page $number could not be read, so nothing was exported: ${e.reason()}", e)
            }
        return ExportPage(layer.pdfPage, PageEdits.readingOrder(layer).map { it.text })
    }
}
