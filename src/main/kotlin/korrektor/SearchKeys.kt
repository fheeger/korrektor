package korrektor

import korrektor.editor.Focus
import korrektor.editor.showPlace
import korrektor.search.step

// What the search and replace panel does to the open project (S10): it shows a result. Extensions of the key actions,
// because `ProjectKeys` has as many functions as the code checks allow.

/**
 * Shows result [index] of the search: its page is opened, the cursor goes to the start of the hit in the text and the
 * scan highlights the line. The result becomes the current one.
 */
fun ProjectKeys.openResult(index: Int) {
    val search = model.search ?: return
    val match = search.results.getOrNull(index)
    val shown = if (match == null) null else model.editor?.let { editor -> model.viewer?.let { editor to it } }
    if (match != null && shown != null) {
        val (editor, viewer) = shown
        search.current = index
        editor.showPlace(match.page, Focus(match.lineId, match.start))
        if (viewer.page != match.page) viewer.goTo(match.page)
    }
}

/**
 * Goes to the next (or previous) hit of the search: the one after the hit shown last, or after the cursor if none was
 * shown yet; it wraps around at the end of the list. Nothing happens if there are no results (F3, Shift+F3, Enter in
 * the search field).
 */
fun ProjectKeys.stepMatch(forward: Boolean) {
    model.search?.step(forward)?.let { openResult(it) }
}
