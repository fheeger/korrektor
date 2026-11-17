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
