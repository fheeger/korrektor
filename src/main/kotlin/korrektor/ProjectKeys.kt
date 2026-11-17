package korrektor

import korrektor.editor.Arrival
import korrektor.editor.LineMove
import korrektor.editor.UndoResult
import korrektor.editor.arriveAt
import korrektor.editor.firstLine
import korrektor.editor.focusPanel
import korrektor.editor.lastLine
import korrektor.editor.moveLine
import korrektor.editor.redo
import korrektor.editor.undo
import korrektor.viewer.fitPage
import korrektor.viewer.fitWidth
import korrektor.viewer.focusPageField

/**
 * What the shortcuts do in the open project: they move the cursor in the editor and the page in the viewer. The
 * actions of the lines (T050), the pages (T051) and the zoom (T053) are here; F1 shows the list.
 */
class ProjectKeys(
    private val model: AppModel,
) : KeyActions {
    // A table instead of one big `when`: each action is one line, and an action with nothing to do is just not here.
    private val handlers: Map<AppAction, () -> Unit> =
        mapOf(
            AppAction.UNDO to { undoOrRedo(undoing = true) },
            AppAction.REDO to { undoOrRedo(undoing = false) },
            AppAction.PREVIOUS_LINE to { moveLine(-1) },
            AppAction.NEXT_LINE to { moveLine(1) },
            AppAction.FIRST_LINE to { model.editor?.firstLine() },
            AppAction.LAST_LINE to { model.editor?.lastLine() },
            AppAction.NEXT_PAGE to { model.viewer?.let { openPage(it.page + 1, Arrival.FIRST) } },
            AppAction.PREVIOUS_PAGE to { model.viewer?.let { openPage(it.page - 1, Arrival.FIRST) } },
            AppAction.GO_TO_PAGE to { model.viewer?.focusPageField() },
            AppAction.ZOOM_IN to { model.viewer?.zoomIn() },
            AppAction.ZOOM_OUT to { model.viewer?.zoomOut() },
            AppAction.FIT_PAGE to { model.viewer?.fitPage() },
            AppAction.FIT_WIDTH to { model.viewer?.fitWidth() },
            AppAction.SHOW_SHORTCUTS to { model.shortcutsOpen = !model.shortcutsOpen },
        )

    /** The actions that do something: all of them, which a test checks, so that no key or menu entry is dead. */
    val handledActions: Set<AppAction> get() = handlers.keys

    override fun perform(action: AppAction) {
        handlers[action]?.invoke()
    }

    /**
     * The page typed in the page field: opened with the cursor on its first line, or, if it is the page shown, the
     * cursor goes to its first line (the field has had the focus).
     */
    fun goToPage(page: Int) {
        val viewer = model.viewer ?: return
        if (page == viewer.page) {
            model.editor?.let { editor ->
                editor.lines.firstOrNull()?.let { editor.requestFocus(it.id, 0) }
                    ?: editor.focusPanel()
            }
        } else {
            openPage(page, Arrival.FIRST)
        }
    }

    /** Escape in the page field: the cursor goes back to the active line, or to the first one. */
    fun leavePageField() {
        val editor = model.editor ?: return
        val line = editor.lines.firstOrNull { it.id == editor.activeLineId } ?: editor.lines.firstOrNull()
        if (line != null) editor.requestFocus(line.id, editor.caret, keepColumn = true) else editor.focusPanel()
    }

    /**
     * Takes back (or brings back) the last change of the book. If it was on another page, that page is opened: the
     * editor keeps where the cursor is wanted and puts it there when the page is shown (T069).
     */
    private fun undoOrRedo(undoing: Boolean) {
        val editor = model.editor ?: return
        when (val result = if (undoing) editor.undo() else editor.redo()) {
            is UndoResult.Done -> model.viewer?.let { if (it.page != result.page) it.goTo(result.page) }
            UndoResult.DoesNotFit ->
                model.notice =
                    "That change does not fit the page any more (it was changed outside the app) and is dropped."
            is UndoResult.Failed -> model.error = "The change cannot be taken back: ${result.reason}"
            UndoResult.NothingToDo -> Unit
        }
    }

    /** Down on the last line goes to the first line of the next page, Up on the first to the last of the one before. */
    private fun moveLine(delta: Int) {
        val editor = model.editor
        val viewer = model.viewer
        // The editor follows the viewer a moment after it changed the page. In that moment the lines are those of the
        // page before: a key then would work out its move from the old page and open the wrong one, so it is dropped.
        if (editor != null && viewer != null && editor.page == viewer.page) {
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
