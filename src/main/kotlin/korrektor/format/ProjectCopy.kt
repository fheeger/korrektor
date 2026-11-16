package korrektor.format

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

/**
 * Copies the whole project to [target], a folder that does not exist yet: `project.json`, `source.pdf`, `pages/` and
 * `history/`, each file with the crash-safe copy of `AtomicFile`. The working files are left out: the lock and
 * `*.tmp`. The project itself is not changed. If anything fails, nothing is left at [target].
 *
 * Used when saving to the project folder keeps failing and the user chooses another place (S07). If the project
 * folder cannot be read any more, the copy fails with a message, and so does nothing else of that project.
 *
 * @throws FormatException if [target] exists, the project has no PDF, or the copy failed.
 */
fun Project.copyTo(target: Path) {
    checkCanCopyTo(target)
    try {
        copyFiles(target)
    } catch (e: IOException) {
        failCopy(target, e, e.reason())
    } catch (e: UncheckedIOException) {
        failCopy(target, e, e.cause?.reason() ?: e.message)
    }
}

private fun Project.checkCanCopyTo(target: Path) {
    if (target.exists()) throw FormatException("$target already exists")
    // A copy without the PDF would not open: better to say so before anything is made.
    if (!sourcePdf.exists()) throw FormatException("$folder has no ${Project.SOURCE_PDF} to copy")
}

private fun Project.copyFiles(target: Path) {
    Files.createDirectories(target)
    Files.walk(folder).use { paths ->
        paths.filter { !isWorkingFile(it) }.forEach { source ->
            val destination = target.resolve(folder.relativize(source).toString())
            if (source.isDirectory()) Files.createDirectories(destination) else AtomicFile.copy(source, destination)
        }
    }
    // As when the project was made; best effort, not every file system supports it.
    target.resolve(Project.SOURCE_PDF).toFile().setReadOnly()
}

private fun failCopy(
    target: Path,
    cause: Exception,
    reason: String?,
): Nothing {
    // [target] did not exist before the copy, so it holds nothing but what the copy made.
    try {
        deleteTree(target)
    } catch (_: FormatException) {
        // The copy failed already; the message for that is more useful than one for the cleanup.
    }
    throw FormatException("Cannot copy the project to $target: $reason", cause)
}

private fun isWorkingFile(file: Path) = file.fileName.toString() == ProjectLock.FILE || file.extension == "tmp"
