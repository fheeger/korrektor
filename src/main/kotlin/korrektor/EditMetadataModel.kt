package korrektor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Metadata
import korrektor.format.reason
import java.io.IOException

/**
 * The state of the "Edit metadata" dialog. The fields start with the current values of the open project
 * ([current] is null for a project without metadata, then they start empty).
 */
class EditMetadataModel(
    private val app: AppModel,
    private val current: Metadata?,
) : MetadataForm {
    override var title: String by mutableStateOf(current?.title ?: "")
        private set

    override var author: String by mutableStateOf(current?.author ?: "")
        private set

    override var year: String by mutableStateOf(current?.year ?: "")
        private set

    override var source: String by mutableStateOf(current?.source ?: "")
        private set

    var error: String? by mutableStateOf(null)
        private set

    private val changed: Boolean
        get() =
            title != (current?.title ?: "") ||
                author != (current?.author ?: "") ||
                year != (current?.year ?: "") ||
                source != (current?.source ?: "")

    /** The title must not be blank, and there must be something to save. */
    val canSave: Boolean get() = title.isNotBlank() && changed

    override fun updateTitle(text: String) {
        title = text
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

    /**
     * Saves the metadata and closes the dialog. If writing fails, [error] says so, the dialog stays open and
     * the old metadata stays in the file and in the app.
     */
    fun save() {
        if (!canSave) return
        // Unknown fields of the existing metadata are kept.
        val updated =
            current?.copy(title = title, author = author, year = year, source = source)
                ?: Metadata(title, author, year, source)
        try {
            app.updateMetadata(updated)
            app.closeEditMetadata()
        } catch (e: IOException) {
            error = "The metadata could not be saved: ${e.reason()}"
        }
    }
}
