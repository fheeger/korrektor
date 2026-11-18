package korrektor.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import korrektor.editor.HistoricFonts

/** The most that the list of results takes of the window; it scrolls beyond that. */
private val RESULTS_HEIGHT = 180.dp
private val RESULT_PLACE_WIDTH = 110.dp

/**
 * The list of results. In replace mode every row has a checkbox and shows the line as it is and as it will be (before
 * → after); a replacement that is not valid shows no "after". Clicking a row shows the place; the checkbox only checks.
 */
@Composable
internal fun ResultList(
    model: SearchModel,
    onOpenResult: (Int) -> Unit,
) {
    if (model.results.isEmpty()) return
    val state = rememberLazyListState()
    // The list follows the hit that was gone to (F3): it is scrolled to if it is not in view.
    LaunchedEffect(model.current) {
        val shown = state.layoutInfo.visibleItemsInfo.map { it.index }
        if (model.current >= 0 && model.current !in shown) state.scrollToItem(maxOf(0, model.current - 1))
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = RESULTS_HEIGHT).testTag("search results"), state) {
        itemsIndexed(model.results) { index, match ->
            val selected = index == model.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onOpenResult(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("result $index"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.showReplace) {
                    Check(model.isSelected(index), "check $index") { model.setSelected(index, it) }
                }
                Text(
                    "p. ${match.page}, line ${match.lineNumber}",
                    Modifier.width(RESULT_PLACE_WIDTH),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
                val preview = if (model.showReplace) model.preview(match)?.getOrNull() else null
                if (preview == null) {
                    Text(
                        contextOf(match.snippet(), false),
                        fontFamily = HistoricFonts.family,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                } else {
                    Preview(match.snippet(null, PREVIEW_CONTEXT_SHOWN, PREVIEW_CONTEXT_SHOWN), preview)
                }
            }
        }
    }
}

/** How much of the line the row shows around the hit in a preview: both halves have to fit in one row. */
private const val PREVIEW_CONTEXT_SHOWN = 15

/** The line as it is, an arrow, and the line as it will be. */
@Composable
private fun Preview(
    before: Snippet,
    after: Snippet,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Half(before, replaced = false)
        Text("→", color = MaterialTheme.colorScheme.outline)
        Half(after, replaced = true)
    }
}

@Composable
private fun Half(
    snippet: Snippet,
    replaced: Boolean,
) {
    Text(
        contextOf(snippet, replaced),
        Modifier.width(PREVIEW_HALF),
        fontFamily = HistoricFonts.family,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val PREVIEW_HALF = 330.dp

/** The line with the hit marked; with [replaced] the text it is to become is marked instead. */
@Composable
private fun contextOf(
    snippet: Snippet,
    replaced: Boolean,
) = buildAnnotatedString {
    append(snippet.before)
    val colors = MaterialTheme.colorScheme
    val mark = if (replaced) colors.secondaryContainer else colors.tertiaryContainer
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = mark)) {
        append(if (replaced) snippet.replacement.orEmpty() else snippet.found)
    }
    append(snippet.after)
}

/** A checkbox: small, so that a row of the list stays as tall as its text. */
@Composable
private fun Check(
    on: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Text(
        if (on) "☑" else "☐",
        Modifier.toggleable(on, role = Role.Checkbox, onValueChange = onChange).testTag(tag),
        fontSize = 16.sp,
    )
}
