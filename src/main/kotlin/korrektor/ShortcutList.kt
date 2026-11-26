package korrektor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import korrektor.characters.SpecialCharacters

private val MAX_HEIGHT = 640.dp

/** The list of keyboard shortcuts in a dialog (F1). F1 again, Escape and "Close" close it. */
@Composable
fun ShortcutListDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) { ShortcutList(onClose) }
}

/**
 * Every shortcut with the keys that trigger it and what it does, from the table in [Shortcuts], and the keys of the
 * text in a line. The content of the dialog, apart from the window, so that it can be tested.
 */
@Composable
fun ShortcutList(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val close = event.type == KeyEventType.KeyDown && (event.key == Key.F1 || event.key == Key.Escape)
                if (close) onClose()
                close
            }.testTag("shortcut list"),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        // The list scrolls inside a dialog of a bounded height, so that "Close" stays in view however many shortcuts.
        Column(
            Modifier.padding(24.dp).width(620.dp).heightIn(max = MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Keyboard shortcuts", style = MaterialTheme.typography.headlineSmall)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (shortcut in Shortcuts.all) {
                    Entry(
                        shortcut.combos.joinToString("   or   ") { it.text },
                        shortcut.description,
                        "shortcut ${shortcut.action}",
                    )
                }
                Text("In a line", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                for ((key, what) in Shortcuts.editorKeys) Entry(key, what, "editor key $key")
                Text("Special characters", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                for (character in SpecialCharacters.all) {
                    Entry(character.shortcut, "${character.text}   ${character.name}", "character ${character.name}")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun Entry(
    keys: String,
    what: String,
    tag: String,
) {
    Row(Modifier.testTag(tag), verticalAlignment = Alignment.Top) {
        Text(keys, Modifier.width(220.dp), style = MaterialTheme.typography.bodyMedium)
        Text(what, style = MaterialTheme.typography.bodyMedium)
    }
}
