package korrektor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The root key handler (T049) in the whole window: a shortcut reaches the actions once, wherever the focus is. */
@OptIn(ExperimentalTestApi::class)
class ShortcutsUiTest {
    private val performed = mutableListOf<AppAction>()

    private fun withOpenProject(block: ComposeUiTest.() -> Unit) {
        val dir = Files.createTempDirectory("shortcutsui")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f), TestText("Zweite", 30f, 220f, 14f))
            TestPdfs.write(pdf, listOf(TestPage(texts = texts), TestPage(texts = texts)))
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            runComposeUiTest {
                setContent {
                    App(
                        AppModel(),
                        { project.folder },
                        { null },
                        Modifier.size(1100.dp, 800.dp),
                        keyActions = { performed += it },
                    )
                }
                onNodeWithText("Open project…").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    runCatching { onNodeWithContentDescription("Page 1").assertExists() }.isSuccess
                }
                waitUntil(timeoutMillis = 10_000) { runCatching { field("l2").assertExists() }.isSuccess }
                waitForIdle()
                block()
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

    private fun SemanticsNodeInteraction.ctrl(key: Key) {
        requestFocusOf(this)
        performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(key)
            keyUp(Key.CtrlLeft)
        }
    }

    private fun requestFocusOf(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus)
    }

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun aShortcutInATextRowReachesTheActionsOnce() =
        withOpenProject {
            field("l1").ctrl(Key.DirectionDown)
            waitForIdle()
            assertEquals(listOf(AppAction.NEXT_PAGE), performed)
        }

    @Test
    fun aShortcutInTheGoToFieldReachesTheActionsOnce() =
        withOpenProject {
            onNodeWithContentDescription("Go to page").ctrl(Key.G)
            waitForIdle()
            assertEquals(listOf(AppAction.GO_TO_PAGE), performed)
        }

    @Test
    fun aShortcutWithTheFocusOnAButtonReachesTheActionsOnce() =
        withOpenProject {
            onNodeWithContentDescription("Zoom in").ctrl(Key.One)
            waitForIdle()
            assertEquals(listOf(AppAction.FIT_WIDTH), performed)
        }

    @Test
    fun aShortcutIsUsedUpSoTheFieldDoesNotActOnIt() =
        withOpenProject {
            val before = field("l1").text()
            field("l1").ctrl(Key.Zero)
            field("l1").performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            assertEquals(listOf(AppAction.FIT_PAGE, AppAction.NEXT_LINE), performed)
            assertEquals(before, field("l1").text(), "no character was typed")
        }

    @Test
    fun keysThatAreNotShortcutsAreLeftToTheField() =
        withOpenProject {
            field("l1").performKeyInput { pressKey(Key.Enter) }
            field("l2").performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
            assertEquals(emptyList(), performed)
        }
}
