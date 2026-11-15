package korrektor.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class LineOperationsUiTest {
    private fun words(
        text: String,
        y: Double,
    ) = text.split(' ').mapIndexed { i, t -> Word(t, Box(10.0 + 40.0 * i, y, 35.0, 10.0)) }

    private val page =
        PageLayer(
            1,
            300.0,
            200.0,
            listOf(
                Region(
                    "r1",
                    RegionType.TEXT,
                    1,
                    null,
                    listOf(
                        Line("l1", Box(10.0, 10.0, 130.0, 10.0), "l1", words("Von der Kriegsfüh-", 10.0)),
                        Line("l2", Box(10.0, 24.0, 130.0, 10.0), "l2", words("rung iſt nützlich", 24.0)),
                        Line("l3", null, null, listOf(Word("Anmerkung"))),
                    ),
                ),
            ),
        )
    private val empty = PageLayer(2, 300.0, 200.0, emptyList(), note = "No text on this page.")

    private fun editor(): BookEditor {
        val pages = mapOf(1 to page, 2 to empty)
        return BookEditor({ pages.getValue(it) }, Dispatchers.IO)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun SemanticsNodeInteraction.cursor() =
        fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].start

    private fun BookEditor.texts() = lines.map { it.text }

    private fun androidx.compose.ui.test.ComposeUiTest.press(
        id: String,
        key: Key,
    ) = field(id).performKeyInput { pressKey(key) }

    private fun androidx.compose.ui.test.ComposeUiTest.cursorAt(
        editor: BookEditor,
        id: String,
        index: Int,
    ) {
        runOnIdle { editor.requestFocus(id, index) }
        waitForIdle()
    }

    // --- Enter ------------------------------------------------------------------------------------------------

    @Test
    fun enterInTheMiddleOfALineSplitsItAndPutsTheCursorAtTheStartOfTheNewLine() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", 8) // Von der |Kriegsfüh-

            press("l1", Key.Enter)
            waitForIdle()

            assertEquals(listOf("Von der", "Kriegsfüh-", "rung iſt nützlich", "Anmerkung"), editor.texts())
            val newLine = editor.lines[1]
            assertNull(newLine.origin, "a new line has no origin")
            field(newLine.id).assertIsFocused()
            assertEquals(0, field(newLine.id).cursor())
            assertEquals(newLine.id, editor.activeLineId)
        }

    @Test
    fun enterAtTheEndAddsAnEmptyLineBelowWithoutABox() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l2", "rung iſt nützlich".length)

            press("l2", Key.Enter)
            waitForIdle()

            assertEquals(listOf("Von der Kriegsfüh-", "rung iſt nützlich", "", "Anmerkung"), editor.texts())
            assertNull(editor.lines[2].box)
            field(editor.lines[2].id).assertIsFocused()
        }

    @Test
    fun enterWithASelectionReplacesTheSelectionByTheBreak() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", 0)
            field("l1").performTextInputSelection(TextRange(3, 8)) // " der " is selected

            press("l1", Key.Enter)
            waitForIdle()

            assertEquals(listOf("Von", "Kriegsfüh-"), editor.texts().take(2))
        }

    // --- Backspace and Delete ---------------------------------------------------------------------------------

    @Test
    fun backspaceAtTheStartJoinsWithThePreviousLineAndPutsTheCursorAtTheJunction() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l2", 0)

            press("l2", Key.Backspace)
            waitForIdle()

            assertEquals(listOf("Von der Kriegsfüh-rung iſt nützlich", "Anmerkung"), editor.texts())
            field("l1").assertIsFocused()
            assertEquals("Von der Kriegsfüh-".length, field("l1").cursor())
        }

    @Test
    fun backspaceOnTheFirstLineChangesNothing() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", 0)

            press("l1", Key.Backspace)
            waitForIdle()

            assertEquals(3, editor.lines.size)
            assertEquals(emptySet(), editor.changedPages)
        }

    @Test
    fun backspaceInTheMiddleOfALineIsLeftToTheTextField() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l2", 4)

            press("l2", Key.Backspace)
            waitForIdle()

            assertEquals(3, editor.lines.size, "no join")
            assertEquals("run iſt nützlich", editor.lines[1].text, "one character was deleted")
        }

    @Test
    fun deleteAtTheEndJoinsTheNextLineAndKeepsTheCursor() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", "Von der Kriegsfüh-".length)

            press("l1", Key.Delete)
            waitForIdle()

            assertEquals(listOf("Von der Kriegsfüh-rung iſt nützlich", "Anmerkung"), editor.texts())
            assertEquals("Von der Kriegsfüh-".length, field("l1").cursor())
        }

    @Test
    fun deleteOnTheLastLineChangesNothing() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l3", "Anmerkung".length)

            press("l3", Key.Delete)
            waitForIdle()

            assertEquals(3, editor.lines.size)
            assertEquals(emptySet(), editor.changedPages)
        }

    // --- the commands -----------------------------------------------------------------------------------------

    @Test
    fun addLineAddsAnEmptyLineBelowTheActiveOneAndPutsTheCursorInIt() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", 3)

            onNodeWithText("Add line").performClick()
            waitForIdle()

            assertEquals(listOf("Von der Kriegsfüh-", "", "rung iſt nützlich", "Anmerkung"), editor.texts())
            val added = editor.lines[1]
            assertNull(added.box)
            assertNull(added.origin)
            field(added.id).assertIsFocused()
        }

    @Test
    fun deleteLineRemovesTheActiveLineAndTheCursorGoesToThePreviousOne() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            onNodeWithText("Delete line").assertIsNotEnabled() // no active line yet
            cursorAt(editor, "l2", 3)
            onNodeWithText("Delete line").assertIsEnabled()

            onNodeWithText("Delete line").performClick()
            waitForIdle()

            assertEquals(listOf("Von der Kriegsfüh-", "Anmerkung"), editor.texts())
            field("l1").assertIsFocused()
            assertEquals("Von der Kriegsfüh-".length, field("l1").cursor())
        }

    @Test
    fun addLineOnAPageWithoutLinesCreatesTheFirstOne() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(2) }
            setContent { TextPanel(editor) }
            onNodeWithText("No text on this page.").assertExists()

            onNodeWithText("Add line").performClick()
            waitForIdle()

            assertEquals(listOf(""), editor.texts())
            assertEquals(
                "r1",
                editor.layer!!
                    .regions
                    .single()
                    .id,
            )
            field(editor.lines[0].id).assertIsFocused()
        }

    @Test
    fun theCommandsAreOffWhileThePageIsNotThere() =
        runComposeUiTest {
            val editor = editor() // nothing shown yet: no layer
            setContent { TextPanel(editor) }

            onNodeWithText("Add line").assertIsNotEnabled()
            onNodeWithText("Delete line").assertIsNotEnabled()
        }

    // --- the marker ---------------------------------------------------------------------------------------------

    @Test
    fun linesWithoutABoxHaveAMarkerAndOthersDoNot() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            onNode(hasTestTag("no box l3"), useUnmergedTree = true).assertExists()
            onNode(hasTestTag("no box l1"), useUnmergedTree = true).assertDoesNotExist()
            onNode(hasTestTag("no box l2"), useUnmergedTree = true).assertDoesNotExist()

            cursorAt(editor, "l1", 10) // Von der Kr|iegsfüh-: the second half has no box
            press("l1", Key.Enter)
            waitForIdle()
            onNode(hasTestTag("no box ${editor.lines[1].id}"), useUnmergedTree = true).assertExists()
        }

    // --- the rows keep their identity ---------------------------------------------------------------------

    @Test
    fun aRowThatWasNotChangedKeepsItsFocusAndCursorWhenAnotherLineIsEdited() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l3", 4)

            runOnIdle { editor.setLineText("l1", "Geändert") }
            waitForIdle()

            field("l3").assertIsFocused()
            assertEquals(4, field("l3").cursor())
            assertEquals("l3", editor.activeLineId)
        }

    @Test
    fun nothingIsWrittenToTheOriginalLayerAndTheOldLayerIsNotChanged() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            val before = editor.layer
            setContent { TextPanel(editor) }
            cursorAt(editor, "l2", 5)

            press("l2", Key.Enter)
            waitForIdle()

            assertSame(page, before, "the layer that was shown before the edit is the same object, unchanged")
            assertNotNull(editor.layer)
            assertEquals(3, page.regions[0].lines.size)
            assertEquals(setOf(1), editor.changedPages)
        }

    @Test
    fun theEnterKeyOfTheNumpadSplitsToo() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            cursorAt(editor, "l1", 8)

            press("l1", Key.NumPadEnter)
            waitForIdle()

            assertEquals(listOf("Von der", "Kriegsfüh-", "rung iſt nützlich", "Anmerkung"), editor.texts())
        }
}
