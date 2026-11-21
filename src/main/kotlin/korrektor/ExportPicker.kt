package korrektor

import korrektor.export.ExportFormat
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** Lets the user choose where an export goes. It is an interface so that tests can replace the dialog. */
fun interface ExportPicker {
    /** The chosen file, or null if the user cancelled. [suggestedName] is the name to start with. */
    fun pick(
        suggestedName: String,
        format: ExportFormat,
    ): Path?
}

/**
 * The system save dialog. The system asks before it replaces a file, but only for the name as typed: a name without
 * the extension of the format is therefore put back into the dialog with the extension added, so that the question
 * is asked for the file that is written. Call it on the UI thread, as Compose's click handlers do.
 */
object AwtExportPicker : ExportPicker {
    override fun pick(
        suggestedName: String,
        format: ExportFormat,
    ): Path? {
        var name = suggestedName
        while (true) {
            val dialog = FileDialog(null as Frame?, "Export as ${format.label}", FileDialog.SAVE)
            dialog.file = name
            dialog.isVisible = true
            val file = dialog.file ?: return null
            if (file.endsWith(".${format.extension}", ignoreCase = true)) return Path.of(dialog.directory, file)
            name = "$file.${format.extension}"
        }
    }
}
