package korrektor.search

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The search panel in the whole window: the keys, the field, the results and a click on a result (T085). */
@OptIn(ExperimentalTestApi::class)
class SearchUiTest {
    /** A book of three pages with two lines each: `Eins n` and `Zwei`. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("searchui")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                List(3) { n ->
                    TestPage(
                        texts = listOf(TestText("Eins ${n + 1}", 30f, 250f, 14f), TestText("Zwei", 30f, 220f, 14f)),
                    )
                },
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 10_000) { exists(node("line text l2")) }
                waitForIdle()
                block(model)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun ComposeUiTest.node(tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag), useUnmergedTree = true)

    private fun exists(node: SemanticsNodeInteraction) = runCatching { node.assertExists() }.isSuccess

    private fun ComposeUiTest.ctrl(key: Key) {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(key)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.press(
        key: Key,
        shift: Boolean = false,
    ) {
        onRoot().performKeyInput {
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForPage(
        model: AppModel,
        page: Int,
    ) {
        val editor = checkNotNull(model.editor)
        waitUntil(timeoutMillis = 10_000) { editor.page == page && editor.layer != null }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForResults(
        model: AppModel,
        count: Int,
    ) {
        val search = checkNotNull(model.search)
        waitUntil(timeoutMillis = 10_000) { !search.searching && search.results.size == count }
        waitForIdle()
    }

    @Test
    fun ctrlFOpensThePanelWithoutTheReplaceFieldAndCtrlHWithIt() =
        withOpenProject { model ->
            assertFalse(exists(node("search panel")))
            ctrl(Key.F)
            node("search panel").assertExists()
            node("search field").assertIsFocused()
            assertFalse(exists(node("replace field")))
            assertTrue(checkNotNull(model.search).open)

            ctrl(Key.H)
            node("replace field").assertExists()
            node("search field").assertIsFocused()

            // The toggle shows and hides the field at any time; the panel stays.
            node("replace toggle").performClick()
            assertFalse(exists(node("replace field")))
            node("replace toggle").performClick()
            node("replace field").assertExists()
            ctrl(Key.F)
            node("replace field").assertExists() // Ctrl+F does not hide a field that is shown
        }

    @Test
    fun typingListsTheResultsAndTheOptionsChangeThem() =
        withOpenProject { model ->
            ctrl(Key.F)
            node("search field").performTextInput("zwei")
            waitForResults(model, 3)
            node("search summary").assertTextEquals("3 matches on 3 pages")
            node("result 0").assertExists()
            node("result 2").assertExists()

            node("match case").performClick()
            waitForResults(model, 0)
            node("search summary").assertTextEquals("No matches")

            node("match case").performClick()
            node("scope page").performClick()
            waitForResults(model, 1) // the page shown is page 1
            assertEquals(listOf(1), checkNotNull(model.search).results.map { it.page })
        }

    @Test
    fun aRegexThatIsNotValidIsAMessage() =
        withOpenProject { model ->
            ctrl(Key.F)
            node("regex").performClick()
            node("search field").performTextInput("(")
            waitForResults(model, 0)
            node("search message").assertExists()
        }

    @Test
    fun clickingAResultOpensItsPageAndPutsTheCursorOnTheMatch() =
        withOpenProject { model ->
            val editor = checkNotNull(model.editor)
            ctrl(Key.F)
            node("search field").performTextInput("Eins")
            waitForResults(model, 3)

            node("result 2").performClick()
            waitUntil(timeoutMillis = 10_000) { editor.page == 3 && editor.layer != null }
            waitForIdle()
            assertEquals(3, checkNotNull(model.viewer).page)
            assertEquals("l1", editor.activeLineId)
            assertEquals(2, checkNotNull(model.search).current)
            node("line text l1").assertIsFocused()
        }

    @Test
    fun escapeClosesThePanelAndTheCursorIsBackInTheText() =
        withOpenProject { model ->
            val editor = checkNotNull(model.editor)
            ctrl(Key.F)
            node("search field").performTextInput("Eins")
            waitForResults(model, 3)
            node("search field").performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertFalse(exists(node("search panel")))
            assertFalse(checkNotNull(model.search).open)
            waitUntil(timeoutMillis = 10_000) { editor.activeLineId != null }
            node("line text l1").assertIsFocused()

            // Opening it again finds the old query and its results.
            ctrl(Key.F)
            node("search panel").assertExists()
            assertEquals("Eins", checkNotNull(model.search).query.text)
            assertEquals(3, checkNotNull(model.search).results.size)
        }

    @Test
    fun f3AndShiftF3GoThroughTheHitsAndWrapAround() =
        withOpenProject { model ->
            val editor = checkNotNull(model.editor)
            ctrl(Key.F)
            node("search field").performTextInput("Zwei")
            waitForResults(model, 3)
            // The cursor is not in the text yet: the first F3 goes to the first hit.
            press(Key.F3)
            waitForPage(model, 1)
            assertEquals(0, checkNotNull(model.search).current)
            assertEquals("l2", editor.activeLineId)
            press(Key.F3)
            waitForPage(model, 2)
            assertEquals("l2", editor.activeLineId)
            press(Key.F3)
            waitForPage(model, 3)
            press(Key.F3)
            waitForPage(model, 1)
            assertEquals(0, checkNotNull(model.search).current, "after the last it wraps to the first")
            press(Key.F3, shift = true)
            waitForPage(model, 3)
            assertEquals(2, checkNotNull(model.search).current, "before the first it wraps to the last")
            press(Key.F3, shift = true)
            waitForPage(model, 2)
        }

    @Test
    fun enterInTheSearchFieldGoesOnAndTheFieldKeepsTheKeyboard() =
        withOpenProject { model ->
            val editor = checkNotNull(model.editor)
            ctrl(Key.F)
            node("search field").performTextInput("Zwei")
            waitForResults(model, 3)
            node("search field").performKeyInput { pressKey(Key.Enter) }
            waitForPage(model, 1)
            assertEquals("l2", editor.activeLineId, "the line is marked")
            node("search field").assertIsFocused()
            node("search field").performKeyInput { pressKey(Key.Enter) }
            waitForPage(model, 2)
            node("search field").assertIsFocused()
            node("search field").performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Enter)
                keyUp(Key.ShiftLeft)
            }
            waitForPage(model, 1)
            node("search field").assertIsFocused()
            // Nothing was typed into the book: Enter in the field never splits a line.
            assertEquals(2, editor.lines.size)
            assertEquals(0, editor.history.undoSteps.size)
        }

    @Test
    fun f3FromTheTextKeepsTheCursorInTheText() =
        withOpenProject { model ->
            ctrl(Key.F)
            node("search field").performTextInput("Zwei")
            waitForResults(model, 3)
            node("result 0").performClick()
            waitForPage(model, 1)
            node("line text l2").assertIsFocused()
            press(Key.F3)
            waitForPage(model, 2)
            node("line text l2").assertIsFocused()
            assertEquals(1, checkNotNull(model.search).current)
        }

    @Test
    fun f3WithNoResultsDoesNothing() =
        withOpenProject { model ->
            val viewer = checkNotNull(model.viewer)
            press(Key.F3)
            press(Key.F3, shift = true)
            assertEquals(1, viewer.page)
            assertEquals(-1, checkNotNull(model.search).current)
        }
}
