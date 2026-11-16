package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.editor.AutoSaver
import korrektor.editor.BookEditor
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.ProjectLock
import korrektor.format.ProjectLockedException
import korrektor.settings.AppSettings
import korrektor.viewer.ViewerState
import korrektor.viewer.restore
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * The state of the app: which project is open, and the error or notice to show, if any.
 * [projectFactory] makes new projects; tests put another one in its place.
 */
class AppModel(
    val projectFactory: ProjectFactory = DefaultProjectFactory,
    /** Where the page and zoom of each project are remembered. In memory unless the app gives it a file. */
    val settings: AppSettings = AppSettings.inMemory(),
    /** How the corrections of a project are written; tests put a failing one in its place. */
    private val pageWriter: (Project) -> (Int, PageLayer) -> Unit = { it::writePage },
) {
    /** The position of the divider between scan and text, the same for every project. */
    val layout = LayoutState(settings)

    /** The open project, or null on the start screen. */
    var project: Project? by mutableStateOf(null)
        internal set

    /**
     * What the viewer shows of the open project: the page and the zoom. Made when the project is opened, from what
     * the settings remember, and saved back to them (see `saveView` and `keepViewSaved`).
     */
    var viewer: ViewerState? by mutableStateOf(null)
        internal set

    /** The right to work on the open project: nobody else can open it as long as this is held (S07). */
    internal var lock: ProjectLock? = null

    /** The corrected text of the open project in memory, shared by the scan and the text panel (S05). */
    var editor: BookEditor? by mutableStateOf(null)
        internal set

    /** Writes the corrections of the open project to its page files (S07); `App` runs its timer. */
    var saver: AutoSaver? by mutableStateOf(null)
        internal set

    /**
     * The metadata of the open project. [Project.metadata] is plain data, so the screens read it from here,
     * which the UI can observe.
     */
    var metadata: Metadata? by mutableStateOf(null)
        internal set

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

    /** Whether the list of keyboard shortcuts is showing: F1 opens it, F1 again closes it. */
    var shortcutsOpen: Boolean by mutableStateOf(false)

    /** A message for the user, shown until it is dismissed. */
    var error: String? by mutableStateOf(null)
        internal set

    /**
     * Set when the window is to be closed and the changes could not be saved: what went wrong. The dialog that offers
     * to save somewhere else or to close without saving shows while this is set (S07).
     */
    var closeProblem: String? by mutableStateOf(null)
        internal set

    /** True while a close request is saving; a second request in that time is ignored. */
    internal var closing: Boolean = false

    /** Ends the app. `Main` puts `exitApplication` here; tests put something that records the call. */
    var exit: () -> Unit = {}

    /** Something the user should know that is not an error, for example which pages had no text. */
    var notice: String? by mutableStateOf(null)
        internal set

    /**
     * Opens the project in [folder]. If that fails, [error] says why and the current project stays open. A project
     * that is open already (in another window or instance, or this one) is not opened a second time (S07).
     */
    fun openProject(folder: Path) {
        if (!flushBeforeSwitch()) return
        var taken: ProjectLock? = null
        try {
            // Only a project folder is locked: the lock file must not be left in just any folder that was chosen.
            if (Files.isRegularFile(folder.resolve(Project.PROJECT_FILE))) taken = ProjectLock.acquire(folder)
            open(Project.open(folder), taken)
        } catch (e: ProjectLockedException) {
            error = e.message
        } catch (e: FormatException) {
            taken?.close()
            error = e.message
        }
    }

    fun dismissError() {
        error = null
    }

    fun dismissNotice() {
        notice = null
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

    /** Makes [created] the open project, shows [notice] if there is one, and closes the "New project" dialog. */
    fun openCreated(
        created: Project,
        notice: String? = null,
    ) {
        if (flushBeforeSwitch() && openLocked(created)) {
            this.notice = notice
            newProjectOpen = false
        }
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

    internal fun open(
        opened: Project,
        taken: ProjectLock?,
    ) {
        saveView() // the project that was open until now
        // Its lock is given back only now that the new project is open: if opening failed, nothing was changed.
        lock?.close()
        lock = taken
        project = opened
        viewer = ViewerState(opened.pages.size).also { state -> settings.viewOf(opened.folder)?.let(state::restore) }
        val newEditor = BookEditor(opened::readPage, Dispatchers.IO)
        val newSaver = AutoSaver(newEditor, Dispatchers.IO, write = pageWriter(opened))
        newEditor.onUnsaved = newSaver::changed
        editor = newEditor
        saver = newSaver
        metadata = opened.metadata
        error = null
        notice = null
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
