package korrektor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Line
import korrektor.format.PageLayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

private const val NANOS_PER_MILLI = 1_000_000L

/** A request to put the cursor at [focus]. A new request has a new [serial], also if the place is the same. */
class FocusRequest(
    val focus: Focus,
    val serial: Long,
)

/** Where the cursor goes when a page is opened by the keyboard: its first line or its last line. */
enum class Arrival { FIRST, LAST }

/** What a move to another line came to: it moved, it needs another page, or there was nothing to move to. */
enum class LineMove { MOVED, PREVIOUS_PAGE, NEXT_PAGE, NOTHING }

/**
 * The corrected text of the book while it is being worked on, shared by the scan and the text panel (S05). It holds
 * the corrected layer of the page shown, and keeps every page that was changed in this session, so that going to
 * another page and back does not lose a correction. It does not write anything: saving is autosave (S07), which will
 * write what `unsaved()` says. The original OCR layer is never touched.
 *
 * Compose state, all of it changed from the UI thread, except that [show] reads a page file on [io].
 *
 * @param readPage reads the corrected layer of a page from the project (`Project::readPage`); a seam for tests.
 */
class BookEditor(
    internal val readPage: (Int) -> PageLayer,
    private val io: CoroutineDispatcher,
    /** The clock in milliseconds that decides when typing pauses (see [TypingGroups]); tests replace it. */
    internal val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    /** The undo and redo steps of the book (S08). */
    val history: UndoHistory = UndoHistory(),
) {
    // Every page changed in this session with its latest layer. Written on the UI thread only, read by the saver too.
    internal val changed = ConcurrentHashMap<Int, PageLayer>()

    // Guards the changes of [changedPages] and of the history, which the UI thread (an edit) and the saver both make.
    internal val changedLock: Any get() = history.lock

    /** Called after every edit that changed a layer (the saver starts its timer here); not by [show]. */
    var onUnsaved: (() -> Unit)? = null
    private var serial = 0L

    /** The page the cursor is wanted on when it is shown, and where on it (see [arriveAt]). */
    internal var arriving: Pair<Int, Arrival>? = null

    /** The page a change was made on by undo or redo and where the cursor goes when that page is shown (T069). */
    internal var pendingFocus: Pair<Int, Focus>? = null

    // While `oneStep` runs: how deep it is, and the layer the first edit inside started from (see Recording.kt).
    internal var grouping = 0
    internal var groupStart: PageLayer? = null

    /** Counts the requests to give the text panel itself the focus (no line to put the cursor in). */
    internal var panelFocusRequests: Int by mutableIntStateOf(0)

    /**
     * The column of the cursor, as a character index: where it was last put or moved by the user. Not state: nothing
     * draws it. The rows report it, and moving to the line above or below keeps it (also over a short line).
     */
    var caret: Int = 0

    /** The page shown, from 1. */
    var page: Int by mutableIntStateOf(1)
        private set

    /** The corrected layer of the page shown, or null while it is being read or if it could not be read. */
    var layer: PageLayer? by mutableStateOf(null)
        internal set

    /** Why the page could not be read, if it could not. */
    var error: String? by mutableStateOf(null)
        private set

    /** The line the cursor is in; none after a page change. */
    var activeLineId: String? by mutableStateOf(null)
        private set

    /** The latest wish to move the cursor (after a split, a join, a click on the scan): the panel carries it out. */
    var focusRequest: FocusRequest? by mutableStateOf(null)
        private set

    /** The pages with corrections that are not saved yet: the pages the saver has to write (see `unsaved()`). */
    var changedPages: Set<Int> by mutableStateOf(emptySet())
        internal set

    /** The lines of the page shown in reading order. */
    val lines: List<Line> get() = layer?.let(PageEdits::readingOrder) ?: emptyList()

    /**
     * Shows [number]: from memory if it was changed in this session, otherwise read from the project. The layer is
     * empty while it is read. If the caller is cancelled (the next page was asked for), the result is not used.
     */
    suspend fun show(number: Int) {
        // A wish for the cursor on another page is stale (the page it was for was never shown): drop it.
        if (arriving?.first != number) arriving = null
        if (pendingFocus?.first != number) pendingFocus = null
        history.seal() // typing on the page before and typing on this one are not one step
        page = number
        layer = null
        error = null
        activeLineId = null
        focusRequest = null // a wish for the cursor belongs to the page it was made on
        val kept = changed[number]
        if (kept != null) {
            layer = kept
            applyArrival(number)
            return
        }
        try {
            val read = withContext(io) { readPage(number) }
            if (page == number) {
                layer = changed[number] ?: read // an undo may have changed the page while it was read
                applyArrival(number)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            // A page file that cannot be read is a message in the panel, not a crash.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (page == number) error = e.message ?: e::class.simpleName
        }
    }

    /** Makes the line the active one, if it is on the page. */
    fun activate(lineId: String?) {
        activeLineId = lineId?.takeIf { id -> lines.any { it.id == id } }
    }

    fun setLineText(
        lineId: String,
        text: String,
    ) {
        val current = layer ?: return
        // Typing is grouped into steps by what the edit did to the text, which the layer before it knows.
        val typed = Typed(page, lineId, TypingGroups.textEdit(find(current, lineId).text, text))
        edit(typed) { PageEdits.setLineText(it, lineId, text) }
    }

    fun splitLine(
        lineId: String,
        index: Int,
    ) = edit { PageEdits.splitLine(it, lineId, index) }

    fun joinWithPrevious(lineId: String) = edit { PageEdits.joinWithPrevious(it, lineId) }

    fun joinWithNext(lineId: String) = edit { PageEdits.joinWithNext(it, lineId) }

    /** Adds a line after [lineId], or at the end of the page if it is null. */
    fun addLineAfter(
        lineId: String?,
        text: String = "",
    ) = edit { PageEdits.addLineAfter(it, lineId, text) }

    fun deleteLine(lineId: String) = edit { PageEdits.deleteLine(it, lineId) }

    /**
     * Asks the panel to put the cursor at [index] of the line [lineId], also without an edit (a click on the scan). The
     * column ([caret]) becomes [index], unless [keepColumn]: a move to the line above or below keeps the column it
     * started from, even over a line that is too short for it.
     */
    fun requestFocus(
        lineId: String,
        index: Int,
        keepColumn: Boolean = false,
    ) {
        if (!keepColumn) caret = index
        activate(lineId)
        focusRequest = FocusRequest(Focus(lineId, index), ++serial)
    }

    private fun edit(
        typed: Typed? = null,
        change: (PageLayer) -> EditResult,
    ) {
        val current = layer ?: return
        val result = change(current)
        if (result.layer !== current) {
            recordEdit(current, result.layer, typed)
            onUnsaved?.invoke()
        }
        val focus = result.focus
        when {
            focus != null -> requestFocus(focus.lineId, focus.index)
            // The active line is gone (the only line of the page was deleted).
            activeLineId != null && lines.none { it.id == activeLineId } -> activeLineId = null
        }
    }
}
