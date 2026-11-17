package korrektor

import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ctrl+S does nothing, silently (T063). */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class CtrlSTest {
    private fun event(
        ctrl: Boolean = true,
        shift: Boolean = false,
        alt: Boolean = false,
        type: KeyEventType = KeyEventType.KeyDown,
        key: Key = Key.S,
    ) = KeyEvent(key, type, isCtrlPressed = ctrl, isShiftPressed = shift, isAltPressed = alt)

    @Test
    fun ctrlSIsIgnoredAndIsNotAShortcutOfTheTable() {
        assertTrue(Shortcuts.isIgnored(event()))
        assertNull(Shortcuts.resolve(event()), "not an action: it is not in the menus or the list")
        assertTrue(Shortcuts.all.none { s -> s.combos.any { it.key == Key.S } })
    }

    @Test
    fun otherCombinationsOfSAreNotIgnored() {
        assertFalse(Shortcuts.isIgnored(event(ctrl = false)), "a plain s is typed")
        assertFalse(Shortcuts.isIgnored(event(shift = true)), "Ctrl+Shift+S")
        assertFalse(Shortcuts.isIgnored(event(alt = true)), "Ctrl+Alt+S, which AltGr+S is on Windows")
        assertFalse(Shortcuts.isIgnored(event(type = KeyEventType.KeyUp)))
        assertFalse(Shortcuts.isIgnored(event(key = Key.A)))
    }

    private fun withOpenProject(
        performed: MutableList<AppAction>,
        block: ComposeUiTest.(AppModel) -> Unit,
    ) {
        val dir = Files.createTempDirectory("ctrls")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f), TestText("Zweite", 30f, 220f, 14f))
            TestPdfs.write(pdf, List(2) { TestPage(texts = texts) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            runComposeUiTest {
                setContent {
                    App(model, { null }, { null }, Modifier.size(1100.dp, 800.dp), keyActions = { performed += it })
                }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 10_000) { model.editor?.lines?.isNotEmpty() == true }
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

    private fun SemanticsNodeInteraction.ctrlS() {
        performSemanticsAction(SemanticsActions.RequestFocus)
        performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
    }

    private fun ComposeUiTest.assertNothingChanged(
        model: AppModel,
        textBefore: String,
        pageBefore: Int,
        performed: List<AppAction>,
    ) {
        waitForIdle()
        assertEquals(emptyList(), performed, "no action")
        assertEquals(pageBefore, model.viewer!!.page)
        assertEquals(
            textBefore,
            model.editor!!
                .lines
                .first()
                .text,
            "no s typed",
        )
        assertEquals(emptySet(), model.editor!!.changedPages)
        assertNull(model.error)
        assertNull(model.notice)
        assertFalse(model.shortcutsOpen)
        assertNull(model.saver!!.warning)
        onNode(hasTestTag("save warning")).assertDoesNotExist()
    }

    @Test
    fun ctrlSInATextRowDoesNothing() {
        val performed = mutableListOf<AppAction>()
        withOpenProject(performed) { model ->
            val text =
                model.editor!!
                    .lines
                    .first()
                    .text
            onNode(hasTestTag("line text l1"), useUnmergedTree = true).ctrlS()
            assertNothingChanged(model, text, 1, performed)
            assertEquals("Erste Zeile", onNode(hasTestTag("line text l1"), useUnmergedTree = true).text())
        }
    }

    @Test
    fun ctrlSInThePageFieldAndOnAButtonDoesNothing() {
        val performed = mutableListOf<AppAction>()
        withOpenProject(performed) { model ->
            val text =
                model.editor!!
                    .lines
                    .first()
                    .text
            onNodeWithContentDescription("Go to page").ctrlS()
            assertNothingChanged(model, text, 1, performed)
            assertEquals("1", onNodeWithContentDescription("Go to page").text())
            onNodeWithContentDescription("Zoom in").ctrlS()
            assertNothingChanged(model, text, 1, performed)
        }
    }

    @Test
    fun ctrlSDoesNotStartASaveEarly() {
        val performed = mutableListOf<AppAction>()
        withOpenProject(performed) { model ->
            runOnIdle { model.editor!!.setLineText("l1", "geändert") }
            onNode(hasTestTag("line text l1"), useUnmergedTree = true).ctrlS()
            waitForIdle()
            assertEquals(setOf(1), model.editor!!.changedPages, "still waiting for the timer: nothing was written")
            assertFalse(model.saver!!.saving)
        }
    }

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text
}
