package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppModel
import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.Metadata
import korrektor.format.PageLayer
import korrektor.format.Project
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TextPanelTest {
    private fun line(
        id: String,
        text: String,
    ) = Line(id, Box(10.0, 10.0, 50.0, 10.0), id, if (text.isEmpty()) emptyList() else text.split(' ').map { Word(it) })

    private fun layer(
        number: Int,
        vararg lines: Line,
        note: String? = null,
    ) = PageLayer(
        number,
        100.0,
        100.0,
        if (lines.isEmpty()) emptyList() else listOf(Region("r1", RegionType.TEXT, 1, null, lines.toList())),
        note = note,
    )

    private fun editorOf(vararg pages: PageLayer): BookEditor {
        val byNumber = pages.associateBy { it.pdfPage }
        return BookEditor({ n -> byNumber.getValue(n) }, Dispatchers.IO)
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun androidx.compose.ui.test.ComposeUiTest.rows() =
        onAllNodes(
            hasTestTag("line row l1") or hasTestTag("line row l2"),
        ).fetchSemanticsNodes().size

    // --- the lines ----------------------------------------------------------------------------------------

    @Test
    fun oneRowPerLineInReadingOrderWithTheirNumbers() =
        runComposeUiTest {
            val twoRegions =
                PageLayer(
                    1,
                    100.0,
                    100.0,
                    listOf(
                        Region("r2", RegionType.TEXT, 2, null, listOf(line("l3", "drei"))),
                        Region("r1", RegionType.TEXT, 1, null, listOf(line("l1", "eins"), line("l2", "zwei"))),
                    ),
                )
            val editor = editorOf(twoRegions)
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            assertEquals("eins", onNode(hasTestTag("line text l1"), useUnmergedTree = true).text())
            assertEquals("zwei", onNode(hasTestTag("line text l2"), useUnmergedTree = true).text())
            assertEquals("drei", onNode(hasTestTag("line text l3"), useUnmergedTree = true).text())
            onNodeWithText("1").assertExists()
            onNodeWithText("2").assertExists()
            onNodeWithText("3").assertExists()
            val tops =
                listOf(
                    "l1",
                    "l2",
                    "l3",
                ).map { onNodeWithTag("line row $it").fetchSemanticsNode().positionInRoot.y }
            assertEquals(tops.sorted(), tops, "from top to bottom in reading order")
        }

    @Test
    fun theTextIsShownExactlyAsStored() =
        runComposeUiTest {
            val texts =
                listOf(
                    "ſtraße uͤber ꝛc. Haupt=",
                    "Kriegsfüh-",
                    "zwei  Leerzeichen und   drei",
                    " führendes und folgendes ",
                    "x".repeat(600), // a very long line stays one line
                )
            val editor = editorOf(layer(1, *texts.mapIndexed { i, t -> line("l${i + 1}", t) }.toTypedArray()))
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor, Modifier.size(800.dp, 600.dp)) }

            for ((i, t) in texts.withIndex()) {
                assertEquals(
                    t,
                    onNode(hasTestTag("line text l${i + 1}"), useUnmergedTree = true).text(),
                    "line ${i + 1}",
                )
            }
        }

    // --- the states ---------------------------------------------------------------------------------------

    @Test
    fun thePanelSaysLoadingWhileThePageIsReadAndShowsTheLinesThen() =
        runComposeUiTest {
            val release = CompletableDeferred<Unit>()
            val editor =
                BookEditor({
                    runBlocking { release.await() }
                    layer(1, line("l1", "da"))
                }, Dispatchers.IO)
            setContent { TextPanel(editor) }
            val loading = kotlinx.coroutines.GlobalScope.launch { editor.show(1) }

            waitUntil { runCatching { onNodeWithText("Loading…").assertExists() }.isSuccess }
            release.complete(Unit)
            waitUntil {
                runCatching {
                    onNode(
                        hasTestTag("line text l1"),
                        useUnmergedTree = true,
                    ).assertExists()
                }.isSuccess
            }
            onNodeWithText("Loading…").assertDoesNotExist()
            loading.cancel()
        }

    @Test
    fun anErrorIsShownInsteadOfTheLines() =
        runComposeUiTest {
            val editor = BookEditor({ error("the page file is damaged") }, Dispatchers.IO)
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            onNodeWithText("The text of this page could not be read: the page file is damaged").assertExists()
        }

    @Test
    fun aPageWithoutLinesShowsItsNote() =
        runComposeUiTest {
            val editor = editorOf(layer(1, note = "No text on this page."), layer(2))
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            onNodeWithText("No text on this page.").assertExists()

            runBlocking { editor.show(2) }
            waitForIdle()
            onNodeWithText("No lines on this page.").assertExists()
        }

    @Test
    fun goingToAnotherPageChangesTheRows() =
        runComposeUiTest {
            val editor =
                editorOf(
                    layer(1, line("l1", "Seite eins")),
                    layer(2, line("l1", "Seite zwei"), line("l2", "noch eine")),
                )
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            assertEquals("Seite eins", onNode(hasTestTag("line text l1"), useUnmergedTree = true).text())

            runBlocking { editor.show(2) }
            waitForIdle()

            assertEquals("Seite zwei", onNode(hasTestTag("line text l1"), useUnmergedTree = true).text())
            assertEquals(2, rows())
        }

    // --- the active line ----------------------------------------------------------------------------------

    @Test
    fun clickingARowMakesItTheActiveLineAndMarksIt() =
        runComposeUiTest {
            val editor = editorOf(layer(1, line("l1", "eins"), line("l2", "zwei")))
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            onNodeWithTag("line row l1").assertIsNotSelected()

            onNodeWithTag("line row l2").performClick()

            assertEquals("l2", editor.activeLineId)
            onNodeWithTag("line row l2").assertIsSelected()
            onNodeWithTag("line row l1").assertIsNotSelected()
        }

    @Test
    fun theListScrollsToTheActiveLineWhenItChangesFromOutside() =
        runComposeUiTest {
            val many = (1..80).map { line("l$it", "Zeile $it") }
            val editor = editorOf(layer(1, *many.toTypedArray()))
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor, Modifier.size(400.dp, 200.dp)) } // room for about eight rows
            onNodeWithTag("line row l70").assertDoesNotExist()

            runOnIdle { editor.activate("l70") }
            waitForIdle()

            onNodeWithTag("line row l70").assertExists()
            onNodeWithTag("line row l70").assertIsSelected()
        }

    // --- in the app ---------------------------------------------------------------------------------------

    @Test
    fun theAppShowsTheLinesOfThePageTheViewerIsOn() {
        val dir = Files.createTempDirectory("textpanel")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                listOf(
                    TestPage(texts = listOf(TestText("Erste Seite", 30f, 250f), TestText("zweite Zeile", 30f, 230f))),
                    TestPage(texts = listOf(TestText("Seite zwei", 30f, 250f))),
                ),
            )
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent { App(model, { project.folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                waitUntil(
                    timeoutMillis = 5_000,
                ) {
                    runCatching {
                        onNode(
                            hasTestTag("line text l1"),
                            useUnmergedTree = true,
                        ).assertExists()
                    }.isSuccess
                }
                assertEquals("Erste Seite", onNode(hasTestTag("line text l1"), useUnmergedTree = true).text())
                assertEquals("zweite Zeile", onNode(hasTestTag("line text l2"), useUnmergedTree = true).text())

                runOnIdle { model.viewer!!.goTo(2) }
                waitUntil(timeoutMillis = 5_000) {
                    runCatching {
                        assertEquals(
                            "Seite zwei",
                            onNode(hasTestTag("line text l1"), useUnmergedTree = true).text(),
                        )
                    }.isSuccess
                }
                assertEquals(
                    1,
                    onAllNodes(hasTestTag("line row l1") or hasTestTag("line row l2")).fetchSemanticsNodes().size,
                )
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
