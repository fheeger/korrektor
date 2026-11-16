package korrektor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.PageLayer
import korrektor.format.reason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** What the save status indicator says (S07): [text] is shown as it is. */
enum class SaveStatus(
    val text: String,
) {
    /** Nothing is unsaved and nothing is being written. */
    SAVED("Saved"),

    /** Pages are being written. */
    SAVING("Saving…"),

    /** Pages are unsaved and nothing is being written: waiting for the timer, or the last save failed. */
    NOT_SAVED("Not saved"),
}

/** How long saving waits before it tries again after a failure. */
const val RETRY_DELAY_MS = 5_000L

/** How long the user must stop typing before the corrections are saved. */
const val SAVE_DELAY_MS = 1_000L

/** The longest the corrections of continuous typing wait for a save: the most that a crash can cost. */
const val SAVE_MAX_WAIT_MS = 10_000L

private const val NANOS_PER_MILLI = 1_000_000L

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
 * Saves never overlap. [run] decides when to save: after a pause in typing, regularly during typing, and when asked.
 *
 * @param write writes the layer of a page, normally `project::writePage`; a seam for tests.
 */
class AutoSaver(
    private val editor: BookEditor,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val write: (Int, PageLayer) -> Unit,
) {
    private val one = Mutex()

    // What the timer needs to know, kept by `changed` (UI thread) and read by `run`.
    private val timing = Any()
    private var lastChange = 0L
    private var firstUnsaved: Long? = null
    private val flushRequested = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** True while pages are being written. */
    var saving: Boolean by mutableStateOf(false)
        private set

    /** Why the last save failed, or null if it did not (or if a later one worked). */
    var error: String? by mutableStateOf(null)
        private set

    private var warningDismissed: Boolean by mutableStateOf(false)

    /** What the indicator shows. The one place that decides it; the UI only shows it. */
    val status: SaveStatus
        get() =
            when {
                saving -> SaveStatus.SAVING
                editor.changedPages.isNotEmpty() -> SaveStatus.NOT_SAVED
                else -> SaveStatus.SAVED
            }

    /**
     * What to tell the user while saving fails: the reason, once however often saving is tried, until it works again
     * or the user has dismissed it (it comes back after a save that worked and failed again).
     */
    val warning: String? get() = error.takeUnless { warningDismissed }

    /** Something was changed: the timer starts or starts again. Called by the editor (`onUnsaved`) on every edit. */
    fun changed() {
        val t = now()
        synchronized(timing) {
            lastChange = t
            if (firstUnsaved == null) firstUnsaved = t
        }
        wake.trySend(Unit)
    }

    /** Asks for a save at once, for example because the page changes. Does not wait. */
    fun requestFlush() {
        flushRequested.set(true)
        wake.trySend(Unit)
    }

    /**
     * Saves when it is time: [SAVE_DELAY_MS] after the last change, but not later than [SAVE_MAX_WAIT_MS] after the
     * first change that is not saved yet (so that continuous typing is written regularly), and at once on
     * [requestFlush]. Does not return: run it from an effect that is cancelled when the project closes. With nothing
     * unsaved nothing is written.
     */
    suspend fun run() {
        while (true) {
            wake.receive()
            awaitDeadline()
            var outcome = saveAfterWaiting()
            // A failed save is tried again, as long as the pages are unsaved. The changes stay in memory meanwhile.
            while (outcome is SaveOutcome.Failed) {
                // A change in the meantime takes the normal way: the timer of a second, then the save.
                if (withTimeoutOrNull(RETRY_DELAY_MS) { wake.receive() } != null) awaitDeadline()
                outcome = saveAfterWaiting()
            }
        }
    }

    private suspend fun saveAfterWaiting(): SaveOutcome {
        synchronized(timing) { firstUnsaved = null }
        return saveNow()
    }

    /** Hides the warning about a failed save until saving has worked and failed again. Saving goes on as before. */
    fun dismissWarning() {
        warningDismissed = true
    }

    /** Returns when it is time to save: the deadline has come, or a flush was asked for. A new change moves it. */
    private suspend fun awaitDeadline() {
        while (!flushRequested.getAndSet(false)) {
            val deadline =
                synchronized(timing) {
                    minOf(
                        lastChange + SAVE_DELAY_MS,
                        (firstUnsaved ?: lastChange) + SAVE_MAX_WAIT_MS,
                    )
                }
            val wait = deadline - now()
            if (wait <= 0 || withTimeoutOrNull(wait) { wake.receive() } == null) return
        }
    }

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
            warningDismissed = false
            return SaveOutcome.Done
        }
        saving = true
        try {
            val outcome = withContext(io) { writeAll(snapshot) { ensureActive() } }
            error = (outcome as? SaveOutcome.Failed)?.reason
            if (error == null) warningDismissed = false
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
