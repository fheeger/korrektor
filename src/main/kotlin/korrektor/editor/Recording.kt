package korrektor.editor

import korrektor.format.Change
import korrektor.format.PageLayer

// How the edits of the editor become steps of the undo history (S08). Extensions, because `BookEditor` has as many
// functions as the code checks allow.

/**
 * Makes everything that [block] edits one undo step: Enter with a selected text replaces the selection and splits the
 * line, which is two edits of the editor but one key press. The step is made when the block ends, from the layer
 * before it to the layer after it; edits that cancel out leave no step.
 */
fun <T> BookEditor.oneStep(block: () -> T): T {
    grouping++
    try {
        return block()
    } finally {
        if (--grouping == 0) finishGroup()
    }
}

/**
 * Records a step that changes several pages, for example a "replace all" (S10): [changes] are the step, [layers] the
 * pages as they are after it. The pages become unsaved, each with the number of the step's entry.
 */
fun BookEditor.recordStep(
    changes: List<Change>,
    layers: Map<Int, PageLayer>,
) {
    synchronized(changedLock) {
        val seq = checkNotNull(history.record(changes)) { "A step that is not typing is never dropped" }
        for ((number, new) in layers) put(number, new.copy(history = seq))
    }
    onUnsaved?.invoke()
}

/** Records the edit of the page shown that turned [before] into [after]; [typed] is set if it was typing in a line. */
internal fun BookEditor.recordEdit(
    before: PageLayer,
    after: PageLayer,
    typed: Typed?,
) {
    if (grouping > 0) {
        if (groupStart == null) groupStart = before
        layer = after // only shown: the page and its step are put together when the block ends
    } else {
        synchronized(changedLock) {
            val stamp = history.record(diff(page, before, after), typed, now(), before.history)
            put(page, after.copy(history = stamp))
        }
    }
}

/** Makes [new] the layer of [number], and the page unsaved. The layer and its step must be made under the lock. */
internal fun BookEditor.put(
    number: Int,
    new: PageLayer,
) {
    synchronized(changedLock) {
        changed[number] = new
        changedPages = changedPages + number
    }
    if (number == page) layer = new
}

private fun BookEditor.finishGroup() {
    val start = groupStart ?: return
    groupStart = null
    val end = layer ?: return
    synchronized(changedLock) {
        val changes = diff(page, start, end)
        if (changes.isEmpty()) {
            put(page, start) // the edits cancelled out
        } else {
            put(page, end.copy(history = history.record(changes, null, now(), start.history)))
        }
    }
}
