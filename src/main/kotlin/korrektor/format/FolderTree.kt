package korrektor.format

import java.nio.file.Path

/**
 * Deletes [folder] and everything in it, files that were made read-only included (the copy of the PDF in a project
 * is). Used to clean up after a failed or cancelled creation. It is deliberately blunt: callers make sure that
 * [folder] is one they created themselves.
 *
 * @throws FormatException if something could not be deleted (a file held open by a virus scanner, an open
 *   Explorer window on Windows): the caller must not say that nothing is left behind.
 */
internal fun deleteTree(folder: Path) {
    val left =
        folder
            .toFile()
            .walkBottomUp()
            .filter {
                it.setWritable(true)
                !it.delete() && it.exists()
            }.toList()
    if (left.isNotEmpty()) throw FormatException("$folder could not be deleted completely; please delete it by hand")
}
