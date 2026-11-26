package korrektor.characters

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import korrektor.App
import korrektor.AppAction
import korrektor.AppModel
import korrektor.ProjectKeys
import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.awt.Component
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import java.awt.event.KeyEvent as AwtKeyEvent

/** The special characters in the open project (T108), with the events a real keyboard sends. */
@OptIn(ExperimentalTestApi::class)
class CharacterUiTest {
    private val source = object : Component() {}

    // Compose has no public way to wrap an AWT event, and only an AWT event can be one that types a character.
    private val wrap =
        Class
            .forName("androidx.compose.ui.input.key.KeyEvent_desktopKt")
            .getMethod("toComposeEvent", AwtKeyEvent::class.java)

    private fun event(
        id: Int,
        code: Int,
        char: Char,
        modifiers: Int,
    ): KeyEvent {
        val location =
            if (id ==
                AwtKeyEvent.KEY_TYPED
            ) {
                AwtKeyEvent.KEY_LOCATION_UNKNOWN
            } else {
                AwtKeyEvent.KEY_LOCATION_STANDARD
            }
        return KeyEvent(wrap.invoke(null, AwtKeyEvent(source, id, 0L, modifiers, code, char, location))!!)
    }

    /** One key as a keyboard sends it: down, the character it types (if any), up. */
    private fun SemanticsNodeInteraction.stroke(
        code: Int,
        char: Char? = null,
        modifiers: Int = 0,
    ) {
        performKeyPress(event(AwtKeyEvent.KEY_PRESSED, code, char ?: AwtKeyEvent.CHAR_UNDEFINED, modifiers))
        if (char != null) performKeyPress(event(AwtKeyEvent.KEY_TYPED, AwtKeyEvent.VK_UNDEFINED, char, modifiers))
        performKeyPress(event(AwtKeyEvent.KEY_RELEASED, code, char ?: AwtKeyEvent.CHAR_UNDEFINED, modifiers))
    }

    private val ctrl = AwtKeyEvent.CTRL_DOWN_MASK
    private val ctrlShift = AwtKeyEvent.CTRL_DOWN_MASK or AwtKeyEvent.SHIFT_DOWN_MASK

    private fun SemanticsNodeInteraction.leader() = stroke(AwtKeyEvent.VK_K, modifiers = ctrl)

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.focus(tag: String): SemanticsNodeInteraction {
        val node = onNode(hasTestTag(tag))
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        return node
    }

