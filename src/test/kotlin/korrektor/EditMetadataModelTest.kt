package korrektor

import korrektor.format.Metadata
import korrektor.format.Project
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditMetadataModelTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("editmetadata")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    /** An app with an open project, and the model of its "Edit metadata" dialog. */
    private fun open(
        dir: Path,
        metadata: Metadata?,
    ): Pair<AppModel, EditMetadataModel> {
        val pdf = dir.resolve("book.pdf").also { it.writeBytes("%PDF-1.4".toByteArray()) }
        val app = AppModel()
        app.openCreated(Project.create(dir.resolve("Folder name.korrektur"), pdf, emptyList(), metadata))
        app.openEditMetadata()
        return app to EditMetadataModel(app, app.metadata)
    }

    private fun projectJson(app: AppModel): Path = app.project!!.folder.resolve("project.json")

    @Test
    fun theFieldsStartWithTheCurrentValues() =
        withDir { dir ->
            val (_, model) = open(dir, Metadata("Abhandlung", "Ewald", "ca. 1790", "https://example.org/book"))

            assertEquals("Abhandlung", model.title)
            assertEquals("Ewald", model.author)
            assertEquals("ca. 1790", model.year)
            assertEquals("https://example.org/book", model.source)
            assertNull(model.error)
        }

    @Test
    fun aProjectWithoutMetadataStartsWithEmptyFields() =
        withDir { dir ->
            val (_, model) = open(dir, null)

            assertEquals("", model.title)
            assertEquals("", model.author)
            assertEquals("", model.year)
            assertEquals("", model.source)
            assertFalse(model.canSave)
        }

    @Test
    fun savingNeedsANonBlankTitleAndAChange() =
        withDir { dir ->
            val (app, model) = open(dir, Metadata("Title", "Author"))
            assertFalse(model.canSave, "nothing changed yet")

            model.updateAuthor("Someone else")
            assertTrue(model.canSave)

            model.updateAuthor("Author")
            assertFalse(model.canSave, "changed back to the original")

            model.updateTitle("  ")
            assertFalse(model.canSave, "blank title")
            val before = projectJson(app).readBytes()
            model.save()
            assertEquals(before.toList(), projectJson(app).readBytes().toList())
            assertEquals(Metadata("Title", "Author"), app.metadata)
        }

    @Test
    fun savingChangesTheFileTheAppAndTheWindowTitle() =
        withDir { dir ->
            val (app, model) = open(dir, Metadata("Old title", "Old author", "1785"))

            model.updateTitle("New title")
            model.updateAuthor("")
            model.updateYear("1785?")
            model.updateSource("https://example.org/book")
            model.save()

            val expected = Metadata("New title", "", "1785?", "https://example.org/book")
            assertEquals(expected, app.metadata)
            assertEquals("New title – korrektor", windowTitle(app.project, app.metadata))
            assertEquals(expected, Project.open(app.project!!.folder).metadata)
            assertFalse(app.editMetadataOpen)
            assertNull(model.error)
        }

    @Test
    fun theFolderIsNotRenamed() =
        withDir { dir ->
            val (app, model) = open(dir, Metadata("Old title"))
            val folder = app.project!!.folder

            model.updateTitle("A completely different title")
            model.save()

            assertEquals("Folder name.korrektur", app.project!!.folder.name)
            assertTrue(Files.isDirectory(folder))
            assertEquals(
                listOf("Folder name.korrektur", "book.pdf"),
                Files.list(dir).use { s ->
                    s.map { it.name }.sorted().toList()
                },
            )
        }

    @Test
    fun valuesAreKeptAsTypedAndUnknownFieldsSurvive() =
        withDir { dir ->
            val extra = mapOf("note" to JsonPrimitive("from a later version"))
            val (app, model) = open(dir, Metadata("Title", extra = extra))

            model.updateTitle("  Title with spaces  ")
            model.updateYear("ca. 1790")
            model.save()

            assertEquals(Metadata("  Title with spaces  ", "", "ca. 1790", "", extra), app.metadata)
            assertEquals(app.metadata, Project.open(app.project!!.folder).metadata)
            assertTrue("from a later version" in projectJson(app).readText())
        }

    @Test
    fun aProjectWithoutMetadataGetsItWhenSaved() =
        withDir { dir ->
            val (app, model) = open(dir, null)
            assertNull(Project.open(app.project!!.folder).metadata)

            model.updateTitle("Now it has a title")
            model.save()

            assertEquals(Metadata("Now it has a title"), app.metadata)
            assertEquals(Metadata("Now it has a title"), Project.open(app.project!!.folder).metadata)
            assertEquals("Now it has a title – korrektor", windowTitle(app.project, app.metadata))
        }

    @Test
    fun cancellingChangesNothing() =
        withDir { dir ->
            val (app, model) = open(dir, Metadata("Title"))
            val before = projectJson(app).readBytes()

            model.updateTitle("Edited but never saved")
            app.closeEditMetadata()

            assertFalse(app.editMetadataOpen)
            assertEquals(Metadata("Title"), app.metadata)
            assertEquals(before.toList(), projectJson(app).readBytes().toList())
        }

    @Test
    fun aFailedSaveKeepsTheOldMetadataEverywhereAndShowsAMessage() =
        withDir { dir ->
            val (app, model) = open(dir, Metadata("Old title", "Old author"))
            val before = projectJson(app).readBytes()
            // A folder where the temporary file must go: writing fails, on every operating system.
            Files.createDirectory(app.project!!.folder.resolve("project.json.tmp"))

            model.updateTitle("New title")
            model.save()

            assertNotNull(model.error)
            assertTrue("could not be saved" in model.error!!, model.error)
            assertEquals(Metadata("Old title", "Old author"), app.metadata)
            assertEquals(Metadata("Old title", "Old author"), app.project!!.metadata)
            assertEquals(before.toList(), projectJson(app).readBytes().toList())
            assertTrue(app.editMetadataOpen, "the dialog stays open")

            // The failed attempt cleaned up the temporary path, so saving again works,
            // and the message is gone with the next edit.
            model.updateTitle("New title again")
            assertNull(model.error)
            model.save()
            assertEquals("New title again", Project.open(app.project!!.folder).metadata?.title)
            assertFalse(app.editMetadataOpen)
        }
}
