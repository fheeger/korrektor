package korrektor.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
 * sideways within its row, because a row is a line of the book). The line with the cursor (`editor.activeLineId`)
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
    LaunchedEffect(active, lines.size) {
        val index = lines.indexOfFirst { it.id == active }
        val visible = state.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && index !in visible) state.scrollToItem(maxOf(0, index - 1))
    }
    val numberWidth = (lines.size.toString().length * DIGIT_WIDTH + NUMBER_PADDING).dp
    LazyColumn(Modifier.fillMaxSize(), state) {
        itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
            LineRow(line, index + 1, numberWidth, line.id == active) { editor.activate(line.id) }
        }
    }
}

@Composable
private fun LineRow(
    line: Line,
    number: Int,
    numberWidth: Dp,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { selected = active }
            .testTag("line row ${line.id}")
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number.toString(),
            Modifier.width(numberWidth),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
        )
        Text(
            line.text,
            Modifier.horizontalScroll(rememberScrollState()).testTag("line text ${line.id}"),
            fontFamily = HistoricFonts.family,
            fontSize = 17.sp,
            softWrap = false,
            maxLines = 1,
        )
    }
}
