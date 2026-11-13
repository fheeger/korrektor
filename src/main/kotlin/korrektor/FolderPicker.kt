package korrektor

import java.nio.file.Path
import javax.swing.JFileChooser

/** Lets the user choose a folder. It is an interface so that tests can replace the dialog. */
fun interface FolderPicker {
    /** The chosen folder, or null if the user cancelled. */
    fun pick(): Path?
}

/**
 * A folder chooser from Swing. The AWT file dialog cannot choose folders on Windows, which is what a project is.
 * It must be called on the UI thread, as Compose's click handlers are.
 */
object SwingFolderPicker : FolderPicker {
    override fun pick(): Path? {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }
}
