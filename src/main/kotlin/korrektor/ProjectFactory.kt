package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.ImportResult
import korrektor.importer.PdfImport
import java.nio.file.Path

/**
 * The two steps of making a project from a PDF: create the project folder with its copy of the PDF, and import the
 * text. The app uses [DefaultProjectFactory]; tests and UI tests put a slow, failing or cancellable one in its place.
 */
interface ProjectFactory {
    /** Creates the project folder for [pdf] in [location], with the PDF copied in and an empty page list. */
    fun create(
        location: Path,
        pdf: Path,
        metadata: Metadata,
    ): Project

    /**
     * Imports the text of the project's PDF page by page, calling [progress] with the pages done and the total, and
     * stopping with a `CancellationException` once [isCancelled] is true.
     */
    fun importText(
        project: Project,
        progress: (done: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): ImportResult
}

/** The real thing: [NewProject.create] and [PdfImport.run]. */
object DefaultProjectFactory : ProjectFactory {
    override fun create(
        location: Path,
        pdf: Path,
        metadata: Metadata,
    ) = NewProject.create(location, pdf, metadata)

    override fun importText(
        project: Project,
        progress: (done: Int, total: Int) -> Unit,
        isCancelled: () -> Boolean,
    ) = PdfImport.run(project, progress, isCancelled)
}
