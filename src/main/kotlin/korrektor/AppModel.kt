package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.Project
import java.nio.file.Path
import kotlin.io.path.name

/** The state of the app: which project is open, and the error to show, if any. */
class AppModel {
    /** The open project, or null on the start screen. */
    var project: Project? by mutableStateOf(null)
        private set

    /**
     * The metadata of the open project. [Project.metadata] is plain data, so the screens read it from here,
     * which the UI can observe.
     */
    var metadata: Metadata? by mutableStateOf(null)
        private set

    /** Whether the "New project" dialog is showing. */
    var newProjectOpen: Boolean by mutableStateOf(false)
        private set

    /** True while a new project is being created. The window must not be closed then, or it would stay half-made. */
    var creating: Boolean by mutableStateOf(false)
        internal set

    /** Whether the window may be closed now. */
    val canClose: Boolean get() = !creating

    /** Whether the "Edit metadata" dialog is showing. */
    var editMetadataOpen: Boolean by mutableStateOf(false)
        private set

    /** A message for the user, shown until it is dismissed. */
    var error: String? by mutableStateOf(null)
        private set

    /** Opens the project in [folder]. If that fails, [error] says why and the current project stays open. */
    fun openProject(folder: Path) {
        try {
            open(Project.open(folder))
        } catch (e: FormatException) {
            error = e.message
        }
    }

    fun dismissError() {
        error = null
    }

    fun openNewProject() {
        newProjectOpen = true
    }

    fun closeNewProject() {
        newProjectOpen = false
    }

    fun openEditMetadata() {
        editMetadataOpen = true
    }

    fun closeEditMetadata() {
        editMetadataOpen = false
    }

    /** Makes [created] the open project, and closes the "New project" dialog. */
    fun openCreated(created: Project) {
        open(created)
        newProjectOpen = false
    }

    /**
     * Changes the metadata of the open project and writes it to `project.json`.
     * If writing fails, the exception is thrown and the old metadata stays, in the file and in the app.
     */
    fun updateMetadata(new: Metadata) {
        val open = project ?: return
        open.updateMetadata(new)
        metadata = new
    }

    private fun open(opened: Project) {
        project = opened
        metadata = opened.metadata
        error = null
    }
}

/**
 * The window title: `<title> – korrektor` for a project, with the folder name instead of the title for a
 * project without metadata, and just `korrektor` on the start screen.
 */
fun windowTitle(
    project: Project?,
    metadata: Metadata?,
): String = if (project == null) APP_NAME else "${displayTitle(project, metadata)} – $APP_NAME"

/** The title from the metadata, or the folder name without `.korrektur` for a project without metadata. */
fun displayTitle(
    project: Project,
    metadata: Metadata?,
): String = metadata?.title ?: project.folder.name.removeSuffix(".korrektur")
