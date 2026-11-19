package korrektor.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Metadata
import korrektor.format.PageStatus
import korrektor.settings.AppSettings
import korrektor.settings.exportOptions
import korrektor.settings.rememberExportOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** What the export dialog needs to know of the book: its number of pages, its metadata and the page statuses now. */
class ExportBook(
    val pageCount: Int,
    val metadata: Metadata?,
    /** The status of every page that is not unchecked, as the editor has it now. */
    val statuses: () -> Map<Int, PageStatus>,
)

/**
 * The state of the export dialog (S11), without any UI: the format, the page range, the options, the note about
 * pages that are not checked yet, and the export while it runs.
 *
 * Each format has its own options; they start as what the settings remember of the last export of that format (the
 * defaults the first time), and are remembered again when an export starts.
 *
 * @param onDone called, on the UI thread, with a message for the user when an export has been written
 */
class ExportModel(
    private val book: ExportBook,
    private val settings: AppSettings,
    private val export: BookExport,
    private val scope: CoroutineScope,
    private val onDone: (String) -> Unit,
) {
    private val choices = ExportFormat.entries.associateWith { settings.exportOptions(it) }.toMutableMap()
    private var job: Job? = null

    /** The format that is exported; its options are [options]. */
    var format: ExportFormat by mutableStateOf(ExportFormat.TEXT)
        private set

    /** The options of [format], as they are in the dialog now. */
    var options: ExportOptions by mutableStateOf(choices.getValue(ExportFormat.TEXT))
        private set

    /** The text of the field for the first page; the whole book at first. */
    var from: String by mutableStateOf("1")
        private set

    /** The text of the field for the last page. */
    var to: String by mutableStateOf(book.pageCount.toString())
        private set

    /** True while an export is running. */
    var busy: Boolean by mutableStateOf(false)
        private set

    /** How many pages the running export has done. */
    var done: Int by mutableIntStateOf(0)
        private set

    /** Why the last export failed, if it did. */
    var error: String? by mutableStateOf(null)
        private set

    private val pageCount: Int get() = book.pageCount

    // The two fields as a range, empty if the first is after the last; null if one of them is not a number.
    private fun typed(): IntRange? {
        val first = from.trim().toIntOrNull()
        val last = to.trim().toIntOrNull()
        return if (first != null && last != null) first..last else null
    }

    /** The pages to export, or null if the two fields do not make a range of the book. */
    val range: IntRange?
        get() = typed()?.takeIf { !it.isEmpty() && it.first >= 1 && it.last <= pageCount }

    /** What is wrong with the range, or null if it is fine. */
    val rangeProblem: String?
        get() {
            val typed = typed()
            val inBook = typed != null && typed.first in 1..pageCount && typed.last in 1..pageCount
            return when {
                !inBook -> "Enter page numbers from 1 to $pageCount."
                typed.isEmpty() -> "The first page must not come after the last."
                else -> null
            }
        }

    /** Whether the export can be started. */
    val canExport: Boolean get() = range != null && !busy

    /** How many pages are in the range, 0 if there is none. */
    val pagesInRange: Int get() = range?.count() ?: 0

    /** "38 of 165 pages are still unchecked", or null if there are none in the range (or there is no range). */
    val uncheckedNote: String?
        get() {
            val pages = range ?: return null
            val total = pages.count()
            val unchecked = total - book.statuses().keys.count { it in pages }
            if (unchecked == 0) return null
            val what = if (pages.first == 1 && pages.last == pageCount) "pages" else "pages in the range"
            val noun = if (total == 1) "page" else what
            return "$unchecked of $total $noun ${if (unchecked == 1) "is" else "are"} still unchecked. " +
                "They are exported as they are."
        }

    fun selectFormat(new: ExportFormat) {
        if (busy) return
        format = new
        options = choices.getValue(new)
    }

    fun updateFrom(text: String) {
        from = text
    }

    fun updateTo(text: String) {
        to = text
    }

    /** Changes the options of the format shown, for example `changeOptions(options.copy(header = false))`. */
    fun changeOptions(new: ExportOptions) {
        if (busy || new.format != format) return
        options = new
        choices[new.format] = new
    }

    /** The name the save dialog suggests: the title as a file name, with the extension of the format. */
    fun suggestedName(fileName: (String) -> String): String =
        fileName(book.metadata?.title ?: "Export") + "." + format.extension

    /**
     * Exports the range to [target] in the background. The options are remembered first. When it is done [onDone]
     * says so; if it fails, [error] says why; [cancel] stops it, and the file is then as it was.
     */
    fun start(target: Path) {
        val pages = range ?: return
        if (busy) return
        settings.rememberExportOptions(options)
        busy = true
        done = 0
        error = null
        val chosen = options
        job =
            scope.launch {
                try {
                    val count = export.run(target, book.metadata, pages, chosen) { done = it }
                    onDone("Exported $count ${if (count == 1) "page" else "pages"} to $target")
                } catch (e: ExportException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
    }

    /** Stops the running export. Nothing is written: the file is as it was. */
    fun cancel() {
        job?.cancel(CancellationException("The export was cancelled"))
    }
}
