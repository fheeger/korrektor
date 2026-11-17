package korrektor.editor

import korrektor.format.Change
import korrektor.format.FormatException
import korrektor.format.LineChange
import korrektor.format.PageLayer

// Undo and redo (S08). Extensions, like the line moves of LineNavigation.kt: they use the editor's layers and history.

/**
 * Takes back the last step of the book, on whatever page it is (also one that was never shown in this session). The
 * pages become unsaved and the step can be brought back with [redo]. If the step is on another page, the result says
 * which: the caller shows that page, and the cursor goes to the place of the change when it is there (`pendingFocus`).
 */
fun BookEditor.undo(): UndoResult = move(undoing = true)

/** Brings back the step that [undo] took back last, as [undo] does. */
fun BookEditor.redo(): UndoResult = move(undoing = false)

private fun BookEditor.move(undoing: Boolean): UndoResult =
    synchronized(changedLock) {
        val step =
            try {
                history.peek(undoing)
            } catch (e: FormatException) {
                // Older history is read when the loaded steps are used up; a damaged or unreadable file is no crash.
                return@synchronized UndoResult.Failed(e.message ?: "The history cannot be read")
            }
        // To undo a sequence of changes, the inverted changes are made in the reverse order.
        val changes = step?.changes?.let { if (undoing) it.reversed().map(Change::inverted) else it }
        if (step == null || changes == null) {
            UndoResult.NothingToDo
        } else {
            when (val applied = applyAll(changes)) {
                is Applied.Layers -> {
                    val seq = history.commit(undoing, changes)
                    for ((number, layer) in applied.layers) put(number, layer.copy(history = seq))
                    for ((number, status) in applied.statuses) putStatus(number, status)
                    onUnsaved?.invoke()
                    done(changes, applied.layers).also { arrive(it) }
                }
                Applied.DoesNotFit -> {
                    history.discard(undoing)
                    UndoResult.DoesNotFit
                }
                is Applied.Unreadable -> UndoResult.Failed(applied.reason)
            }
        }
    }

/** Where the cursor goes after the changes: at the first one, which is where the user finds the page. */
private fun done(
    changes: List<Change>,
    layers: Map<Int, PageLayer>,
): UndoResult.Done {
    val first = changes.first()
    val focus = (first as? LineChange)?.let { change -> layers[change.page]?.let { focusAfter(change, it) } }
    return UndoResult.Done(first.page, focus)
}

/**
 * Where the cursor belongs when [change] has just been made on [layer]:
 * - a line whose text changed: at the end of what changed, in the text that is there now (after the restored text
 *   when undoing typing, after the typed text when redoing it);
 * - lines that were joined into one: at the place where they were joined;
 * - lines that were split or added: at the start of the first new line that is not the first part;
 * - lines that were removed: at the end of the line above, or the start of the one below.
 */
internal fun focusAfter(
    change: LineChange,
    layer: PageLayer,
): Focus? {
    val removed = change.removed
    val inserted = change.inserted
    return when {
        inserted.isEmpty() -> neighbour(change, layer)
        removed.size == 1 && inserted.size == 1 && removed[0].id == inserted[0].id -> {
            val edit = TypingGroups.textEdit(removed[0].text, inserted[0].text)
            Focus(inserted[0].id, edit.at + edit.inserted)
        }
        inserted.size < removed.size ->
            Focus(inserted.first().id, minOf(removed.first().text.length, inserted.first().text.length))
        else -> Focus(inserted.getOrElse(removed.size) { inserted.last() }.id, 0)
    }
}

/** The end of the line above where lines were removed, else the start of the line below, else the page's first line. */
private fun neighbour(
    change: LineChange,
    layer: PageLayer,
): Focus? {
    val lines =
        layer.regions
            .firstOrNull { it.id == change.region }
            ?.lines
            .orEmpty()
    return lines.getOrNull(change.index - 1)?.let { Focus(it.id, it.text.length) }
        ?: lines.getOrNull(change.index)?.let { Focus(it.id, 0) }
        ?: PageEdits.readingOrder(layer).firstOrNull()?.let { Focus(it.id, 0) }
}

/** Puts the cursor where [result] says: now if its page is the one shown, else when that page is shown. */
private fun BookEditor.arrive(result: UndoResult.Done) {
    val focus = result.focus ?: return
    if (result.page == page &&
        layer != null
    ) {
        requestFocus(focus.lineId, focus.index)
    } else {
        pendingFocus = result.page to focus
    }
}
