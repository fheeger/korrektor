package korrektor.editor

import korrektor.format.HistoryEntry
import korrektor.format.SavedEntry
import korrektor.format.entrySeq
import java.util.concurrent.CancellationException

/**
 * The part of a save that writes the history (S08, T072): the entries of the changes that the pages of this save
 * contain, before the pages, and the `saved` entry after them. It knows the newest entry that is in the file but not
 * yet followed by `saved`, so that a save whose pages failed still ends with `saved` once a later save writes them.
 *
 * @param append appends entries to the history file in one write; a failure is an exception and leaves none of them.
 */
internal class HistoryWriter(
    private val editor: BookEditor,
    private val append: (List<HistoryEntry>) -> Unit,
) {
    // The newest entry that is in the history file but is not followed by a `saved` entry yet; 0 if there is none.
    private var uncommitted = 0

    /** Whether an entry waits for its `saved` entry. */
    val hasUncommitted: Boolean get() = uncommitted != 0

    /**
     * Appends the entries of [taken]. If that fails, nothing of them is in the file and they are pending again, and
     * the result says why; the save is a failed one.
     */
    fun append(taken: SaveSnapshot): SaveOutcome.Failed? {
        val newest = taken.entries.mapNotNull { it.entrySeq }.maxOrNull()
        return if (newest == null) null else appendOrRestore(taken, newest)
    }

    private fun appendOrRestore(
        taken: SaveSnapshot,
        newest: Int,
    ): SaveOutcome.Failed? =
        try {
            append(taken.entries)
            uncommitted = newest
            null
        } catch (e: CancellationException) {
            editor.history.restore(taken.entries)
            throw e
        } catch (
            // The history could not be written: nothing of this save was, and the entries wait for the retry.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            editor.history.restore(taken.entries)
            SaveOutcome.Failed(taken.layers.keys.firstOrNull() ?: 0, describeFailure(e))
        }

    /** Says that everything up to the newest entry is in the page files. A failure is tried again by the next save. */
    fun markSaved(): SaveOutcome.Failed? {
        val upTo = uncommitted
        return if (upTo == 0) {
            null
        } else {
            try {
                append(listOf(SavedEntry(upTo)))
                uncommitted = 0
                null
            } catch (
                // The pages are written; the entry is tried again with the next save.
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                SaveOutcome.Failed(0, describeFailure(e))
            }
        }
    }
}
