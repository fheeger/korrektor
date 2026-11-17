package korrektor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import korrektor.format.Line

/** The width of one digit of the line numbers, in dp: the number column is as wide as the largest number needs. */
private const val DIGIT_WIDTH = 10

/** Room around the digits of the line numbers, in dp. */
private const val NUMBER_PADDING = 12

/**
 * The text of the page shown, one row per line in reading order, with the line number on the left and the text as
 * stored in the font for historical characters: no normalization, no trimming, no wrapping (a long line scrolls
 * sideways within its row, because a row is a line of the book). Each row is a text field: what the user types goes
 * to the editor at once (and nowhere else: saving is S07). The line with the cursor (`editor.activeLineId`)
 * is marked, and the list scrolls to keep it in view. While the page is read the panel says so, and if it cannot be
 * read, why. A page without lines shows the page's note.
 */
@Composable
fun TextPanel(
    editor: BookEditor,
    modifier: Modifier = Modifier,
    saver: AutoSaver? = null,
) {
    val layer = editor.layer
    val lines = editor.lines
    // Keys only reach the shortcuts if something has the focus. When the page changes the focused row is gone, so the
    // panel takes the focus until a row asks for it (the cursor arriving on the new page): while the page is read,
    // and for good on a page without lines.
    val panelFocus = remember { FocusRequester() }
    // Also when the last line of the page is deleted: the row that had the focus is gone.
    LaunchedEffect(editor.page, lines.isEmpty(), editor.panelFocusRequests) { panelFocus.requestFocus() }
    Column(
        modifier
            .fillMaxSize()
            .focusRequester(panelFocus)
            .focusable()
            .testTag("text list"),
    ) {
        LineCommands(editor, saver)
        Box(Modifier.fillMaxSize()) {
            when {
                editor.error != null ->
                    Message(
                        "The text of this page could not be read: ${editor.error}",
                        error = true,
                    )
                layer == null -> Message("Loading…")
                lines.isEmpty() -> Message(layer.note ?: "No lines on this page.")
                // A new page is a new list: the rows of the old page (same ids) must not keep the focus or the scroll.
                else -> key(editor.page) { LineList(editor, lines) }
            }
        }
    }
}

/**
 * "Add line" (below the active line, or at the end of the page) and "Delete line" (the active line). Enter, Backspace
 * and Delete in a row do the same and more (split and join); these two are for the mouse.
 */
