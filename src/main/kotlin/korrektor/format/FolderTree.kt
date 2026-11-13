package korrektor.format

import java.nio.file.Path

/**
 * Deletes [folder] and everything in it, files that were made read-only included (the copy of the PDF in a project
 * is). Used to clean up after a failed or cancelled creation. It is deliberately blunt: callers make sure that
 * [folder] is one they created themselves.
 */
internal fun deleteTree(folder: Path) {
    folder.toFile().walkBottomUp().forEach {
        it.setWritable(true)
        it.delete()
    }
}
