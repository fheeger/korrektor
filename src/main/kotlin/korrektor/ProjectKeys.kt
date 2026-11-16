package korrektor

import korrektor.editor.Arrival
import korrektor.editor.LineMove
import korrektor.editor.arriveAt
import korrektor.editor.firstLine
import korrektor.editor.lastLine
import korrektor.editor.moveLine
import korrektor.viewer.focusPageField

/**
 * What the shortcuts do in the open project: they move the cursor in the editor and the page in the viewer. The
 * actions of the lines (T050) and the pages (T051) are here; the zoom and list actions come with their tickets.
 */
class ProjectKeys(
    private val model: AppModel,
) : KeyActions {
    override fun perform(action: AppAction) {
        when (action) {
            AppAction.PREVIOUS_LINE -> moveLine(-1)
            AppAction.NEXT_LINE -> moveLine(1)
            AppAction.FIRST_LINE -> model.editor?.firstLine()
            AppAction.LAST_LINE -> model.editor?.lastLine()
            AppAction.NEXT_PAGE -> model.viewer?.let { openPage(it.page + 1, Arrival.FIRST) }
            AppAction.PREVIOUS_PAGE -> model.viewer?.let { openPage(it.page - 1, Arrival.FIRST) }
            AppAction.GO_TO_PAGE -> model.viewer?.focusPageField()
            else -> Unit
        }
    }

    /**
     * The page typed in the page field: opened with the cursor on its first line, or, if it is the page shown, the
     * cursor goes to its first line (the field has had the focus).
     */
    fun goToPage(page: Int) {
        val viewer = model.viewer ?: return
        if (page == viewer.page) {
            model.editor?.let { editor -> editor.lines.firstOrNull()?.let { editor.requestFocus(it.id, 0) } }
        } else {
            openPage(page, Arrival.FIRST)
        }
    }

    /** Escape in the page field: the cursor goes back to the active line, or to the first one. */
    fun leavePageField() {
        val editor = model.editor ?: return
        val line = editor.lines.firstOrNull { it.id == editor.activeLineId } ?: editor.lines.firstOrNull()
        line?.let { editor.requestFocus(it.id, editor.caret) }
    }

    /** Down on the last line goes to the first line of the next page, Up on the first to the last of the one before. */
    private fun moveLine(delta: Int) {
        val editor = model.editor
        val viewer = model.viewer
        if (editor != null && viewer != null) {
            when (editor.moveLine(delta)) {
                LineMove.NEXT_PAGE -> openPage(viewer.page + 1, Arrival.FIRST)
                LineMove.PREVIOUS_PAGE -> openPage(viewer.page - 1, Arrival.LAST)
                else -> Unit
            }
        }
    }

    /** Opens [page] if there is such a page: the editor follows the viewer and puts the cursor where it is wanted. */
    private fun openPage(
        page: Int,
        arrival: Arrival,
    ) {
        val editor = model.editor
        val viewer = model.viewer
        if (editor != null && viewer != null && page in 1..viewer.pageCount) {
            editor.arriveAt(page, arrival)
            viewer.goTo(page)
        }
    }
}
