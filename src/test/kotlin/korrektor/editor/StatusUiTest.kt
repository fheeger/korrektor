package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Metadata
import korrektor.format.PageStatus
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The page status and the progress in the whole window (S09). */
@OptIn(ExperimentalTestApi::class)
class StatusUiTest {
    /** A book of three pages with two lines each. */
    private fun withOpenProject(
        pages: Int = 3,
        block: ComposeUiTest.(AppModel) -> Unit,
    ) {
        val dir = Files.createTempDirectory("statusui")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                List(pages) { n ->
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
                waitUntil(timeoutMillis = 10_000) { exists(field("l2")) }
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

    private fun ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun exists(node: SemanticsNodeInteraction) = runCatching { node.assertExists() }.isSuccess

    private fun ComposeUiTest.progress() = onNode(hasTestTag("progress"))

    private fun ComposeUiTest.ctrl(
        key: Key,
        shift: Boolean = false,
    ) {
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForPage(
        model: AppModel,
        page: Int,
    ) {
        waitUntil(timeoutMillis = 10_000) { model.editor?.page == page && model.editor?.layer != null }
        waitForIdle()
    }

    private fun ComposeUiTest.status(model: AppModel) = checkNotNull(model.editor).statuses

    @Test
    fun aNewBookIsUncheckedAndTheProgressIsZero() =
        withOpenProject {
            progress().assertTextEquals("0 of 3 corrected (0 %)")
            onNodeWithText("Page 1 of 3 · unchecked").assertExists()
        }

    @Test
    fun theStatusAndTheProgressFollowAChangeAndItsUndo() =
        withOpenProject { model ->
            val editor = checkNotNull(model.editor)
            editor.setStatus(1, PageStatus.CORRECTED)
            waitForIdle()
            progress().assertTextEquals("1 of 3 corrected (33 %)")
            onNodeWithText("Page 1 of 3 · corrected").assertExists()

            editor.setStatus(3, PageStatus.CORRECTED)
            waitForIdle()
            progress().assertTextEquals("2 of 3 corrected (67 %)")
            onNodeWithText("Page 1 of 3 · corrected").assertExists()

            editor.undo()
            editor.undo()
            waitForIdle()
            progress().assertTextEquals("0 of 3 corrected (0 %)")
            onNodeWithText("Page 1 of 3 · unchecked").assertExists()
        }

    @Test
    fun ctrlEnterMarksThePageCorrectedAndOpensTheNextOneWithTheCursorInItsFirstLine() =
        withOpenProject { model ->
            ctrl(Key.Enter)
            waitForPage(model, 2)

            assertEquals(mapOf(1 to PageStatus.CORRECTED), status(model))
            onNodeWithText("Page 2 of 3 · unchecked").assertExists()
            field("l1").assertIsFocused()

            ctrl(Key.Z)
            waitForPage(model, 1)
            assertEquals(emptyMap(), status(model))
            onNodeWithText("Page 1 of 3 · unchecked").assertExists()
        }

    @Test
    fun undoingCtrlEnterThroughTheKeyPutsTheCursorInTheFirstLineOfThePageAgain() =
        withOpenProject { model ->
            ctrl(Key.Enter)
            waitForPage(model, 2)
            ctrl(Key.Z)
            waitForPage(model, 1)

            assertEquals(emptyMap(), status(model))
            field("l1").assertIsFocused()
            ctrl(Key.Enter) // the keys still arrive: the focus was not lost
            waitForPage(model, 2)
        }

    @Test
    fun ctrlEnterOnTheLastPageMarksItAndStaysAndOnACorrectedPageKeepsItCorrected() =
        withOpenProject { model ->
            checkNotNull(model.viewer).goTo(3)
            waitForPage(model, 3)
            ctrl(Key.Enter)
            assertEquals(mapOf(3 to PageStatus.CORRECTED), status(model))
            onNodeWithText("Page 3 of 3 · corrected").assertExists()

            ctrl(Key.Enter)
            assertEquals(mapOf(3 to PageStatus.CORRECTED), status(model))
            assertEquals(1, checkNotNull(model.editor).history.undoSteps.size, "the same status is no new step")
        }

    @Test
    fun ctrlShiftEnterMarksThePageUncheckedAndStays() =
        withOpenProject { model ->
            checkNotNull(model.editor).setStatus(1, PageStatus.CORRECTED)
            waitForIdle()

            ctrl(Key.Enter, shift = true)

            assertEquals(emptyMap(), status(model))
            onNodeWithText("Page 1 of 3 · unchecked").assertExists()
            progress().assertTextEquals("0 of 3 corrected (0 %)")
        }

    @Test
    fun theButtonChangesTheStatusInBothDirectionsAndStaysOnThePage() =
        withOpenProject { model ->
            onNodeWithText("Mark as corrected").performClick()
            waitForIdle()
            assertEquals(mapOf(1 to PageStatus.CORRECTED), status(model))
            assertEquals(1, checkNotNull(model.viewer).page)
            onNodeWithText("Mark as unchecked").assertExists()
            onNodeWithText("Page 1 of 3 · corrected").assertExists()

            onNodeWithText("Mark as unchecked").performClick()
            waitForIdle()
            assertEquals(emptyMap(), status(model))
            onNodeWithText("Mark as corrected").assertExists()
            field("l1").assertIsFocused()
        }

    @Test
    fun editingACorrectedPageDoesNotChangeItsStatus() =
        withOpenProject { model ->
            checkNotNull(model.editor).setStatus(1, PageStatus.CORRECTED)
            field("l1").performTextReplacement("Eins geändert")
            waitForIdle()

            assertEquals(mapOf(1 to PageStatus.CORRECTED), status(model))
        }

    @Test
    fun theKeyWorksWithTheFocusInThePageField() =
        withOpenProject { model ->
            onNode(hasContentDescription("Go to page")).performClick()
            ctrl(Key.Enter)
            waitForPage(model, 2)

            assertEquals(mapOf(1 to PageStatus.CORRECTED), status(model))
        }

    @Test
    fun ctrlShiftDownAndUpJumpBetweenTheUncheckedPages() =
        withOpenProject(pages = 6) { model ->
            val editor = checkNotNull(model.editor)
            for (page in listOf(2, 3, 5)) editor.setStatus(page, PageStatus.CORRECTED)
            waitForIdle()

            ctrl(Key.DirectionDown, shift = true)
            waitForPage(model, 4)
            field("l1").assertIsFocused()
            ctrl(Key.DirectionDown, shift = true)
            waitForPage(model, 6)
            ctrl(Key.DirectionDown, shift = true) // none after page 6: nothing happens
            waitForIdle()
            assertEquals(6, checkNotNull(model.viewer).page)

            ctrl(Key.DirectionUp, shift = true)
            waitForPage(model, 4)
            ctrl(Key.DirectionUp, shift = true)
            waitForPage(model, 1)
            ctrl(Key.DirectionUp, shift = true)
            waitForIdle()
            assertEquals(1, checkNotNull(model.viewer).page)
            assertEquals(3, editor.history.undoSteps.size, "the jumps are no steps and the text is not touched")
        }

    @Test
    fun aPageThatWasJustCorrectedIsNotTheNextUncheckedOne() =
        withOpenProject(pages = 6) { model ->
            checkNotNull(model.viewer).goTo(4)
            waitForPage(model, 4)
            ctrl(Key.Enter) // page 4 corrected, page 5 shown
            waitForPage(model, 5)
            checkNotNull(model.viewer).goTo(4)
            waitForPage(model, 4)

            ctrl(Key.DirectionDown, shift = true)
            waitForPage(model, 5)
        }
}
