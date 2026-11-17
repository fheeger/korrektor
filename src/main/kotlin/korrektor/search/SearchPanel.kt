package korrektor.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The panel for finding and replacing (S10), between the status bar and the scan and text: the search field with its
 * options and the scope, the replace field if it is shown, a line that says how the search is going, and the list of
 * results. [onOpenResult] shows a result (its index in `model.results`); [onStep] goes to the next (true) or the
 * previous hit (Enter and Shift+Enter in the field); [onLeave] gives the cursor back to the text when the panel is
 * closed with Escape.
 */
@Composable
fun SearchPanel(
    model: SearchModel,
    onOpenResult: (Int) -> Unit,
    onStep: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The search of the page shown follows the page and its text.
    LaunchedEffect(model.editor.page, model.editor.layer) { model.pageChanged() }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("search panel")) {
        QueryRow(model, onStep, onLeave)
        if (model.showReplace) ReplaceRow(model)
        SummaryRow(model)
        if (model.showReplace) SelectionRow(model)
        ResultList(model, onOpenResult)
    }
}

@Composable
private fun QueryRow(
    model: SearchModel,
    onStep: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(model.query.text)) }
    // Ctrl+F and Ctrl+H: the cursor goes into the field and its text is selected, so that typing replaces it.
    DisposableEffect(Unit) { onDispose { model.editor.focusHeld = false } }
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
                // While the field has the keyboard, stepping through the hits does not take it away (see `focusHeld`).
                .onFocusChanged { model.editor.focusHeld = it.isFocused }
                .onPreviewKeyEvent { event -> fieldKey(event, model, onStep, onLeave) }
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

/**
 * The keys of the search field that are not typing: Escape closes the panel and the cursor goes back to the text,
 * Enter goes to the next hit and Shift+Enter to the previous one. Returns whether the key was used.
 */
private fun fieldKey(
    event: KeyEvent,
    model: SearchModel,
    onStep: (Boolean) -> Unit,
    onLeave: () -> Unit,
): Boolean {
    val down = event.type == KeyEventType.KeyDown
    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
    return when {
        down && event.key == Key.Escape -> {
            model.hide()
            onLeave()
            true
        }
        down && enter && !event.isCtrlPressed -> {
            onStep(!event.isShiftPressed)
            true
        }
        else -> false
    }
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

/** In replace mode: how many hits are checked, buttons to check all or none, and why a replacement is not valid. */
@Composable
private fun SelectionRow(model: SearchModel) {
    if (model.results.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${model.selectedCount} of ${model.results.size} selected",
            Modifier.testTag("selected count"),
            fontSize = 13.sp,
        )
        TextButton(model::selectAll, Modifier.testTag("select all")) { Text("Select all") }
        TextButton(model::selectNone, Modifier.testTag("select none")) { Text("Select none") }
        model.replacementProblem()?.let {
            Text(it, Modifier.testTag("replacement problem"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
