package korrektor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import korrektor.format.Metadata
import korrektor.format.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The core flow of the app shell, with a fake folder chooser: start screen, open a project, errors. */
@OptIn(ExperimentalTestApi::class)
class AppUiTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("appui")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun project(dir: Path): Path {
        val pdf = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
        val metadata = Metadata("Abhandlung über den kleinen Krieg", "Ewald", "1785")
        return Project.create(dir.resolve("book.korrektur"), pdf, emptyList(), metadata).folder
    }

    @Test
    fun theStartScreenShowsTheAppNameAndBothButtons() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }, { null }) }
            onNodeWithText("korrektor").assertExists()
            onNodeWithText("New project…").assertExists()
            onNodeWithText("Open project…").assertExists()
        }

    @Test
    fun openingAProjectShowsItsTitleAndMetadata() =
        withDir { dir ->
            val folder = project(dir)
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { folder }, { null }) }

                onNodeWithText("Open project…").performClick()

                onNodeWithText("Abhandlung über den kleinen Krieg").assertExists()
                onNodeWithText("Author: Ewald").assertExists()
                onNodeWithText("Year: 1785").assertExists()
                onNodeWithText("Open another project…").assertExists()
            }
        }

    @Test
    fun aFolderThatIsNotAProjectShowsADismissibleError() =
        withDir { dir ->
            runComposeUiTest {
                setContent { App(AppModel(), { dir }, { null }) }

                onNodeWithText("Open project…").performClick()
                onNodeWithText("has no project.json", substring = true).assertExists()
                // Nothing was opened.
                onNodeWithText("New project…").assertExists()

                onNodeWithText("Dismiss").performClick()
                onNodeWithText("has no project.json", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun aBadFolderLeavesTheOpenProjectOpen() =
        withDir { dir ->
            val folder = project(dir)
            val bad = Files.createDirectory(dir.resolve("empty"))
            runComposeUiTest {
                val folders = ArrayDeque(listOf(folder, bad))
                setContent { App(AppModel(), { folders.removeFirst() }, { null }) }

                onNodeWithText("Open project…").performClick()
                onNodeWithText("Open another project…").performClick()

                onNodeWithText("has no project.json", substring = true).assertExists()
                onNodeWithText("Abhandlung über den kleinen Krieg").assertExists()
            }
        }

    @Test
    fun cancellingTheDialogDoesNothing() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }, { null }) }

            onNodeWithText("Open project…").performClick()

            onNodeWithText("New project…").assertExists()
            onNodeWithText("Dismiss").assertDoesNotExist()
        }

    // --- the "New project" dialog -------------------------------------------------------------------------

    private fun pdf(
        dir: Path,
        name: String,
    ): Path = dir.resolve(name).also { it.writeBytes("%PDF-1.4\n".toByteArray()) }

    @Test
    fun theDialogStartsWithChoosingAPdf() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }, { null }) }

            onNodeWithText("New project…").performClick()

            onNodeWithText("Choose PDF…").assertExists()
            onNodeWithText("No PDF chosen").assertExists()
            onNodeWithText("Create").assertIsNotEnabled()
            onNodeWithText("Change…").assertIsNotEnabled()
        }

    @Test
    fun cancellingTheDialogClosesIt() =
        runComposeUiTest {
            setContent { App(AppModel(), { null }, { null }) }
            onNodeWithText("New project…").performClick()

            onNodeWithText("Cancel").performClick()

            onNodeWithText("Choose PDF…").assertDoesNotExist()
            onNodeWithText("Open project…").assertExists()
        }

    @Test
    fun choosingAPdfSuggestsATitleAndCreateOpensTheProject() =
        withDir { dir ->
            val source = pdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()

                onNodeWithText("Choose PDF…").performClick()
                onNodeWithText("Some_Book.pdf").assertExists()
                onNodeWithText("Some Book").assertExists()
                onNodeWithText("Create").assertIsEnabled()

                onNodeWithText("Some Book").performTextReplacement("Abhandlung")
                onNodeWithText("Year").performTextReplacement("ca. 1790")
                onNodeWithText("Create").performClick()
                waitUntil(timeoutMillis = 10_000) { model.project != null }
                waitForIdle()

                onNodeWithText("Abhandlung").assertExists()
                onNodeWithText("Year: ca. 1790").assertExists()
                onNodeWithText("Choose PDF…").assertDoesNotExist()
                assertEquals("Abhandlung – korrektor", windowTitle(model.project, model.metadata))
                assertTrue(dir.resolve("Abhandlung.korrektur/source.pdf").exists())
            }
        }

    @Test
    fun createIsDisabledWhileTheTitleIsBlank() =
        withDir { dir ->
            val source = pdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                setContent { App(AppModel(), { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()

                onNodeWithText("Some Book").performTextReplacement("")

                onNodeWithText("Create").assertIsNotEnabled()
            }
        }

    @Test
    fun anExistingProjectFolderShowsAnErrorAndTheDialogStaysOpen() =
        withDir { dir ->
            val source = pdf(dir, "Some_Book.pdf")
            Files.createDirectory(dir.resolve("Some Book.korrektur"))
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()

                onNodeWithText("Create").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText("already exists", substring = true).fetchSemanticsNodes().isNotEmpty()
                }

                onNodeWithText("already exists", substring = true).assertExists()
                onNodeWithText("Choose PDF…").assertExists()
                assertNull(model.project)
                assertTrue(dir.resolve("Some Book.korrektur").listDirectoryEntries().isEmpty())
            }
        }

    // --- the "Edit metadata" dialog -----------------------------------------------------------------------

    private val oldTitle = "Abhandlung über den kleinen Krieg"

    @Test
    fun editingTheTitleChangesTheScreenAndTheStoredMetadata() =
        withDir { dir ->
            val folder = project(dir)
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { folder }, { null }) }
                onNodeWithText("Open project…").performClick()

                onNodeWithText("Edit metadata…").performClick()
                onNodeWithText("Save").assertIsNotEnabled()
                onNode(hasSetTextAction() and hasText(oldTitle)).performTextReplacement("Kleiner Krieg")
                onNode(hasSetTextAction() and hasText("1785")).performTextReplacement("ca. 1790")
                onNodeWithText("Save").assertIsEnabled()
                onNodeWithText("Save").performClick()

                onNodeWithText("Kleiner Krieg").assertExists()
                onNodeWithText("Year: ca. 1790").assertExists()
                onNodeWithText("Save").assertDoesNotExist()
                assertEquals("Kleiner Krieg – korrektor", windowTitle(model.project, model.metadata))
                assertEquals("Kleiner Krieg", Project.open(folder).metadata?.title)
                assertEquals("book.korrektur", folder.name)
            }
        }

    @Test
    fun cancellingTheEditChangesNothing() =
        withDir { dir ->
            val folder = project(dir)
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                onNodeWithText("Edit metadata…").performClick()
                onNode(hasSetTextAction() and hasText(oldTitle)).performTextReplacement("Never saved")

                onNodeWithText("Cancel").performClick()

                onNodeWithText(oldTitle).assertExists()
                onNodeWithText("Never saved").assertDoesNotExist()
                assertEquals(oldTitle, Project.open(folder).metadata?.title)
            }
        }

    @Test
    fun aBlankTitleCannotBeSaved() =
        withDir { dir ->
            val folder = project(dir)
            runComposeUiTest {
                setContent { App(AppModel(), { folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                onNodeWithText("Edit metadata…").performClick()

                onNode(hasSetTextAction() and hasText(oldTitle)).performTextReplacement("")

                onNodeWithText("Save").assertIsNotEnabled()
            }
        }

    @Test
    fun aFailedSaveShowsAMessageAndKeepsTheDialogAndTheOldTitle() =
        withDir { dir ->
            val folder = project(dir)
            Files.createDirectory(folder.resolve("project.json.tmp"))
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { folder }, { null }) }
                onNodeWithText("Open project…").performClick()
                onNodeWithText("Edit metadata…").performClick()
                onNode(hasSetTextAction() and hasText(oldTitle)).performTextReplacement("Cannot be saved")

                onNodeWithText("Save").performClick()

                onNodeWithText("could not be saved", substring = true).assertExists()
                onNodeWithText("Save").assertExists()
                assertEquals(oldTitle, model.metadata?.title)
                assertEquals(oldTitle, Project.open(folder).metadata?.title)
            }
        }
}
