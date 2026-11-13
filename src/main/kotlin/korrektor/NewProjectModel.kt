package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.FormatException
import korrektor.format.Metadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * The state of the "New project" dialog (A1). On success the new project becomes the open one in [app].
 * Creating copies the PDF, which can be large, so it runs on [io] while [busy] is set.
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

    var busy: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

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

    /** Starts creating the project. Returns the running job, or null if [canCreate] is false. */
    fun create(): Job? {
        val pdf = pdf
        val location = location
        if (!canCreate || pdf == null || location == null) return null
        busy = true
        error = null
        val metadata = Metadata(title, author, year, source)
        return scope.launch {
            try {
                app.show(withContext(io) { NewProject.create(location, pdf, metadata) })
            } catch (e: FormatException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}
