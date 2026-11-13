package korrektor

import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.ImportResult
import korrektor.pdf.PdfException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.CancellationException

/** How making a project went. */
internal sealed interface CreationOutcome {
    /** The project exists and its text is imported. */
    class Done(
        val project: Project,
        val result: ImportResult,
    ) : CreationOutcome

    /** The user cancelled. Nothing is left behind. */
    data object Cancelled : CreationOutcome

    /** Something went wrong, [message] says what. Nothing is left behind. */
    class Failed(
        val message: String,
    ) : CreationOutcome
}

/** What to make and how to make it. */
internal class CreationRequest(
    val location: Path,
    val pdf: Path,
    val metadata: Metadata,
    val factory: ProjectFactory,
    val io: CoroutineDispatcher,
)

/**
 * Creates the project and imports its text, off the UI thread. Whatever goes wrong, and when [cancelRequested]
 * turns true, the project that was just made is deleted completely, so no half-made project is left behind (S03).
 * [progress] gets the pages done and the total. A cancel is noticed between two pages.
 */
internal suspend fun createProject(
    request: CreationRequest,
    cancelRequested: () -> Boolean,
    progress: (done: Int, total: Int) -> Unit,
): CreationOutcome =
    try {
        val project =
            withContext(request.io) { request.factory.create(request.location, request.pdf, request.metadata) }
        importInto(project, request, cancelRequested, progress)
    } catch (e: FormatException) {
        CreationOutcome.Failed(e.message ?: "The project could not be created.")
    } catch (
        // Whatever else goes wrong must end up as a message, not as a crash of the window.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        currentCoroutineContext().ensureActive() // being cancelled from outside is not a failure to report
        CreationOutcome.Failed("Creating the project failed unexpectedly: ${e.message ?: e::class.simpleName}")
    }

/** Imports into the new [project]; on anything but success the project is deleted before the outcome is returned. */
private suspend fun importInto(
    project: Project,
    request: CreationRequest,
    cancelRequested: () -> Boolean,
    progress: (done: Int, total: Int) -> Unit,
): CreationOutcome {
    val outcome =
        try {
            val result =
                withContext(request.io) {
                    request.factory.importText(project, progress) { cancelRequested() || !isActive }
                }
            CreationOutcome.Done(project, result)
        } catch (e: CancellationException) {
            if (!cancelRequested()) {
                discard(project, request)
                throw e // cancelled from outside, for example because the window goes away
            }
            CreationOutcome.Cancelled
        } catch (e: FormatException) {
            CreationOutcome.Failed(e.message ?: "The text could not be imported.")
        } catch (e: PdfException) {
            CreationOutcome.Failed(e.message ?: "The PDF could not be read.")
        } catch (
            // The project must not stay half-made whatever the reason.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            CreationOutcome.Failed("Importing the text failed unexpectedly: ${e.message ?: e::class.simpleName}")
        }
    if (outcome !is CreationOutcome.Done) discard(project, request)
    return outcome
}

/** Deletes the project, also when the surrounding coroutine is being cancelled. */
private suspend fun discard(
    project: Project,
    request: CreationRequest,
) {
    withContext(NonCancellable + request.io) { NewProject.discard(project) }
}
