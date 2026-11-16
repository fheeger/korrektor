package korrektor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ShortcutListTest {
    @Test
    fun everyShortcutIsListedWithItsKeysAndItsDescription() =
        runComposeUiTest {
            setContent { ShortcutList({}) }
            for (shortcut in Shortcuts.all) {
                val row = onNode(hasTestTag("shortcut ${shortcut.action}"))
                row.assertExists()
                onNodeWithText(shortcut.description).assertExists()
                for (combo in shortcut.combos) {
                    onNode(
                        hasTestTag("shortcut ${shortcut.action}") and
                            hasAnyDescendant(hasText(combo.text, substring = true)),
                    ).assertExists()
                }
            }
            onNodeWithText("Ctrl+Down").assertExists()
            onNodeWithText("F1").assertExists()
        }

    @Test
    fun theKeysOfTheTextInALineAreListedToo() =
        runComposeUiTest {
            setContent { ShortcutList({}) }
            for ((key, what) in Shortcuts.editorKeys) {
                onNode(hasTestTag("editor key $key")).assertExists()
                onNodeWithText(what).assertExists()
            }
        }

    @Test
    fun closeAndEscapeAndF1CloseTheList() =
        runComposeUiTest {
            var closed = 0
            setContent { ShortcutList({ closed++ }) }
            onNodeWithText("Close").performClick()
            assertEquals(1, closed)
            waitForIdle()
            onNode(hasTestTag("shortcut list")).performKeyInput { pressKey(Key.Escape) }
            assertEquals(2, closed)
            onNode(hasTestTag("shortcut list")).performKeyInput { pressKey(Key.F1) }
            assertEquals(3, closed)
            onNode(hasTestTag("shortcut list")).performKeyInput { pressKey(Key.A) }
            assertEquals(3, closed, "other keys do not close it")
        }
}
