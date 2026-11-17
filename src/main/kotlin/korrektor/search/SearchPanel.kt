package korrektor.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import korrektor.editor.HistoricFonts

/** The most that the list of results takes of the window; it scrolls beyond that. */
private val RESULTS_HEIGHT = 180.dp
private val FIELD_SHAPE = RoundedCornerShape(4.dp)

/**
 * The panel for finding and replacing (S10), between the status bar and the scan and text: the search field with its
 * options and the scope, the replace field if it is shown, a line that says how the search is going, and the list of
 * results. [onOpenResult] shows a result (its index in `model.results`); [onLeave] gives the cursor back to the text
 * when the panel is closed with Escape.
 */
@Composable
fun SearchPanel(
    model: SearchModel,
    onOpenResult: (Int) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The search of the page shown follows the page and its text.
    LaunchedEffect(model.editor.page, model.editor.layer) { model.pageChanged() }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("search panel")) {
        QueryRow(model, onLeave)
        if (model.showReplace) ReplaceRow(model)
        SummaryRow(model)
        ResultList(model, onOpenResult)
    }
}

@Composable
private fun QueryRow(
    model: SearchModel,
    onLeave: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(model.query.text)) }
    // Ctrl+F and Ctrl+H: the cursor goes into the field and its text is selected, so that typing replaces it.
    LaunchedEffect(model.fieldRequests) {
        field = field.copy(selection = TextRange(0, field.text.length))
        focus.requestFocus()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Toggle(if (model.showReplace) "▾" else "▸", model.showReplace, "replace toggle") {
            model.showReplace = it
        }
        TextBox(
            field,
            { typed ->
                field = typed
                if (typed.text != model.query.text) model.change(model.query.copy(text = typed.text))
            },
            "Find",
            Modifier
                .weight(1f)
                .focusRequester(focus)
                .onPreviewKeyEvent { event -> closeOnEscape(event.type, event.key, model, onLeave) }
                .testTag("search field"),
        )
        val query = model.query
        Toggle("Match case", query.matchCase, "match case") { model.change(query.copy(matchCase = it)) }
        Toggle("Whole words", query.wholeWords, "whole words") { model.change(query.copy(wholeWords = it)) }
        Toggle("Regex", query.regex, "regex") { model.change(query.copy(regex = it)) }
        Toggle("This page", model.scopeOf == SearchScope.PAGE, "scope page") {
            if (it) model.changeScope(SearchScope.PAGE)
        }
        Toggle("Whole book", model.scopeOf == SearchScope.BOOK, "scope book") {
            if (it) model.changeScope(SearchScope.BOOK)
        }
        TextButton({
            model.hide()
            onLeave()
        }, Modifier.testTag("search close")) { Text("✕") }
    }
}

/** Escape closes the panel and the cursor goes back to the text. */
private fun closeOnEscape(
    type: KeyEventType,
    key: Key,
    model: SearchModel,
    onLeave: () -> Unit,
): Boolean {
    val escape = type == KeyEventType.KeyDown && key == Key.Escape
    if (escape) {
        model.hide()
        onLeave()
    }
    return escape
}

@Composable
private fun ReplaceRow(model: SearchModel) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TOGGLE_ALIGN))
        var field by remember { mutableStateOf(TextFieldValue(model.replacement)) }
        TextBox(
            field,
            {
                field = it
                model.replacement = it.text
            },
            "Replace with",
            Modifier.weight(1f).testTag("replace field"),
        )
    }
}

/** The width of the toggle in front of the search field, so that the replace field starts under it. */
private val TOGGLE_ALIGN = 36.dp

@Composable
private fun SummaryRow(model: SearchModel) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val summary = searchSummary(model)
        if (summary.isNotEmpty()) Text(summary, Modifier.testTag("search summary"), fontSize = 13.sp)
        model.message?.let {
            Text(it, Modifier.testTag("search message"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (model.searching) TextButton(model::cancel, Modifier.testTag("search cancel")) { Text("Cancel") }
    }
}

@Composable
private fun ResultList(
    model: SearchModel,
    onOpenResult: (Int) -> Unit,
) {
    if (model.results.isEmpty()) return
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = RESULTS_HEIGHT).testTag("search results")) {
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
            ) {
                Text(
                    "p. ${match.page}, line ${match.lineNumber}",
                    Modifier.width(RESULT_PLACE_WIDTH),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
                Text(contextOf(match.snippet()), fontFamily = HistoricFonts.family, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

private val RESULT_PLACE_WIDTH = 110.dp

@Composable
private fun contextOf(snippet: Snippet) =
    buildAnnotatedString {
        append(snippet.before)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer)) {
            append(snippet.found)
        }
        append(snippet.after)
    }

/** A small button that is on or off (an option, the scope): it says so to the screen reader as a checkbox does. */
@Composable
private fun Toggle(
    text: String,
    on: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .border(1.dp, if (on) colors.primary else colors.outline, FIELD_SHAPE)
            .background(if (on) colors.primaryContainer else colors.surface, FIELD_SHAPE)
            .toggleable(on, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
    ) {
        Text(text, fontSize = 13.sp)
    }
}

/** A one-line text field with a border and a hint that shows while it is empty. */
@Composable
private fun TextBox(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value,
        onChange,
        modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                FIELD_SHAPE,
            ).padding(horizontal = 8.dp, vertical = 6.dp),
        singleLine = true,
        textStyle =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = HistoricFonts.family,
                fontSize = 15.sp,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.outline, fontSize = 15.sp)
                inner()
            }
        },
    )
}
