package korrektor.format

import kotlinx.serialization.json.JsonObject

/**
 * One entry of the history of the book (`history/book.jsonl`, S08). Every entry that changes pages has a [seq], counted
 * up from 1 over all of them, and a list of [changes] that is applied forward as it is.
 */
sealed interface HistoryEntry

/** A step: the user changed the text. */
data class EditEntry(
    val seq: Int,
    val changes: List<Change>,
) : HistoryEntry

/**
 * Step [step] was undone. [changes] are what was done to the pages to undo it (the inverted changes of the step, in
 * reverse order), so applying them forward is what the entry did.
 */
data class UndoEntry(
    val seq: Int,
    val step: Int,
    val changes: List<Change>,
) : HistoryEntry

/** Step [step] was redone; [changes] are the changes of the step. */
data class RedoEntry(
    val seq: Int,
    val step: Int,
    val changes: List<Change>,
) : HistoryEntry

/** All entries up to [upTo] are in the page files: after a crash, only the entries after the last one are checked. */
data class SavedEntry(
    val upTo: Int,
) : HistoryEntry

/** An entry of a type this version does not know: kept as it is, so that a newer project loses nothing. */
data class UnknownEntry(
    val json: JsonObject,
) : HistoryEntry

/** The number of the entry, or null for an entry that has none (`saved`, and types this version does not know). */
val HistoryEntry.entrySeq: Int?
    get() =
        when (this) {
            is EditEntry -> seq
            is UndoEntry -> seq
            is RedoEntry -> seq
            else -> null
        }
