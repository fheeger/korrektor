package korrektor.format

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
