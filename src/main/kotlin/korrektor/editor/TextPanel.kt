package korrektor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
) {
    val layer = editor.layer
    val lines = editor.lines
    Box(modifier.fillMaxSize().testTag("text list")) {
        when {
            editor.error != null -> Message("The text of this page could not be read: ${editor.error}", error = true)
            layer == null -> Message("Loading…")
            lines.isEmpty() -> Message(layer.note ?: "No lines on this page.")
            else -> LineList(editor, lines)
        }
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
    val actions = remember(editor) { RowActions(editor::activate, editor::setLineText) { wishes.remove(it) } }
    val numberWidth = (lines.size.toString().length * DIGIT_WIDTH + NUMBER_PADDING).dp
    LazyColumn(Modifier.fillMaxSize(), state) {
        itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
            LineRow(line, numberWidth, RowState(index + 1, line.id == active, wishes[line.id]), actions)
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
)

/** What a row can do; the same instance for every row. */
private class RowActions(
    val activate: (String?) -> Unit,
    val changeText: (String, String) -> Unit,
    val wishDone: (String) -> Unit,
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
        Text(
            state.number.toString(),
            Modifier.width(numberWidth).clickable { focus.requestFocus() },
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
        )
        BasicTextField(
            value = field,
            onValueChange = { typed ->
                val clean = typed.text.replace('\n', ' ').replace('\r', ' ')
                field = if (clean == typed.text) typed else typed.copy(text = clean)
                if (clean != line.text) actions.changeText(line.id, clean)
            },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onFocusChanged { if (it.isFocused) actions.activate(line.id) }
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