    private fun withOpenProject(block: ComposeUiTest.(AppModel, ProjectKeys) -> Unit) {
        val dir = Files.createTempDirectory("characters")
        try {
            val pdf = dir.resolve("book.pdf")
            val texts = listOf(TestText("Erste Zeile", 30f, 250f, 14f), TestText("Zweite", 30f, 220f, 14f))
            TestPdfs.write(pdf, List(2) { TestPage(texts = texts) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            val model = AppModel()
            val keys = ProjectKeys(model)
            runComposeUiTest {
                setContent { App(model, { null }, { null }, Modifier.size(1100.dp, 800.dp)) }
                runOnIdle { model.openProject(project.folder) }
                waitUntil(timeoutMillis = 10_000) { model.editor?.lines?.isNotEmpty() == true }
                waitForIdle()
                block(model, keys)
            }
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun aTypedEventAloneTypes() =
        withOpenProject { _, _ ->
            // The control for the tests below: without it they would pass even if nothing were held back.
            val line = focus("line text l1")
            line.performTextInputSelection(TextRange(0))
            line.performKeyPress(event(AwtKeyEvent.KEY_TYPED, AwtKeyEvent.VK_UNDEFINED, 'r', 0))
            waitForIdle()
            assertEquals("rErste Zeile", line.text())
        }

    @Test
    fun aCombinationInsertsIntoTheLineOverTheSelectionAndCanBeUndone() =
        withOpenProject { model, keys ->
            val line = focus("line text l1")
            line.performTextInputSelection(TextRange(0, 5))
            line.stroke(AwtKeyEvent.VK_U, modifiers = ctrlShift)
            waitForIdle()
            assertEquals("uͤ Zeile", line.text())
            assertEquals(
                "uͤ Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )

            line.stroke(AwtKeyEvent.VK_S, modifiers = ctrlShift)
            waitForIdle()
            assertEquals("uͤſ Zeile", line.text(), "the cursor was behind the first one")

            runOnIdle { keys.perform(AppAction.UNDO) }
            runOnIdle { keys.perform(AppAction.UNDO) }
            waitForIdle()
            assertEquals(
                "Erste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
        }

    @Test
    fun theSecondKeyDoesNotTypeItsOwnLetter() =
        withOpenProject { _, _ ->
            val line = focus("line text l1")
            line.performTextInputSelection(TextRange(0))
            line.leader()
            waitForIdle()
            onNodeWithTag("leader hint").assertExists()
            line.stroke(AwtKeyEvent.VK_R, 'r')
            waitForIdle()
            assertEquals("ꝛErste Zeile", line.text())
            onNodeWithTag("leader hint").assertDoesNotExist()

            line.stroke(AwtKeyEvent.VK_X, 'x')
            waitForIdle()
            assertEquals("ꝛxErste Zeile", line.text(), "the next key types again")
        }

    @Test
    fun aDialogKeepsTheLineBehindItFromTakingCharacters() =
        withOpenProject { model, _ ->
            focus("line text l1")
            runOnIdle { model.openEditMetadata() }
            waitForIdle()
            assertEquals(false, model.characters.ready)
            runOnIdle { model.characters.insert(SpecialCharacters.all.first()) }
            waitForIdle()
            assertEquals(
                "Erste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
        }

    @Test
    fun altGrWithoutAltIsNotACombination() =
        withOpenProject { model, _ ->
            // Windows may report AltGr as Ctrl plus AltGraph, without Alt.
            val line = focus("line text l1")
            line.stroke(AwtKeyEvent.VK_S, modifiers = ctrlShift or AwtKeyEvent.ALT_GRAPH_DOWN_MASK)
            waitForIdle()
            assertEquals(
                "Erste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
        }

    @Test
    fun cancellingTypesNothing() =
        withOpenProject { _, _ ->
            val line = focus("line text l1")
            line.leader()
            line.stroke(AwtKeyEvent.VK_J, 'j')
            line.leader()
            line.stroke(AwtKeyEvent.VK_ESCAPE, '\u001b')
            waitForIdle()
            assertEquals("Erste Zeile", line.text())
            onNodeWithTag("leader hint").assertDoesNotExist()
        }

    @Test
    fun theFindAndReplaceFieldsTakeCharacters() =
        withOpenProject { model, keys ->
            runOnIdle { keys.perform(AppAction.REPLACE) }
            waitForIdle()
            val find = focus("search field")
            find.stroke(AwtKeyEvent.VK_S, modifiers = ctrlShift)
            waitForIdle()
            assertEquals("ſ", find.text())
            assertEquals("ſ", model.search!!.query.text)

            val replace = focus("replace field")
            replace.leader()
            replace.stroke(AwtKeyEvent.VK_1, '1')
            waitForIdle()
            assertEquals("„", replace.text())
            assertEquals("„", model.search!!.replacement)
            assertEquals(
                "Erste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
                "the line was not touched",
            )
        }

    @Test
    fun withoutAFocusedFieldNothingIsInserted() =
        withOpenProject { model, _ ->
            focus("line text l1")
            val panel = focus("text list")
            assertEquals(false, model.characters.ready)
            panel.stroke(AwtKeyEvent.VK_S, modifiers = ctrlShift)
            panel.leader()
            waitForIdle()
            onNodeWithTag("leader hint").assertDoesNotExist()
            assertEquals(
                "Erste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
        }

    @Test
    fun theMenuInsertsAtTheCursorOfTheLineShown() =
        withOpenProject { model, keys ->
            focus("line text l2").performTextInputSelection(TextRange(0))
            runOnIdle { model.characters.insert(SpecialCharacters.all.first()) }
            waitForIdle()
            assertEquals("ſZweite", model.editor!!.lines[1].text)

            // The rows of the page before are gone: the menu must not write into one of them.
            runOnIdle { keys.perform(AppAction.NEXT_PAGE) }
            waitUntil(timeoutMillis = 10_000) { model.editor?.page == 2 && model.editor?.lines?.isNotEmpty() == true }
            waitForIdle()
            runOnIdle { model.characters.insert(SpecialCharacters.all.first()) }
            waitForIdle()
            assertEquals(1, model.editor!!.lines.sumOf { line -> line.text.count { it == 'ſ' } }, "in the new line")
            assertEquals(
                "ſErste Zeile",
                model.editor!!
                    .lines
                    .first()
                    .text,
            )
        }

    @Test
    fun theListOfShortcutsShowsTheCharacters() =
        runComposeUiTest {
            setContent { korrektor.ShortcutList({}) }
            for (character in SpecialCharacters.all) onNodeWithTag("character ${character.name}").assertExists()
        }
}
