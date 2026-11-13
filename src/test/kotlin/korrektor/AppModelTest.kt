package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppModelTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("appmodel")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun createProject(
        dir: Path,
        folderName: String,
        metadata: Metadata?,
    ): Path {
        val pdf = dir.resolve("$folderName.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
        return Project.create(dir.resolve(folderName), pdf, emptyList(), metadata).folder
    }

    private fun projectFileWith(
        dir: Path,
        json: String,
    ): Path = dir.resolve("broken").also { it.createDirectories() }.also { it.resolve("project.json").writeText(json) }

    // --- AppModel -----------------------------------------------------------------------------------------

    @Test
    fun aProjectFolderOpens() =
        withDir { dir ->
            val folder = createProject(dir, "Book.korrektur", Metadata("Book", "Someone", "1785"))
            val model = AppModel()
            assertNull(model.project)

            model.openProject(folder)

            assertEquals(Metadata("Book", "Someone", "1785"), model.project?.metadata)
            assertNull(model.error)
        }

    @Test
    fun aFolderWithoutProjectFileIsAnErrorAndChangesNothing() =
        withDir { dir ->
            val empty = dir.resolve("empty").also { it.createDirectories() }
            val model = AppModel()

            model.openProject(empty)

            assertNull(model.project)
            assertTrue("has no project.json" in model.error!!, model.error)
        }

    @Test
    fun aFolderThatDoesNotExistIsAnError() =
        withDir { dir ->
            val model = AppModel()
            model.openProject(dir.resolve("nothing-here"))
            assertTrue("is not a project folder" in model.error!!, model.error)
        }

    @Test
    fun theWrongFormatAndANewerVersionAreErrors() =
        withDir { dir ->
            val model = AppModel()

            model.openProject(projectFileWith(dir, """{"format": "something-else", "version": 1, "pages": []}"""))
            assertTrue("not a korrektur project" in model.error!!, model.error)

            model.dismissError()
            Files.delete(dir.resolve("broken/project.json"))
            model.openProject(projectFileWith(dir, """{"format": "korrektur-project", "version": 99, "pages": []}"""))
            assertTrue("Please update the app" in model.error!!, model.error)
            assertNull(model.project)
        }

    @Test
    fun aFailedOpenKeepsTheOpenProject() =
        withDir { dir ->
            val model = AppModel()
            model.openProject(createProject(dir, "Good.korrektur", Metadata("Good")))
            val open = model.project
            assertNotNull(open)

            model.openProject(dir.resolve("nothing-here"))

            assertSame(open, model.project)
            assertNotNull(model.error)
        }

    @Test
    fun anErrorCanBeDismissedAndAGoodOpenClearsIt() =
        withDir { dir ->
            val model = AppModel()
            model.openProject(dir.resolve("nothing-here"))
            assertNotNull(model.error)
            model.dismissError()
            assertNull(model.error)

            model.openProject(dir.resolve("nothing-here"))
            model.openProject(createProject(dir, "Good.korrektur", Metadata("Good")))
            assertNull(model.error)
        }

    // --- windowTitle --------------------------------------------------------------------------------------

    @Test
    fun theWindowTitleIsTheAppNameOnTheStartScreen() {
        assertEquals("korrektor", windowTitle(null))
    }

    @Test
    fun theWindowTitleShowsTheProjectTitle() =
        withDir { dir ->
            val model = AppModel()
            model.openProject(createProject(dir, "Folder.korrektur", Metadata("Abhandlung über den Krieg")))
            assertEquals("Abhandlung über den Krieg – korrektor", windowTitle(model.project))
        }

    @Test
    fun aProjectWithoutMetadataShowsItsFolderName() =
        withDir { dir ->
            val model = AppModel()
            model.openProject(createProject(dir, "Old book.korrektur", null))
            assertNull(model.project?.metadata)
            assertEquals("Old book – korrektor", windowTitle(model.project))
        }
}
