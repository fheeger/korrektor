package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.FormatException
import korrektor.format.Project
import java.nio.file.Path
import kotlin.io.path.name

/** The state of the app: which project is open, and the error to show, if any. */
class AppModel {
    /** The open project, or null on the start screen. */
    var project: Project? by mutableStateOf(null)
        private set

    /** A message for the user, shown until it is dismissed. */
    var error: String? by mutableStateOf(null)
        private set

    /** Opens the project in [folder]. If that fails, [error] says why and the current project stays open. */
    fun openProject(folder: Path) {
        try {
            project = Project.open(folder)
            error = null
        } catch (e: FormatException) {
            error = e.message
        }
    }

    fun dismissError() {
        error = null
    }
}

/**
 * The window title: `<title> – korrektor` for a project, with the folder name instead of the title for a
 * project without metadata, and just `korrektor` on the start screen.
 */
fun windowTitle(project: Project?): String = if (project == null) APP_NAME else "${project.displayTitle} – $APP_NAME"

/** The title from the metadata, or the folder name without `.korrektur` for a project without metadata. */
val Project.displayTitle: String
    get() = metadata?.title ?: folder.name.removeSuffix(".korrektur")
