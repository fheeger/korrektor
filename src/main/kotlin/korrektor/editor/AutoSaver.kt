package korrektor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.PageLayer
import korrektor.format.reason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.util.concurrent.CancellationException

/** What a save came to: everything that was unsaved is on disk, or it stopped at a [Failed] page. */
sealed interface SaveOutcome {
    data object Done : SaveOutcome

    /** Writing [page] failed for [reason]; that page and the ones after it are still unsaved. */
    data class Failed(
        val page: Int,
        val reason: String,
    ) : SaveOutcome
}

/**
 * Writes the corrections of an open project to its page files (S07), in the background. [saveNow] takes a snapshot of
 * the unsaved pages of the [editor], writes them one by one on [io] with [write] (the crash-safe writing of
 * `Project.writePage`) and tells the editor which layers are on disk. Only the pages with changes are written; the
 * original layers, `source.pdf` and `project.json` are not touched.
 *
 * Typing is never held up: the snapshot is made of immutable layers, nothing of the editor is locked while writing,
 * and a page edited meanwhile stays unsaved (the editor compares layers by identity) and is written by the next save.
 * Saves never overlap. When to save is decided by the caller (`run`, T059); this is only the saving.
 *
 * @param write writes the layer of a page, normally `project::writePage`; a seam for tests.
 */
class AutoSaver(
    private val editor: BookEditor,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val write: (Int, PageLayer) -> Unit,
) {
    private val one = Mutex()

    /** True while pages are being written. */
    var saving: Boolean by mutableStateOf(false)
        private set

    /** Why the last save failed, or null if it did not (or if a later one worked). */
    var error: String? by mutableStateOf(null)
        private set

    /**
     * Writes every page that is unsaved now. If another save is running, waits for it first. A failure does not
     * escape: it is the result, and also in [error]. Cancelling the caller does not stop a page that is being written
     * (a file write cannot be interrupted), but no further page is started.
     */
    suspend fun saveNow(): SaveOutcome = one.withLock { saveLocked() }

    private suspend fun saveLocked(): SaveOutcome {
        val snapshot = editor.unsaved()
        if (snapshot.isEmpty()) {
            error = null
            return SaveOutcome.Done
        }
        saving = true
        try {
            val outcome = withContext(io) { writeAll(snapshot) { ensureActive() } }
            error = (outcome as? SaveOutcome.Failed)?.reason
            return outcome
        } finally {
            saving = false
        }
    }

    private fun writeAll(
        snapshot: Map<Int, PageLayer>,
        stillWanted: () -> Unit,
    ): SaveOutcome {
        for ((page, layer) in snapshot) {
            stillWanted() // throws if the caller was cancelled: no further page is started
            try {
                write(page, layer)
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Any failure of writing is a failed save, not a crash: the changes stay in memory (S07).
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                return SaveOutcome.Failed(page, describe(e))
            }
            editor.markSaved(page, layer)
        }
        return SaveOutcome.Done
    }

    private fun describe(e: Exception): String =
        when (e) {
            is FileSystemException -> {
                val name = e.file?.let { Path.of(it).fileName.toString() }
                (name?.let { "$it: " } ?: "") + e.reason()
            }
            is IOException -> e.reason()
            else -> e.message ?: e::class.simpleName ?: "unknown error"
        }
}
