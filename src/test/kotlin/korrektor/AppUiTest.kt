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
import korrektor.importer.ImportResult
import korrektor.pdf.PdfException
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** A real PDF of three pages; the middle one has no text. */
    private fun realPdf(
        dir: Path,
        name: String,
    ): Path =
        dir.resolve(name).also {
            val pages =
                listOf(
                    TestPage(texts = listOf(TestText("First page", 20f, 200f))),
                    TestPage(),
                    TestPage(texts = listOf(TestText("Third", 20f, 200f))),
                )
            TestPdfs.write(it, pages)
        }

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
            val source = realPdf(dir, "Some_Book.pdf")
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
                onNodeWithText("Pages: 3").assertExists()
                // The import found no text on page 2: a notice says so, and it can be dismissed.
                onNodeWithText("1 page has no text: 2.").assertExists()
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

    // --- the import in the dialog -------------------------------------------------------------------------

    /** A factory whose import reports two of three pages, then waits for [release] or a cancel. */
    private fun slowFactory(release: CountDownLatch) =
        FakeFactory(importer = { _, progress, isCancelled ->
            progress(1, 3)
            progress(2, 3)
            while (release.count > 0L && !isCancelled()) Thread.sleep(5)
            if (isCancelled()) throw CancellationException("stop")
            ImportResult(3)
        })

    @Test
    fun theDialogShowsTheProgressOfTheImportAndCancelDeletesTheProject() =
        withDir { dir ->
            val source = realPdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                val model = AppModel(slowFactory(CountDownLatch(1)))
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()

                onNodeWithText("Create").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { onAllNodesWithText("Importing page 2 of 3").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Cancel").assertIsEnabled()
                onNodeWithText("Create").assertIsNotEnabled()
                onNodeWithText("Choose PDF…").assertIsNotEnabled()
                assertFalse(model.canClose, "the window cannot be closed while importing")

                onNodeWithText("Cancel").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { onAllNodesWithText("Import cancelled.").fetchSemanticsNodes().isNotEmpty() }
                // The dialog is still there with its fields, and creating is possible again.
                onNodeWithText("Create").assertIsEnabled()
                onNodeWithText("Choose PDF…").assertIsEnabled()
                onNodeWithText("Some Book").assertExists()
                assertTrue(model.canClose)
                assertNull(model.project)
                assertEquals(emptyList(), dir.listDirectoryEntries("*.korrektur"), "nothing is left in the folder")
            }
        }

    @Test
    fun whenTheImportIsDoneTheProjectOpensAndTheDialogGoes() =
        withDir { dir ->
            val source = realPdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                val release = CountDownLatch(1)
                val model = AppModel(slowFactory(release))
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()
                onNodeWithText("Create").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { onAllNodesWithText("Importing page 2 of 3").fetchSemanticsNodes().isNotEmpty() }

                release.countDown()
                waitUntil(timeoutMillis = 10_000) { model.project != null }
                waitForIdle()

                onNodeWithText("Some Book").assertExists()
                onNodeWithText("Importing page 2 of 3").assertDoesNotExist()
                onNodeWithText("Choose PDF…").assertDoesNotExist()
                assertTrue(model.canClose)
            }
        }

    @Test
    fun aFailedImportShowsItsReasonInTheDialogAndLeavesNothing() =
        withDir { dir ->
            val source = realPdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                val model = AppModel(FakeFactory(importer = { _, _, _ -> throw PdfException("The PDF is damaged") }))
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()

                onNodeWithText("Create").performClick()
                waitUntil(
                    timeoutMillis = 10_000,
                ) { onAllNodesWithText("The PDF is damaged").fetchSemanticsNodes().isNotEmpty() }

                onNodeWithText("Create").assertIsEnabled()
                assertNull(model.project)
                assertEquals(emptyList(), dir.listDirectoryEntries("*.korrektur"))
            }
        }

    @Test
    fun theNoticeAboutPagesWithoutTextCanBeDismissed() =
        withDir { dir ->
            val source = realPdf(dir, "Some_Book.pdf")
            runComposeUiTest {
                val model = AppModel()
                setContent { App(model, { null }, { source }) }
                onNodeWithText("New project…").performClick()
                onNodeWithText("Choose PDF…").performClick()
                onNodeWithText("Create").performClick()
                waitUntil(timeoutMillis = 10_000) { model.project != null }
                waitForIdle()
                onNodeWithText("1 page has no text: 2.").assertExists()

                onNodeWithText("Dismiss").performClick()

                onNodeWithText("1 page has no text: 2.").assertDoesNotExist()
                onNodeWithText("Pages: 3").assertExists()
            }
        }
}
