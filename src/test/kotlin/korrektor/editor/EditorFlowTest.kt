package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import korrektor.viewer.next
import korrektor.viewer.previous
import korrektor.viewer.toScreen
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The whole of S05 in one run on a generated book, so that CI covers it: scan, text, edits and pages together. */
@OptIn(ExperimentalTestApi::class)
class EditorFlowTest {
    private fun ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** The app on a book of two pages (three lines, one line), opened; the project folder is checked at the end. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("editorflow")
        try {
            val pdf = dir.resolve("book.pdf")
            val first =
                listOf(
                    TestText("Erste Zeile", 30f, 250f, 14f),
                    TestText("zwoite Zeile", 30f, 220f, 14f),
                    TestText("Dritte", 30f, 190f, 14f),
                )
            TestPdfs.write(
                pdf,
                listOf(TestPage(texts = first), TestPage(texts = listOf(TestText("Seite zwei", 30f, 250f, 14f)))),
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)

            fun snapshot() =
                project.folder.walk().filter { Files.isRegularFile(it) }.associate {
                    it.toString() to
                        it.readBytes()
                }
            val before = snapshot()
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }, Modifier.size(1100.dp, 800.dp)) }
                onNodeWithText("Open project…").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess }
                waitUntil(timeoutMillis = 10_000) { runCatching { field("l3").assertExists() }.isSuccess }
                waitForIdle()
                block(model)
            }
            val after = snapshot()
            assertEquals(before.keys, after.keys, "no file added")
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun ComposeUiTest.clickLineOnScan(
        model: AppModel,
        index: Int,
    ) {
        val box = model.editor!!.lines[index].box!!
        val at = model.viewer!!.toScreen(Box(box.x + box.width / 2, box.y + box.height / 2, 0.0, 0.0))
        onNodeWithContentDescription("Page 1").performMouseInput { click(Offset(at.x.toFloat(), at.y.toFloat())) }
        waitForIdle()
    }

    private fun ComposeUiTest.press(
        id: String,
        key: Key,
    ) {
        field(id).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun clickingALineOnTheScanThenTypingMarksTheLineAndGivesItsRowTheCursor() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)

            clickLineOnScan(model, 1)
            assertEquals("l2", editor.activeLineId)
            field("l2").assertIsFocused()
            onNodeWithContentDescription("Highlighted line: l2").assertExists()

            field("l2").performTextReplacement("zweite Zeile") // the OCR read "zwoite"
            assertEquals(listOf("Erste Zeile", "zweite Zeile", "Dritte"), editor.lines.map { it.text })
        }

    @Test
    fun aLineCanBeSplitInsideAWordAndJoinedAgain() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            runOnIdle { editor.requestFocus("l2", 9) } // zwoite Ze|ile
            waitForIdle()

            press("l2", Key.Enter)
            assertEquals(listOf("Erste Zeile", "zwoite Ze", "ile", "Dritte"), editor.lines.map { it.text })
            val second = editor.lines[2].id
            field(second).assertIsFocused()

            press(second, Key.Backspace)
            assertEquals(listOf("Erste Zeile", "zwoite Zeile", "Dritte"), editor.lines.map { it.text })
        }

    @Test
    fun bothPanelsAgreeAfterGoingToThePageAfterAndBackAndTheCorrectionIsStillThere() =
        withOpenProject { model ->
            val editor = assertNotNull(model.editor)
            val viewer = assertNotNull(model.viewer)
            field("l2").performTextReplacement("zweite Zeile")

            runOnIdle { viewer.next() }
            waitUntil(timeoutMillis = 10_000) { editor.page == 2 && editor.layer != null }
            waitForIdle()
            assertEquals("Seite zwei", field("l1").text())
            assertEquals(2, viewer.page)
            onNodeWithContentDescription("Page 2").assertExists()

            runOnIdle { viewer.previous() }
            waitUntil(timeoutMillis = 10_000) { editor.page == 1 && editor.layer != null }
            waitForIdle()
            assertEquals("zweite Zeile", field("l2").text())
            assertEquals(setOf(1), editor.changedPages)
        }
}