@Composable
private fun LineCommands(
    editor: BookEditor,
    saver: AutoSaver?,
) {
    val ready = editor.layer != null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton({ editor.addLineAfter(editor.activeLineId) }, enabled = ready) { Text("Add line") }
        OutlinedButton({
            editor.activeLineId?.let(editor::deleteLine)
        }, enabled = ready && editor.activeLineId != null) { Text("Delete line") }
        if (saver != null) SaveIndicator(saver, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun Message(
    text: String,
    error: Boolean = false,
) {
    Text(
        text,
        Modifier.padding(12.dp),
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LineList(
    editor: BookEditor,
    lines: List<Line>,
) {
    val state = rememberLazyListState()
    val active = editor.activeLineId
    // The list follows the cursor: if the active line is not among the rows in view, it is scrolled to.
    LaunchedEffect(active, lines.size) { state.reveal(lines.indexOfFirst { it.id == active }) }

    // Where the cursor is wanted (after a split, a join, a click on the scan): the row carries it out when it is there.
    val wishes = remember(editor) { mutableStateMapOf<String, Int>() }
    val request = editor.focusRequest
    LaunchedEffect(request) {
        if (request != null) {
            wishes[request.focus.lineId] = request.focus.index
            state.reveal(lines.indexOfFirst { it.id == request.focus.lineId })
        }
    }

    // One instance of each action for all rows, so that a row whose line did not change is not recomposed.
    // They belong to this page: line ids repeat on every page, so a row that is left over from the page before (a field
    // that reports its text or its focus a moment late) must not change or activate a line of the page shown now.
    val listPage = editor.page
    val actions =
        remember(editor, listPage) {
            RowActions(
                { id -> if (editor.page == listPage) editor.activate(id) },
                { id, text -> if (editor.page == listPage) editor.setLineText(id, text) },
                { wishes.remove(it) },
                editor,
            )
        }
    val numberWidth = (lines.size.toString().length * DIGIT_WIDTH + NUMBER_PADDING).dp
    LazyColumn(Modifier.fillMaxSize(), state) {
        itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
            LineRow(
                line,
                numberWidth,
                RowState(index + 1, line.id == active, wishes[line.id], line.box != null),
                actions,
            )
        }
    }
}

/** Scrolls to row [index] if it is not in view (with the row above it), as long as there is such a row. */
private suspend fun LazyListState.reveal(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.map { it.index }
    if (index >= 0 && index !in visible) scrollToItem(maxOf(0, index - 1))
}

/** What the list tells a row besides its line: its number, whether it is active, and where the cursor is wanted. */
private data class RowState(
    val number: Int,
    val active: Boolean,
    val wish: Int?,
    val hasBox: Boolean,
)

/** What a row can do; the same instance for every row. */
private class RowActions(
    val activate: (String?) -> Unit,
    val changeText: (String, String) -> Unit,
    val wishDone: (String) -> Unit,
    val editor: BookEditor,
)

/**
 * One line of the book as a text field. The cursor and the selection belong to the row; the text belongs to the editor:
 * every change goes to `editor.setLineText` at once, and if the editor's text differs from the field's (a join, a
 * split, later an undo) the field follows it. A line break (a paste) becomes a space: a row is one line. Nothing is
 * corrected or completed for the user: what is typed is stored.
 */
@Composable
private fun LineRow(
    line: Line,
    numberWidth: Dp,
    state: RowState,
    actions: RowActions,
) {
    var field by remember { mutableStateOf(TextFieldValue(line.text)) }
    if (field.text !=
        line.text
    ) {
        field = TextFieldValue(line.text, TextRange(minOf(field.selection.end, line.text.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.wish) {
        val wish = state.wish
        if (wish != null) {
            field = field.copy(selection = TextRange(wish.coerceIn(0, field.text.length)))
            focus.requestFocus()
            actions.wishDone(line.id)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (state.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ).semantics { selected = state.active }
            .testTag("line row ${line.id}")
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberCell(line.id, state, numberWidth) { focus.requestFocus() }
        BasicTextField(
            value = field,
            onValueChange = { typed ->
                val clean = typed.text.replace('\n', ' ').replace('\r', ' ')
                field = if (clean == typed.text) typed else typed.copy(text = clean)
                actions.editor.caret = field.selection.end
                // Always passed on: `line` may be one keystroke behind, and the editor ignores a text it already has.
                actions.changeText(line.id, clean)
            },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onFocusChanged { if (it.isFocused) actions.activate(line.id) }
                    .onPreviewKeyEvent { event -> lineKey(event, line.id, field, actions.editor) }
                    .testTag("line text ${line.id}"),
            singleLine = true,
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = HistoricFonts.family,
                    fontSize = 17.sp,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The keys that change the lines. Enter splits the line at the cursor (a selection is replaced by the break, so the
 * selected text goes); Backspace at the start joins the line with the previous one (an empty line is deleted);
 * Delete at the end joins the next one into it. Anything else, also these keys with Ctrl, Alt or Meta, is left to the
 * text field. Returns whether the key was used.
 */
private fun lineKey(
    event: KeyEvent,
    lineId: String,
    field: TextFieldValue,
    editor: BookEditor,
): Boolean {
    val selection = field.selection
    return when {
        !event.isPlainKeyDown() -> false
        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
            // Removing the selection and cutting the line is one key press, so one undo step.
            editor.oneStep {
                if (!selection.collapsed) {
                    editor.setLineText(
                        lineId,
                        field.text.removeRange(selection.min, selection.max),
                    )
                }
                editor.splitLine(lineId, selection.min)
            }
            true
        }
        event.key == Key.Backspace && selection.collapsed && selection.start == 0 -> {
            // An empty line is deleted, not joined: it has nothing to add to the line above (and, as the first line of
            // the page, nothing to be joined to).
            if (field.text.isEmpty()) editor.deleteLine(lineId) else editor.joinWithPrevious(lineId)
            true
        }
        event.key == Key.Delete && selection.collapsed && selection.end == field.text.length ->
            editor.joinWithNext(lineId).let { true }
        else -> false
    }
}

/** A key press without Ctrl, Alt or Meta: those combinations are left to the text field. */
private fun KeyEvent.isPlainKeyDown() =
    type == KeyEventType.KeyDown && !isCtrlPressed && !isAltPressed && !isMetaPressed

/** The line number, and a dot if the line has no box: it has no place on the scan, so it is not highlighted there. */
@Composable
private fun NumberCell(
    lineId: String,
    state: RowState,
    width: Dp,
    onClick: () -> Unit,
) {
    Row(Modifier.width(width).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(state.number.toString(), color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
        if (!state.hasBox) {
            Text(
                "•",
                Modifier.padding(start = 2.dp).testTag("no box $lineId"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )
        }
    }
}
