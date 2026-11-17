package korrektor.editor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The page status and the progress in the whole window (S09). */
@OptIn(ExperimentalTestApi::class)
class StatusUiTest {
    /** A book of three pages with two lines each. */
    private fun withOpenProject(block: ComposeUiTest.(AppModel) -> Unit) {
        val dir = Files.createTempDirectory("statusui")
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
}
