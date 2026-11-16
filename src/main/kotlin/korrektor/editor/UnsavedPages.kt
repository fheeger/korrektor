package korrektor.editor

import korrektor.format.PageLayer

// What the saver needs to know about the pages with unsaved corrections (S07). Extensions, because `BookEditor` has
// as many functions as the code checks allow.

/**
 * A snapshot of the pages with unsaved changes and their current layers. Layers are immutable, so the saver can write
 * them while the user goes on editing: an edit makes a new layer and the page stays unsaved (see [markSaved]).
 */
fun BookEditor.unsaved(): Map<Int, PageLayer> =
    synchronized(changedLock) { changedPages.sorted().mapNotNull { page -> changed[page]?.let { page to it } }.toMap() }

/**
 * Says that [layer] of [page] is on disk. The page counts as saved only if [layer] is still its current layer, the
 * same object: if the user changed it while it was written, it stays unsaved and the next save writes the new layer.
 * Called by the saver, from any thread.
 */
fun BookEditor.markSaved(
    page: Int,
    layer: PageLayer,
) {
    synchronized(changedLock) {
        if (page in changedPages && changed[page] === layer) changedPages = changedPages - page
    }
}
