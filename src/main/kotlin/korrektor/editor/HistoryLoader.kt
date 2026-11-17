package korrektor.editor

import korrektor.format.Change
import korrektor.format.EditEntry
import korrektor.format.HistoryCodec
import korrektor.format.HistoryEntry
import korrektor.format.HistoryLog
import korrektor.format.RedoEntry
import korrektor.format.SavedEntry
import korrektor.format.UndoEntry
import korrektor.format.entrySeq

/** How many entries are read back at a time: the history is never read further than the steps need. */
private const val READ_BLOCK = 16

/**
 * Reads the history file backwards, from its end, as far as the steps that are wanted need (S08, T073): a long
 * history is never read as a whole. The file is a journal of edits, undos and redos, so the state of a step comes
 * from the latest entry that mentions it:
 * - an `edit` can be undone unless an `undo` that came after it is not followed by a `redo` ("settled" below);
 * - the steps that can be redone are those of the `undo` entries after the newest `edit`: an `undo` makes one
 *   redoable, a `redo` takes it away again; a new `edit` ended the redo steps before it.
 *
 * Entries are read newest first, so what a later entry says about a step is known when its `edit` is reached.
 */
internal class HistoryLoader(
    private val log: HistoryLog,
) {
    /** What one [read] found: older steps (oldest first), and the redo steps if they were found by this read. */
    class Chunk(
        val older: List<Step>,
        val redo: List<Step>?,
    )

    // The entries before this offset are still to be read.
    private var at = log.end

    // The number of an `edit` entry, with true if the latest entry that mentions it undid it.
    private val settled = HashMap<Int, Boolean>()

    // The `undo` and `redo` entries after the newest `edit` entry, newest first.
    private val trailing = ArrayList<HistoryEntry>()
    private var sawEdit = false

    /** The number of the newest entry that has one (the `upTo` of a `saved` entry counts), once it was read. */
    var newestSeq: Int? = null
        private set

    /** Whether entries older than those read so far exist. */
    val hasMore: Boolean get() = at > 0

    /**
     * Reads back until [wanted] more steps that can be undone are found, or the start of the file. Steps that were
     * undone and cannot be redone are skipped. Reads whole blocks: it may find a few more than wanted.
     */
    fun read(wanted: Int): Chunk {
        val found = ArrayDeque<Step>()
        var redo: List<Step>? = null
        while (at > 0 && found.size < wanted) {
            val block = log.readBefore(at, READ_BLOCK)
            at = if (block.entries.isEmpty()) 0 else block.start
            for (json in block.entries.asReversed()) {
                val entry = HistoryCodec.decode(json)
                if (newestSeq == null) newestSeq = (entry as? SavedEntry)?.upTo ?: entry.entrySeq
                if (entry is EditEntry && !sawEdit) redo = redoSteps()
                absorb(entry, found)
            }
        }
        // A history of nothing but undos and redos (it cannot be made by the app) has its redo steps at the end.
        if (at == 0L && !sawEdit) redo = redoSteps()
        return Chunk(found.toList(), redo)
    }

    private fun absorb(
        entry: HistoryEntry,
        found: ArrayDeque<Step>,
    ) {
        when (entry) {
            is EditEntry ->
                if (settled.putIfAbsent(entry.seq, false) !=
                    true
                ) {
                    found.addFirst(Step(entry.seq, entry.changes))
                }
            is UndoEntry -> {
                if (!sawEdit) trailing += entry
                settled.putIfAbsent(entry.step, true)
            }
            is RedoEntry -> {
                if (!sawEdit) trailing += entry
                settled.putIfAbsent(entry.step, false)
            }
            else -> Unit
        }
    }

    /** The steps that can be redone, from the `undo` and `redo` entries after the newest `edit`; the next one last. */
    private fun redoSteps(): List<Step> {
        sawEdit = true
        val stack = ArrayDeque<Step>()
        for (entry in trailing.asReversed()) {
            when (entry) {
                // An undo made the inverse of the step's changes in the reverse order: inverting again gives the step.
                is UndoEntry -> stack.addLast(Step(entry.step, entry.changes.reversed().map(Change::inverted)))
                is RedoEntry -> stack.removeLastOrNull()
                else -> Unit
            }
        }
        trailing.clear()
        return stack.toList()
    }
}
