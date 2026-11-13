package korrektor.importer

import korrektor.format.FormatException
import korrektor.format.PageInfo
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.pdf.PdfDocument
import korrektor.pdf.PdfPage
import java.util.concurrent.CancellationException

/** A message about one page of the import, which is also stored on the page (`PageLayer.note`). */
data class PageNote(
    /** The page number in the project, 1-based. */
    val page: Int,
    val text: String,
)

/** What an import did. */
data class ImportResult(
    /** The number of pages imported: one for every page of the PDF. */
    val pageCount: Int,
    /** The pages that could not be imported fully: no text, or a text layer that could not be read. */
    val notes: List<PageNote> = emptyList(),
)

/**
 * Reads the text layer of a project's PDF page by page and writes, for each page, the original OCR layer
 * (written once, never changed) and the corrected layer, which starts as a copy of it (S03, A2).
 *
 * The rules for what is read from the PDF are in `docs/pdf-text-layer.md`. The work is synchronous: the caller runs
 * it on a background thread and uses [run]'s callbacks for progress and cancelling.
 *
 * A page the import cannot get text from does not stop it: the page becomes an empty page with a note.
 */
object PdfImport {
    internal const val NO_TEXT = "No text on this page."

    /** Page size in points (A4) used when a page cannot be loaded and there is no earlier page to take it from. */
    private const val A4_WIDTH = 595.3
    private const val A4_HEIGHT = 841.9

    /**
     * Imports every page of `project.sourcePdf`. First the page list of the project is set to one entry per PDF
     * page, then the pages are written one after the other: [progress] is called with the number of pages done and
     * the total after each page, and [isCancelled] is asked before each page. [openPage] loads a page of the PDF;
     * it is a seam for tests, to make a page fail.
     *
     * @throws CancellationException if [isCancelled] turns true. The pages written so far stay; the caller decides
     *   what to do with the project (S03 deletes it).
     * @throws FormatException if the project has pages already: the original of a page is written only once.
     * @throws korrektor.pdf.PdfException if the PDF cannot be read. The project is not changed then.
     */
    fun run(
        project: Project,
        progress: (done: Int, total: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
        openPage: (PdfDocument, Int) -> PdfPage = { pdf, index -> pdf.page(index) },
    ): ImportResult {
        if (project.pages.isNotEmpty()) {
            throw FormatException("The text of this project has been imported already; the original is never replaced")
        }
        PdfDocument.open(project.sourcePdf).use { pdf ->
            val total = pdf.pageCount
            project.setPages((1..total).map { PageInfo(it) })
            val notes = mutableListOf<PageNote>()
            var size = A4_WIDTH to A4_HEIGHT
            for (number in 1..total) {
                if (isCancelled()) {
                    throw CancellationException("The import was cancelled after ${number - 1} of $total pages")
                }
                val layer = importPage(pdf, number, size, openPage)
                size = layer.width to layer.height
                layer.note?.let { notes += PageNote(number, it) }
                project.writeOriginalPage(number, layer)
                project.writePage(number, startingPoint(layer))
                progress(number, total)
            }
            return ImportResult(total, notes)
        }
    }

    /**
     * The layer of one page as the text layer of the PDF gives it: size, and the lines in one body region. Whatever
     * goes wrong on the page, the result is an empty page with a note, never an exception (cancelling aside).
     */
    private fun importPage(
        pdf: PdfDocument,
        number: Int,
        previousSize: Pair<Double, Double>,
        openPage: (PdfDocument, Int) -> PdfPage,
    ): PageLayer =
        try {
            openPage(pdf, number - 1).use { readPage(it, number) }
        } catch (
            // A page the PDF cannot give us must not stop the import of the others, whatever the reason.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            val reason = "${e.message ?: e::class.simpleName}. The size of the previous page is used."
            emptyPage(number, previousSize, "The text of this page could not be read: $reason")
        }

    private fun readPage(
        page: PdfPage,
        number: Int,
    ): PageLayer {
        val geometry = PageGeometry.of(page)
        val size = geometry.width to geometry.height
        return try {
            val words = WordExtractor.words(page.text()).map { PlacedWord(it.text, geometry.toFormat(it.box)) }
            val region = LineBuilder.build(words)
            if (region ==
                null
            ) {
                emptyPage(number, size, NO_TEXT)
            } else {
                PageLayer(number, size.first, size.second, listOf(region))
            }
        } catch (
            // The page is there, only its text is not: keep the page's own size.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            emptyPage(number, size, "The text of this page could not be read: ${e.message ?: e::class.simpleName}.")
        }
    }

    private fun emptyPage(
        number: Int,
        size: Pair<Double, Double>,
        note: String,
    ) = PageLayer(number, size.first, size.second, emptyList(), note = note)

    /**
     * The corrected layer starts as a copy of the original with the same region and line ids, and each line names
     * the original line it came from: itself (`docs/project-format.md`, Page files). The note is copied too.
     */
    internal fun startingPoint(original: PageLayer): PageLayer =
        original.copy(
            regions =
                original.regions.map { region ->
                    region.copy(lines = region.lines.map { it.copy(origin = it.id) })
                },
        )
}
