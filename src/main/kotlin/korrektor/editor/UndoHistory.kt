package korrektor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Change
import korrektor.format.EditEntry
import korrektor.format.HistoryEntry
import korrektor.format.HistoryLog
import korrektor.format.LineChange
import korrektor.format.RedoEntry
import korrektor.format.UndoEntry

/** One undo step: what a single Ctrl+Z takes back. [seq] is the number of the history entry that made it. */
class Step(
    val seq: Int,
    val changes: List<Change>,
)

/** Typing in the line [lineId] of [page]: what [UndoHistory.record] needs to group it with the typing before. */
class Typed(
    val page: Int,
    val lineId: String,
    val edit: TextEdit,
)

/**
 * The history of the open book in memory (S08): the steps that can be undone and redone, and the entries that are not
 * written to the history file yet. Typing is grouped into steps by [TypingGroups].
 *
 * Everything is guarded by [lock], which the editor shares (so that the saver takes the unsaved layers and the pending
 * entries together). [canUndo] and [canRedo] are Compose state.
 *
 * @param firstSeq the number the next entry gets: 1 for a new book, one more than the last entry of the file.
 */
@Suppress("TooManyFunctions") // the operations on one state (two stacks and the pending entries), each a few lines
class UndoHistory internal constructor(
    internal val lock: Any,
    private val loader: HistoryLoader?,
    firstSeq: Int,
) {
    /** A history that starts at [firstSeq] and has nothing older in a file. */
    constructor(lock: Any = Any(), firstSeq: Int = 1) : this(lock, null, firstSeq)

    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    private val pending = ArrayList<HistoryEntry>()
    private var nextSeq = firstSeq

    // The top of the undo stack is a step of typing that goes on: where it is, and the stamp the page had before it.
    private var open: OpenTyping? = null

    private class OpenTyping(
        var run: TypingRun,
        val stampBefore: Int?,
    )

    /** Whether there is a step to undo. */
    var canUndo: Boolean by mutableStateOf(false)
        private set

    /** Whether there is a step to redo. */
    var canRedo: Boolean by mutableStateOf(false)
        private set

    /** The steps that can be undone, the oldest first. */
    val undoSteps: List<Step> get() = synchronized(lock) { undoStack.toList() }

    /** The steps that can be redone, the one that comes next last. */
    val redoSteps: List<Step> get() = synchronized(lock) { redoStack.toList() }

    /** The entries that the history file does not have yet, in order. */
    val pendingEntries: List<HistoryEntry> get() = synchronized(lock) { pending.toList() }

    /**
     * Records [changes] as a step: it becomes the next step to undo, the redo steps are gone, and its entry is pending.
     * If [typed] says it is typing that goes on with the open step (see [TypingGroups.continues]), the step is
     * extended instead: it keeps what it removed and takes the newest text, and its entry is updated; if that leaves
     * nothing changed, the step is dropped. [stampBefore] is the `history` of the page's layer before this edit.
     * Returns the number of the entry that the layer is to carry: the step's, or, for a dropped step, [stampBefore].
     */
    fun record(
        changes: List<Change>,
        typed: Typed? = null,
        now: Long = 0,
        stampBefore: Int? = null,
    ): Int? =
        synchronized(lock) {
            val current = open
            val top = undoStack.lastOrNull()
            val joined = if (current != null && top != null) joinWithOpen(current, top, changes, typed, now) else null
            when {
                joined == null || typed == null || current == null || top == null ->
                    newStep(changes, typed, now, stampBefore)
                joined.isEmpty() -> dropOpen(current)
                else -> extendOpen(current, top, joined, typed, now)
            }
        }

    /** The changes of the open step [top] with [changes] added, if [typed] goes on with it ([compose]); else null. */
    private fun joinWithOpen(
        current: OpenTyping,
        top: Step,
        changes: List<Change>,
        typed: Typed?,
        now: Long,
    ): List<Change>? =
        if (typed != null && TypingGroups.continues(current.run, typed.page, typed.lineId, typed.edit, now)) {
            compose(top.changes, changes)
        } else {
            null
        }

    /** The open typing step takes no more edits: whatever comes next is a step of its own. */
    fun seal() {
        synchronized(lock) { open = null }
    }

    /** Gives the saver the pending entries and empties the list; typing goes on in a new step (see [seal]). */
    fun drainPending(): List<HistoryEntry> =
        synchronized(lock) {
            open = null
            pending.toList().also { pending.clear() }
        }

    /** The step that undo (or redo) takes next, or null. Also closes the open typing step. */
    fun peek(undoing: Boolean): Step? =
        synchronized(lock) {
            open = null
            // The steps that were read from the file are used up: the next older ones are read now.
            if (undoing && undoStack.isEmpty() && loader?.hasMore == true) loadMore()
            (if (undoing) undoStack else redoStack).lastOrNull()
        }

    /** Reads the next older steps of the history file: [HISTORY_WINDOW] that can be undone, and once the redo steps. */
    private fun loadMore() {
        val chunk = loader?.read(HISTORY_WINDOW) ?: return
        undoStack.addAll(0, chunk.older)
        chunk.redo?.let {
            redoStack.clear()
            redoStack.addAll(it)
        }
        refresh()
    }

    /**
     * The step [peek] gave was undone (or redone) by [applied] (an [UndoEntry] or [RedoEntry] is made): it moves to the
     * other stack. Returns the number of the entry, which the layers of the pages it changed are to carry.
     */
    fun commit(
        undoing: Boolean,
        applied: List<Change>,
    ): Int =
        synchronized(lock) {
            val step = (if (undoing) undoStack else redoStack).removeLast()
            (if (undoing) redoStack else undoStack).addLast(step)
            val seq = nextSeq++
            pending += if (undoing) UndoEntry(seq, step.seq, applied) else RedoEntry(seq, step.seq, applied)
            refresh()
            seq
        }

    /** The step [peek] gave does not fit its pages: it is dropped (it can be neither undone nor redone). */
    fun discard(undoing: Boolean) {
        synchronized(lock) {
            (if (undoing) undoStack else redoStack).removeLastOrNull()
            refresh()
        }
    }

    /** The saver could not write [entries] (from [drainPending]): they are pending again, before newer ones. */
    fun restore(entries: List<HistoryEntry>) {
        synchronized(lock) { pending.addAll(0, entries) }
    }

    private fun newStep(
        changes: List<Change>,
        typed: Typed?,
        now: Long,
        stampBefore: Int?,
    ): Int {
        val step = Step(nextSeq++, changes)
        undoStack.addLast(step)
        redoStack.clear()
        pending += EditEntry(step.seq, changes)
        open =
            typed?.let { TypingGroups.started(it.page, it.lineId, it.edit, now) }?.let { OpenTyping(it, stampBefore) }
        refresh()
        return step.seq
    }

    private fun extendOpen(
        current: OpenTyping,
        top: Step,
        joined: List<Change>,
        typed: Typed,
        now: Long,
    ): Int {
        undoStack[undoStack.lastIndex] = Step(top.seq, joined)
        // The open step is the newest entry, and nothing has taken the entries since (draining closes the step).
        check((pending.lastOrNull() as? EditEntry)?.seq == top.seq) { "The open step has no pending entry" }
        pending[pending.lastIndex] = EditEntry(top.seq, joined)
        current.run = TypingGroups.extended(current.run, typed.edit, now)
        return top.seq
    }

    private fun dropOpen(current: OpenTyping): Int? {
        undoStack.removeLast()
        pending.removeLast()
        nextSeq-- // it was the newest entry: the numbers stay without a gap
        open = null
        refresh()
        return current.stampBefore
    }

    private fun refresh() {
        canUndo = undoStack.isNotEmpty() || loader?.hasMore == true
        canRedo = redoStack.isNotEmpty()
    }

    companion object {
        /** How many steps that can be undone are read from the history file at a time. */
        const val HISTORY_WINDOW = 100

        /**
         * The history of a project from its file (T073): the newest [HISTORY_WINDOW] steps that can be undone and the
         * steps that can be redone are read now, older ones when undo needs them. The next entry gets the number after
         * the newest one of the file (1 for a project that has no history).
         *
         * @throws korrektor.format.FormatException if an entry that is read is damaged.
         */
        fun load(log: HistoryLog): UndoHistory {
            val history = UndoHistory(Any(), HistoryLoader(log), 1)
            synchronized(history.lock) {
                history.loadMore()
                history.nextSeq = (history.loader?.newestSeq ?: 0) + 1
            }
            return history
        }

        /**
         * The changes of two typing edits one after the other, as one; empty if they cancel out; null if they are not
         * two replacements of one line by one line in the same place (then they are not joined).
         */
        fun compose(
            first: List<Change>,
            second: List<Change>,
        ): List<Change>? {
            val a = first.singleOrNull() as? LineChange
            val b = second.singleOrNull() as? LineChange
            val fits =
                a != null &&
                    b != null &&
                    a.page == b.page &&
                    a.region == b.region &&
                    a.index == b.index &&
                    a.inserted.size == 1 &&
                    b.removed.size == 1 &&
                    a.inserted == b.removed
            return when {
                !fits || a == null || b == null -> null
                a.removed == b.inserted -> emptyList()
                else ->
                    listOf(
                        LineChange(a.page, a.region, a.index, a.removed, b.inserted, a.regionBefore, b.regionAfter),
                    )
            }
        }
    }
}
