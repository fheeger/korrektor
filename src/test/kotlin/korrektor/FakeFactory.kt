package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.ImportResult
import java.nio.file.Path

/**
 * A [ProjectFactory] for tests. It creates projects as the real one does, but its import is whatever [importer]
 * says (by default nothing), so tests can make the import slow, failing, cancellable or observable. If
 * [createFails] is set, creating the project throws it.
 */
class FakeFactory(
    private val createFails: Exception? = null,
    private val importer: (Project, (Int, Int) -> Unit, () -> Boolean) -> ImportResult = { _, _, _ -> ImportResult(0) },
) : ProjectFactory {
    override fun create(
        location: Path,
        pdf: Path,
        metadata: Metadata,
    ): Project = createFails?.let { throw it } ?: NewProject.create(location, pdf, metadata)

    override fun importText(
        project: Project,
        progress: (Int, Int) -> Unit,
        isCancelled: () -> Boolean,
    ) = importer(project, progress, isCancelled)
}
