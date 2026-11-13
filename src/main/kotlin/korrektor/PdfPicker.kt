package korrektor

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** Lets the user choose a PDF. It is an interface so that tests can replace the dialog. */
fun interface PdfPicker {
    /** The chosen file, or null if the user cancelled. */
    fun pick(): Path?
}

/** The system file dialog, showing only `*.pdf`. Call it on the UI thread, as Compose's click handlers do. */
object AwtPdfPicker : PdfPicker {
    override fun pick(): Path? {
        val dialog = FileDialog(null as Frame?, "Choose a PDF", FileDialog.LOAD)
        // Windows takes the pattern, other systems the filter.
        dialog.file = "*.pdf"
        dialog.setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory, file)
    }
}
