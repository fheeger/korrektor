package korrektor.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class EditLinesTest {
    private fun words(
        text: String,
        y: Double,
    ) = text.split(' ').mapIndexed { i, t -> Word(t, Box(10.0 + 30.0 * i, y, 25.0, 10.0)) }

    private val page1 =
        PageLayer(
            1,
            200.0,
            200.0,
            listOf(
                Region(
                    "r1",
                    RegionType.TEXT,
                    1,
                    null,
                    listOf(
                        Line("l1", Box(10.0, 10.0, 90.0, 10.0), "l1", words("Von der Kriegsfüh-", 10.0)),
                        Line("l2", Box(10.0, 24.0, 90.0, 10.0), "l2", words("rnng iſt nuͤtzlich", 24.0)),
                        Line("l3", Box(10.0, 38.0, 30.0, 10.0), "l3", words("Ende", 38.0)),
                    ),
                ),
            ),
        )
    private val page2 =
        PageLayer(
            2,
            200.0,
            200.0,
            listOf(Region("r1", RegionType.TEXT, 1, null, listOf(Line("l1", null, null, listOf(Word("zwei")))))),
        )

    private fun editor(): BookEditor {
        val pages = mapOf(1 to page1, 2 to page2)
        return BookEditor({ pages.getValue(it) }, Dispatchers.IO)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field(id: String): SemanticsNodeInteraction =
        onNode(hasTestTag("line text $id"), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun SemanticsNodeInteraction.cursor() = fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun BookEditor.line(id: String) = lines.first { it.id == id }

    // --- typing -------------------------------------------------------------------------------------------

    @Test
    fun typingChangesTheLineInTheEditorAtOnceAndNothingElse() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            field("l2").performTextReplacement("rung iſt nuͤtzlich")

            assertEquals("rung iſt nuͤtzlich", editor.line("l2").text)
            assertEquals(setOf(1), editor.changedPages)
            assertEquals(page1.regions[0].lines[0], editor.line("l1"))
            assertEquals(page1.regions[0].lines[2], editor.line("l3"))
            assertEquals("rung iſt nuͤtzlich", field("l2").text(), "the row shows what the editor has")
        }

    @Test
    fun theCharactersAreStoredAsTyped() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            for (typed in listOf(
                "ſtraße ꝛc. uͤber",
                "zwei  Leerzeichen",
                " führend und folgend ",
                "Haupt=",
                "Kriegsfüh-",
            )) {
                field("l3").performTextReplacement(typed)
                assertEquals(typed, editor.line("l3").text, "'$typed'")
            }
            field("l3").performTextReplacement("")
            field("l3").performTextInput("ſ ꝛ")
            assertEquals("ſ ꝛ", editor.line("l3").text)
        }

    @Test
    fun theSameNumberOfWordsKeepsTheWordBoxesAndAnotherNumberDropsThem() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            val boxes =
                page1.regions[0]
                    .lines[1]
                    .words
                    .map { it.box }

            field("l2").performTextReplacement("rung iſt nützlich")
            assertEquals(boxes, editor.line("l2").words.map { it.box }, "a corrected word is still where it was")
            assertEquals(Box(10.0, 24.0, 90.0, 10.0), editor.line("l2").box)

            field("l2").performTextReplacement("rung iſt nützlich und")
            assertEquals(List(4) { null }, editor.line("l2").words.map { it.box })
            assertEquals(Box(10.0, 24.0, 90.0, 10.0), editor.line("l2").box, "the line keeps its place on the scan")
        }

    @Test
    fun pastedLineBreaksBecomeSpaces() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            field("l3").performTextReplacement("zwei\nZeilen\r\nund mehr")

            assertEquals("zwei Zeilen  und mehr", editor.line("l3").text)
            assertEquals("zwei Zeilen  und mehr", field("l3").text())
        }

    // --- the editor changes the text from outside -------------------------------------------------------------

    @Test
    fun aRowFollowsTheEditorWhenItsTextChangesFromOutside() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            runOnIdle { editor.joinWithPrevious("l2") } // l2 is appended to l1

            waitForIdle()
            assertEquals("Von der Kriegsfüh-rnng iſt nuͤtzlich", field("l1").text())
            onNodeWithText("3").assertDoesNotExist() // two rows are left
            assertEquals("Ende", field("l3").text())
        }

    // --- the active line and the cursor ----------------------------------------------------------------------

    @Test
    fun theRowWithTheFocusIsTheActiveLine() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            field("l2").performClick()
            assertEquals("l2", editor.activeLineId)
            field("l3").performClick()
            assertEquals("l3", editor.activeLineId)
        }

    @Test
    fun aFocusRequestPutsTheCursorWhereItIsAsked() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            runOnIdle { editor.requestFocus("l2", 5) } // rnng |iſt
            waitForIdle()

            field("l2").assertIsFocused()
            assertEquals(5, field("l2").cursor().start)
            assertEquals("l2", editor.activeLineId)

            runOnIdle { editor.requestFocus("l1", 1000) } // beyond the end: at the end
            waitForIdle()
            field("l1").assertIsFocused()
            assertEquals("Von der Kriegsfüh-".length, field("l1").cursor().start)
        }

    @Test
    fun anAddedLineGetsTheCursorAtOnce() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }

            runOnIdle { editor.addLineAfter("l1") }
            waitForIdle()

            val added = editor.lines[1].id
            field(added).assertIsFocused()
            assertEquals(0, field(added).cursor().start)
        }

    // --- pages and the project ----------------------------------------------------------------------------

    @Test
    fun goingToAnotherPageAndBackShowsTheEditedText() =
        runComposeUiTest {
            val editor = editor()
            runBlocking { editor.show(1) }
            setContent { TextPanel(editor) }
            field("l1").performTextReplacement("Geändert")

            runBlocking { editor.show(2) }
            waitForIdle()
            assertEquals("zwei", field("l1").text())
            runBlocking { editor.show(1) }
            waitForIdle()

            assertEquals("Geändert", field("l1").text())
        }

    @Test
    fun editingInTheWholeAppLeavesTheProjectFolderUntouched() {
        val dir = Files.createTempDirectory("editlines")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(
                pdf,
                listOf(
                    TestPage(texts = listOf(TestText("Erste Zeile", 30f, 250f), TestText("zweite Zeile", 30f, 230f))),
                ),
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
                setContent { App(model, { project.folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 5_000) { runCatching { field("l1").assertExists() }.isSuccess }

                field("l1").performTextReplacement("Korrigierte Zeile ſo")
                waitForIdle()

                val editor = assertNotNull(model.editor)
                assertEquals("Korrigierte Zeile ſo", editor.line("l1").text)
                assertEquals(setOf(1), editor.changedPages)
            }
            val after = snapshot()
            assertEquals(before.keys, after.keys)
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }
}
