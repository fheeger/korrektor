package korrektor.importer

import korrektor.format.FormatException
import korrektor.format.PageInfo
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.pdf.PdfDocument
import korrektor.pdf.PdfPage
import java.util.concurrent.CancellationException

/** What an import did. */
data class ImportResult(
    /** The number of pages imported: one for every page of the PDF. */
    val pageCount: Int,
)

/**
 * Reads the text layer of a project's PDF page by page and writes, for each page, the original OCR layer
 * (written once, never changed) and the corrected layer, which starts as a copy of it (S03, A2).
 *
 * The rules for what is read from the PDF are in `docs/pdf-text-layer.md`. The work is synchronous: the caller runs
 * it on a background thread and uses [run]'s callbacks for progress and cancelling.
 */
object PdfImport {
    /**
     * Imports every page of `project.sourcePdf`. First the page list of the project is set to one entry per PDF
     * page, then the pages are written one after the other: [progress] is called with the number of pages done and
     * the total after each page, and [isCancelled] is asked before each page.
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
    ): ImportResult {
        if (project.pages.isNotEmpty()) {
            throw FormatException("The text of this project has been imported already; the original is never replaced")
        }
        PdfDocument.open(project.sourcePdf).use { pdf ->
            val total = pdf.pageCount
            project.setPages((1..total).map { PageInfo(it) })
            for (number in 1..total) {
                if (isCancelled()) {
                    throw CancellationException(
                        "The import was cancelled after ${number - 1} of $total pages",
                    )
                }
                val original = pdf.page(number - 1).use { importPage(it, number) }
                project.writeOriginalPage(number, original)
                project.writePage(number, startingPoint(original))
                progress(number, total)
            }
            return ImportResult(total)
        }
    }

    /** The layer of one page as the text layer of the PDF gives it: size, and the lines in one body region. */
    private fun importPage(
        page: PdfPage,
        pdfPage: Int,
    ): PageLayer {
        val geometry = PageGeometry.of(page)
        val words = WordExtractor.words(page.text()).map { PlacedWord(it.text, geometry.toFormat(it.box)) }
        return PageLayer(pdfPage, geometry.width, geometry.height, listOfNotNull(LineBuilder.build(words)))
    }

    /**
     * The corrected layer starts as a copy of the original with the same region and line ids, and each line names
     * the original line it came from: itself (`docs/project-format.md`, Page files).
     */
    internal fun startingPoint(original: PageLayer): PageLayer =
        original.copy(
            regions =
                original.regions.map { region ->
                    region.copy(lines = region.lines.map { it.copy(origin = it.id) })
                },
        )
}
