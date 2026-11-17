package korrektor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import korrektor.format.Change
import korrektor.format.EditEntry
import korrektor.format.HistoryEntry
import korrektor.format.LineChange

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
class UndoHistory(
    internal val lock: Any = Any(),
    firstSeq: Int = 1,
) {
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
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private companion object {
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
