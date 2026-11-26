package korrektor.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import korrektor.characters.inserting
import korrektor.characters.takesSpecialCharacters
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    // The search of the page shown follows the page and its text.
    LaunchedEffect(model.editor.page, model.editor.layer) { model.pageChanged() }
    val replaceMarked: () -> Unit = {
        // Like the disabled button: nothing happens (and the cursor stays where it is) with nothing checked.
        if (!model.replacing && model.selectedCount > 0) {
            scope.launch {
                model.replaceSelected()
                onLeave() // the click took the keyboard away from the text: Ctrl+Z must work again
            }
        }
    }
    val replaceOne: () -> Unit = {
        scope.launch {
            model.replaceAndGoOn(onOpenResult, onStep)
            // After the last hit there is no result to go to, and the button is gone: the keyboard goes to the text.
            if (model.current < 0 && model.results.isEmpty()) onLeave()
        }
    }
    // The shortcuts ask through the model; requests from before this panel was shown are not ours.
    var handledOne by remember { mutableIntStateOf(model.replaceOneRequests) }
    var handledMarked by remember { mutableIntStateOf(model.replaceMarkedRequests) }
    LaunchedEffect(model.replaceOneRequests) {
        if (model.replaceOneRequests != handledOne) {
            handledOne = model.replaceOneRequests
            replaceOne()
        }
    }
    LaunchedEffect(model.replaceMarkedRequests) {
        if (model.replaceMarkedRequests != handledMarked) {
            handledMarked = model.replaceMarkedRequests
            replaceMarked()
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("search panel")) {
        QueryRow(model, onStep, onLeave)
        if (model.showReplace) ReplaceRow(model, onStep, onLeave)
        SummaryRow(model)
        if (model.showReplace) {
            SelectionRow(model, replaceMarked, replaceOne) { onStep(true) }
        }
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
    val accept = { typed: TextFieldValue ->
        field = typed
        if (typed.text != model.query.text) model.change(model.query.copy(text = typed.text))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Toggle(if (model.showReplace) "▾" else "▸", model.showReplace, "replace toggle") {
            model.showReplace = it
        }
        TextBox(
            field,
            accept,
            "Find",
            Modifier
                .weight(1f)
                .focusRequester(focus)
                // While the field has the keyboard, stepping through the hits does not take it away (see `focusHeld`).
                .onFocusChanged { model.editor.focusHeld = it.isFocused }
                .takesSpecialCharacters { accept(field.inserting(it)) }
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
private fun ReplaceRow(
    model: SearchModel,
    onStep: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TOGGLE_ALIGN))
        var field by remember { mutableStateOf(TextFieldValue(model.replacement)) }
        val accept = { typed: TextFieldValue ->
            field = typed
            model.replacement = typed.text
        }
        TextBox(
            field,
            accept,
            "Replace with",
            Modifier
                .weight(1f)
                .takesSpecialCharacters { accept(field.inserting(it)) }
                .onPreviewKeyEvent { event -> fieldKey(event, model, onStep, onLeave) }
                .testTag("replace field"),
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
        model.outcome?.let { Text(it, Modifier.testTag("replace outcome"), fontSize = 13.sp) }
        model.message?.let {
            Text(it, Modifier.testTag("search message"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (model.searching) TextButton(model::cancel, Modifier.testTag("search cancel")) { Text("Cancel") }
    }
}

/**
 * In replace mode: how many hits are checked with buttons to check all or none, "Replace selected" (all checked hits
 * in one go), and "Replace" and "Skip" for going through the hits one by one; and why a replacement is not valid.
 */
@Composable
private fun SelectionRow(
    model: SearchModel,
    onReplaceSelect: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
) {
    if (model.results.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val count = "${model.selectedCount} of ${model.results.size} selected"
        Text(count, Modifier.testTag("selected count"), fontSize = 13.sp)
        TextButton(model::selectAll, Modifier.testTag("select all")) { Text("Select all") }
        TextButton(model::selectNone, Modifier.testTag("select none")) { Text("Select none") }
        val ready = !model.replacing && model.replacementProblem() == null
        Button(onReplaceSelect, Modifier.testTag("replace selected"), enabled = ready && model.selectedCount > 0) {
            Text("Replace selected")
        }
        OutlinedButton(onReplace, Modifier.testTag("replace one"), enabled = ready) { Text("Replace") }
        OutlinedButton(onSkip, Modifier.testTag("skip"), enabled = !model.replacing) { Text("Skip") }
        model.replacementProblem()?.let {
            Text(it, Modifier.testTag("replacement problem"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
