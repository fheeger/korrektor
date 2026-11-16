package korrektor

import korrektor.editor.Arrival
import korrektor.editor.LineMove
import korrektor.editor.arriveAt
import korrektor.editor.firstLine
import korrektor.editor.lastLine
import korrektor.editor.moveLine

/**
 * What the shortcuts do in the open project: they move the cursor in the editor and the page in the viewer. The
 * actions of the lines are here (T050); the page, zoom and list actions come with their tickets.
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
            else -> Unit
        }
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
