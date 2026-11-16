package korrektor.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The five editing keys of the story, and the keys that must stay with the text field (T052), with real key events. */
@OptIn(ExperimentalTestApi::class)
class EditKeysUiTest {
    private fun layer(vararg texts: String) =
        PageLayer(
            1,
            300.0,
            200.0,
            if (texts.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    Region(
                        "r1",
                        RegionType.TEXT,
                        1,
                        null,
                        texts.mapIndexed {
                            i,
                            t,
                            ->
                            Line("l${i + 1}", null, null, t.split(' ').filter { it.isNotEmpty() }.map(::Word))
                        },
                    ),
                )
            },
        )

    private fun withPage(
        vararg texts: String,
        block: ComposeUiTest.(BookEditor) -> Unit,
    ) = runComposeUiTest {
        val editor = BookEditor({ layer(*texts) }, Dispatchers.IO)
        runBlocking { editor.show(1) }
        setContent { TextPanel(editor) }
        block(editor)
    }

    private fun ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.cursor() =
        fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].start

    private fun ComposeUiTest.cursorAt(
        editor: BookEditor,
        id: String,
        index: Int,
    ) {
        runOnIdle { editor.requestFocus(id, index) }
        waitForIdle()
    }

    private fun ComposeUiTest.press(
        id: String,
        key: Key,
        ctrl: Boolean = false,
    ) {
        field(id).performKeyInput {
            if (ctrl) keyDown(Key.CtrlLeft)
            pressKey(key)
            if (ctrl) keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun BookEditor.texts() = lines.map { it.text }

    @Test
    fun backspaceOnAnEmptyLineDeletesItAndTheCursorGoesToTheEndOfTheLineAbove() =
        withPage("Eins", "", "Drei") { editor ->
            cursorAt(editor, "l2", 0)
            press("l2", Key.Backspace)
            assertEquals(listOf("Eins", "Drei"), editor.texts())
            field("l1").assertIsFocused()
            assertEquals(4, field("l1").cursor())
        }

    @Test
    fun backspaceOnAnEmptyFirstLineDeletesItAndTheCursorGoesToTheStartOfTheNextLine() =
        withPage("", "Zwei", "Drei") { editor ->
            cursorAt(editor, "l1", 0)
            press("l1", Key.Backspace)
            assertEquals(listOf("Zwei", "Drei"), editor.texts())
            field("l2").assertIsFocused()
            assertEquals(0, field("l2").cursor())
        }

    @Test
    fun backspaceOnTheEmptyLastLineDeletesIt() =
        withPage("Eins", "Zwei", "") { editor ->
            cursorAt(editor, "l3", 0)
            press("l3", Key.Backspace)
            assertEquals(listOf("Eins", "Zwei"), editor.texts())
            field("l2").assertIsFocused()
            assertEquals(4, field("l2").cursor())
        }

    @Test
    fun backspaceOnTheOnlyLineOfThePageDeletesItAndTheKeyboardStillWorks() =
        withPage("") { editor ->
            cursorAt(editor, "l1", 0)
            press("l1", Key.Backspace)
            assertEquals(emptyList(), editor.texts())
            onNode(hasTestTag("text list")).assertIsFocused()
        }

    @Test
    fun backspaceAtTheStartOfALineThatHasTextStillJoins() =
        withPage("Eins", "Zwei") { editor ->
            cursorAt(editor, "l2", 0)
            press("l2", Key.Backspace)
            assertEquals(listOf("EinsZwei"), editor.texts())
        }

    @Test
    fun deleteOnAnEmptyLineJoinsTheNextLineIntoIt() =
        withPage("Eins", "", "Drei") { editor ->
            cursorAt(editor, "l2", 0)
            press("l2", Key.Delete)
            assertEquals(listOf("Eins", "Drei"), editor.texts())
            field("l2").assertIsFocused()
            assertEquals(0, field("l2").cursor())
        }

    @Test
    fun enterAtTheEndOfALineAddsAnEmptyLineBelowWithTheCursorInIt() =
        withPage("Eins", "Zwei") { editor ->
            cursorAt(editor, "l1", 4)
            press("l1", Key.Enter)
            assertEquals(listOf("Eins", "", "Zwei"), editor.texts())
            val added = editor.lines[1]
            assertEquals(null, added.box)
            assertEquals(emptyList(), added.words)
            field(added.id).assertIsFocused()
            assertEquals(
                editor.lines
                    .map { it.id }
                    .distinct()
                    .size,
                3,
                "a new id",
            )
        }

    @Test
    fun enterInTheMiddleOfALineSplitsIt() =
        withPage("Eins Zwei") { editor ->
            cursorAt(editor, "l1", 5)
            press("l1", Key.Enter)
            assertEquals(listOf("Eins", "Zwei"), editor.texts())
        }

    @Test
    fun backspaceAndDeleteWithASelectionDeleteTheSelection() =
        withPage("Eins", "Zwei") { editor ->
            cursorAt(editor, "l2", 0)
            field("l2").performTextInputSelection(TextRange(0, 2))
            press("l2", Key.Backspace)
            assertEquals(listOf("Eins", "ei"), editor.texts(), "no join, the selection went")
            field("l1").performTextInputSelection(TextRange(2, 4))
            press("l1", Key.Delete)
            assertEquals(listOf("Ei", "ei"), editor.texts())
        }

    @Test
    fun ctrlBackspaceAndCtrlDeleteStayWithTheFieldAndDeleteWords() =
        withPage("Eins Zwei Drei") { editor ->
            cursorAt(editor, "l1", 9)
            press("l1", Key.Backspace, ctrl = true)
            assertEquals(listOf("Eins  Drei"), editor.texts())
            cursorAt(editor, "l1", 0)
            press("l1", Key.Delete, ctrl = true)
            assertEquals(1, editor.lines.size, "no join")
        }

    @Test
    fun ctrlBackspaceAtTheStartOfALineDoesNotJoin() =
        withPage("Eins", "Zwei") { editor ->
            cursorAt(editor, "l2", 0)
            press("l2", Key.Backspace, ctrl = true)
            assertEquals(listOf("Eins", "Zwei"), editor.texts())
        }
}
