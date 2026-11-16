package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import korrektor.format.ProjectLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A project is open in one window only (T064). A second model stands for a second window or instance. */
class AppModelLockTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("appmodellock")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun project(
        dir: Path,
        name: String,
    ): Path {
        val pdf = dir.resolve("$name.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
        return Project.create(dir.resolve("$name.korrektur"), pdf, emptyList(), Metadata(name)).folder
    }

    @Test
    fun aProjectThatIsOpenCannotBeOpenedInAnotherWindowAndTheMessageSaysSo() =
        withDir { dir ->
            val folder = project(dir, "book")
            val first = AppModel()
            val second = AppModel()
            first.openProject(folder)
            assertNotNull(first.project)
            assertNull(first.error)

            second.openProject(folder)

            assertNull(second.project, "not opened")
            assertTrue(second.error!!.contains("already open"), second.error)
            assertTrue(second.error!!.contains("book.korrektur"), second.error)
            assertNotNull(first.project, "the first window is not disturbed")
        }

    @Test
    fun theSameFolderAgainInTheSameWindowIsTheSameMessage() =
        withDir { dir ->
            val folder = project(dir, "book")
            val model = AppModel()
            model.openProject(folder)
            model.openProject(folder)
            assertTrue(model.error!!.contains("already open"), model.error)
            assertNotNull(model.project, "the project stays open")
        }

    @Test
    fun aLockedProjectLeavesTheOpenProjectAlone() =
        withDir { dir ->
            val a = project(dir, "a")
            val b = project(dir, "b")
            val other = AppModel()
            other.openProject(b)
            val model = AppModel()
            model.openProject(a)

            model.openProject(b)

            assertTrue(model.error!!.contains("already open"))
            assertEquals(a, model.project!!.folder, "still project a")
            assertNotNull(model.editor)
        }

    @Test
    fun openingAnotherProjectGivesTheFirstOneBack() =
        withDir { dir ->
            val a = project(dir, "a")
            val b = project(dir, "b")
            val model = AppModel()
            model.openProject(a)
            model.openProject(b)
            assertEquals(b, model.project!!.folder)

            val elsewhere = AppModel()
            elsewhere.openProject(a)
            assertNull(elsewhere.error, "a is free now")
            assertEquals(a, elsewhere.project!!.folder)
        }

    @Test
    fun aNewProjectIsLockedFromTheStart() =
        withDir { dir ->
            val pdf = dir.resolve("n.pdf").also { it.writeBytes("%PDF-1.4\n".toByteArray()) }
            val created = Project.create(dir.resolve("n.korrektur"), pdf, emptyList(), Metadata("n"))
            val model = AppModel()
            model.openCreated(created)
            assertNotNull(model.project)

            val other = AppModel()
            other.openProject(created.folder)
            assertTrue(other.error!!.contains("already open"))
        }

    @Test
    fun aFolderThatIsNotAProjectGetsNoLockFile() =
        withDir { dir ->
            val random = Files.createDirectory(dir.resolve("random"))
            val model = AppModel()
            model.openProject(random)
            assertNotNull(model.error)
            assertFalse(random.resolve(ProjectLock.FILE).exists(), "no lock file in just any folder")
        }

    @Test
    fun aProjectThatCannotBeOpenedReleasesItsLock() =
        withDir { dir ->
            val folder = project(dir, "book")
            val original = folder.resolve(Project.PROJECT_FILE).toFile().readText()
            folder.resolve(Project.PROJECT_FILE).writeText("{ this is not a project file")
            val model = AppModel()
            model.openProject(folder)
            assertNotNull(model.error)
            assertNull(model.project)

            folder.resolve(Project.PROJECT_FILE).writeText(original) // repaired
            val again = AppModel()
            again.openProject(folder)
            assertNull(again.error, "the failed attempt did not keep the lock")
            assertNotNull(again.project)
        }
}
