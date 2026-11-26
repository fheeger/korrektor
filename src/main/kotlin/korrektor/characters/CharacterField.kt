package korrektor.characters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// A local, because only the fields at the ends of the tree need it, and everything between would just pass it on.

/** The [CharacterInput] of the window; the default serves a field that is shown on its own, as in a test. */
@Suppress("CompositionLocalAllowlist")
val LocalCharacterInput = compositionLocalOf { CharacterInput() }

/** This value with [added] typed into it: a selection is replaced, and the cursor is behind the new text. */
fun TextFieldValue.inserting(added: String): TextFieldValue =
    TextFieldValue(
        text.replaceRange(selection.min, selection.max, added),
        TextRange(selection.min + added.length),
    )

/**
 * Makes the text field with this modifier take special characters while it has the focus. [insert] is called with the
 * text to put at the cursor.
 */
@Composable
fun Modifier.takesSpecialCharacters(insert: (String) -> Unit): Modifier {
    val input = LocalCharacterInput.current
    val current by rememberUpdatedState(insert)
    val target = remember { CharacterTarget { current(it) } }
    // A field that goes away while it has the focus (a page change) does not report that it lost it.
    DisposableEffect(input) { onDispose { input.blurred(target) } }
    return onFocusChanged { if (it.isFocused) input.focused(target) else input.blurred(target) }
}
