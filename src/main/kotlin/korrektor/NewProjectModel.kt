package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Metadata
import korrektor.importer.summary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * The state of the "New project" dialog (A1). On success the new project becomes the open one in [app].
 * Creating copies the PDF and importing reads every page, which takes a while, so both run on [io] while [busy] is
 * set, with the progress in [importedPages] and [totalPages] and the way out in [cancel].
 */
class NewProjectModel(
    private val app: AppModel,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MetadataForm {
    var pdf: Path? by mutableStateOf(null)
        private set

    override var title: String by mutableStateOf("")
        private set

    override var author: String by mutableStateOf("")
        private set

    override var year: String by mutableStateOf("")
        private set

    override var source: String by mutableStateOf("")
        private set

    /** The folder that will hold the new project folder. */
    var location: Path? by mutableStateOf(null)
        private set

    /** True while the project is being created. The app also uses it to keep the window open meanwhile. */
    val busy: Boolean get() = app.creating

    var error: String? by mutableStateOf(null)
        private set

    /** While the text is imported: the pages done and the total. Both are 0 before the import starts. */
    var importedPages: Int by mutableIntStateOf(0)
        private set

    var totalPages: Int by mutableIntStateOf(0)
        private set

    /** Set by [cancel], read by the import on another thread. */
    @Volatile
    private var cancelRequested = false

    /** True while the text is imported, as opposed to the project being created before that. */
    val importing: Boolean get() = busy && totalPages > 0

    // A suggestion only fills in what the user has not decided on.
    private var titleTyped = false
    private var locationChosen = false

    val canCreate: Boolean get() = pdf != null && title.isNotBlank() && location != null && !busy

    /** The project folder that will be created, so the user can see where the project goes. */
    val projectFolder: Path? get() = location?.resolve(NewProject.folderName(title))

    fun selectPdf(path: Path) {
        pdf = path
        error = null
        if (!titleTyped || title.isBlank()) {
            title = NewProject.suggestTitle(path)
            titleTyped = false
        }
        if (!locationChosen) location = path.toAbsolutePath().parent
    }

    override fun updateTitle(text: String) {
        title = text
        titleTyped = true
        error = null
    }

    override fun updateAuthor(text: String) {
        author = text
        error = null
    }

    override fun updateYear(text: String) {
        year = text
        error = null
    }

    override fun updateSource(text: String) {
        source = text
        error = null
    }

    fun selectLocation(path: Path) {
        location = path
        locationChosen = true
        error = null
    }

    /**
     * Starts creating the project and importing its text. Returns the running job, or null if [canCreate] is false.
     * On success the project opens; on failure or [cancel] a message is shown, the dialog stays as it is, and
     * nothing is left in the target folder.
     */
    fun create(): Job? {
        val pdf = pdf
        val location = location
        if (!canCreate || pdf == null || location == null) return null
        app.creating = true
        error = null
        importedPages = 0
        totalPages = 0
        cancelRequested = false
        val request = CreationRequest(location, pdf, Metadata(title, author, year, source), app.projectFactory, io)
        return scope.launch {
            try {
                when (val outcome = createProject(request, { cancelRequested }, ::reportProgress)) {
                    is CreationOutcome.Done -> app.openCreated(outcome.project, outcome.result.summary())
                    CreationOutcome.Cancelled -> error = "Import cancelled."
                    is CreationOutcome.Failed -> error = outcome.message
                }
            } finally {
                app.creating = false
            }
        }
    }

    /** Asks for the creation to stop at the next page. The project that was made so far is deleted. */
    fun cancel() {
        if (busy) cancelRequested = true
    }

    // Called from the thread of the import; Compose state may be written from any thread.
    private fun reportProgress(
        done: Int,
        total: Int,
    ) {
        totalPages = total
        importedPages = done
    }
}
