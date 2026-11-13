package korrektor

import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.PageInfo
import korrektor.format.Project
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole flow of S02 on real files, without the UI: create a project, reopen it, edit its metadata, and
 * what happens when something goes wrong.
 */
class ProjectLifecycleTest {
    private val pdfBytes = "%PDF-1.4\n% stand-in for a real PDF\näöü".toByteArray()
    private val samplePdf = Path.of("samples", "Abhandlung_über_den_kleinen_Krieg.pdf")

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("lifecycle")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    /** A PDF outside the folder the projects go to, so that the folder's content is only what the app made. */
    private fun pdfIn(
        dir: Path,
        name: String = "book.pdf",
        bytes: ByteArray = pdfBytes,
    ): Path =
        dir
            .resolve("source")
            .also { it.createDirectories() }
            .resolve(name)
            .also { it.writeBytes(bytes) }

    private fun parentIn(dir: Path): Path = dir.resolve("projects").also { it.createDirectories() }

    /** Every path below [dir] with its size, to compare the folder before and after a failure. */
    private fun snapshot(dir: Path): List<String> =
        Files.walk(dir).use { paths ->
            paths.map { "${dir.relativize(it)} ${if (it.isDirectory()) "dir" else it.fileSize()}" }.toList().sorted()
        }

    private val allFields =
        Metadata(
            "Abhandlung über den kleinen Krieg",
            "Johann von Ewald",
            "ca. 1785",
            "https://books.google.de/books?id=MhRLAAAAcAAJ",
        )

    // --- create, reopen, edit -----------------------------------------------------------------------------

    @Test
    fun aCreatedProjectCanBeReopenedWithEverythingInIt() =
        withDir { dir ->
            val source = pdfIn(dir)
            val parent = parentIn(dir)

            val created = NewProject.create(parent, source, allFields)
            val reopened = Project.open(created.folder)

            assertEquals(parent.resolve("Abhandlung über den kleinen Krieg.korrektur"), reopened.folder)
            assertEquals(allFields, reopened.metadata)
            assertEquals(emptyList(), reopened.pages)
            assertContentEquals(pdfBytes, reopened.sourcePdf.readBytes())
            assertContentEquals(pdfBytes, source.readBytes())
            assertEquals(
                """
                {
                  "format": "korrektur-project",
                  "version": 1,
                  "metadata": {"title": "Abhandlung über den kleinen Krieg", "author": "Johann von Ewald", "year": "ca. 1785", "source": "https://books.google.de/books?id=MhRLAAAAcAAJ"},
                  "pages": []
                }
                """.trimIndent() + "\n",
                reopened.folder.resolve("project.json").readText(),
            )
            assertTrue(reopened.folder.resolve("pages").isDirectory())
        }

    @Test
    fun editingTheMetadataChangesOnlyTheMetadataLineAndSurvivesReopening() =
        withDir { dir ->
            val project = NewProject.create(parentIn(dir), pdfIn(dir), allFields)
            val file = project.folder.resolve("project.json")
            val before = file.readText().lines()

            project.updateMetadata(allFields.copy(title = "Kleiner Krieg", author = "", year = "1785?"))

            val after = file.readText().lines()
            assertEquals(before.size, after.size)
            val changed = before.indices.filter { before[it] != after[it] }
            assertEquals(1, changed.size, "only one line differs")
            assertTrue(after[changed.single()].trimStart().startsWith("\"metadata\""))
            val reopened = Project.open(project.folder)
            assertEquals(Metadata("Kleiner Krieg", "", "1785?", allFields.source), reopened.metadata)
            assertEquals(project.folder, reopened.folder, "the folder keeps its name")
        }

    @Test
    fun theWholeFlowThroughTheAppModels() =
        withDir { dir ->
            val parent = parentIn(dir)
            val created = AppModel()
            created.show(NewProject.create(parent, pdfIn(dir), Metadata("First title", "Someone")))

            // A new session opens the project and edits it.
            val session = AppModel()
            session.openProject(created.project!!.folder)
            assertEquals("First title – korrektor", windowTitle(session.project, session.metadata))
            val edit = EditMetadataModel(session, session.metadata)
            edit.updateTitle("Second title")
            edit.updateSource("a link")
            edit.save()
            assertEquals("Second title – korrektor", windowTitle(session.project, session.metadata))

            // And a third one sees the result.
            val later = AppModel()
            later.openProject(created.project!!.folder)
            assertEquals(Metadata("Second title", "Someone", "", "a link"), later.metadata)
            assertEquals(
                listOf("First title.korrektur"),
                Files.list(parent).use { s ->
                    s.map { it.fileName.toString() }.toList()
                },
            )
        }

    // --- the sample PDF -----------------------------------------------------------------------------------

    @Test
    fun aProjectFromTheSamplePdf() {
        assumeTrue(samplePdf.exists(), "the sample PDF is not available")
        withDir { dir ->
            val parent = parentIn(dir)
            val modified = samplePdf.getLastModifiedTime()
            val size = samplePdf.fileSize()
            val title = NewProject.suggestTitle(samplePdf)
            assertEquals("Abhandlung über den kleinen Krieg", title)

            val project = NewProject.create(parent, samplePdf, Metadata(title, "Johann von Ewald", "1785"))

            assertEquals(-1L, Files.mismatch(samplePdf, project.sourcePdf), "the copy is byte-identical")
            assertEquals("Abhandlung über den kleinen Krieg.korrektur", project.folder.fileName.toString())
            assertEquals(Metadata(title, "Johann von Ewald", "1785"), Project.open(project.folder).metadata)
            assertEquals(modified, samplePdf.getLastModifiedTime(), "the original is untouched")
            assertEquals(size, samplePdf.fileSize())
        }
    }

    // --- failures leave nothing behind --------------------------------------------------------------------

    private fun assertRefusedAndNothingChanged(
        parent: Path,
        part: String,
        action: () -> Unit,
    ) {
        val before = snapshot(parent)
        val message = assertFailsWith<FormatException> { action() }.message!!
        assertTrue(part in message, "Expected '$part' in: $message")
        assertEquals(before, snapshot(parent), "the folder must contain exactly what it contained before")
    }

    @Test
    fun aFileThatIsNotAPdfLeavesNothingBehind() =
        withDir { dir ->
            val parent = parentIn(dir)
            val notAPdf = pdfIn(dir, "notes.pdf", "just text".toByteArray())
            assertRefusedAndNothingChanged(
                parent,
                "is not a PDF file",
            ) { NewProject.create(parent, notAPdf, allFields) }
        }

    @Test
    fun anExistingProjectIsRefusedAndStaysExactlyAsItWas() =
        withDir { dir ->
            val parent = parentIn(dir)
            NewProject.create(parent, pdfIn(dir), allFields)
            val other = pdfIn(dir, "other.pdf", "%PDF-1.7 other".toByteArray())
            assertRefusedAndNothingChanged(parent, "already exists") { NewProject.create(parent, other, allFields) }
        }

    @Test
    fun aMissingParentIsRefusedAndNotCreated() =
        withDir { dir ->
            val source = pdfIn(dir)
            val parent = dir.resolve("not-there")
            val before = snapshot(dir)
            assertTrue(
                "does not exist" in
                    assertFailsWith<FormatException> { NewProject.create(parent, source, allFields) }.message!!,
            )
            assertTrue(!parent.exists())
            assertEquals(before, snapshot(dir), "nothing was created anywhere")
        }

    @Test
    fun anUnreadablePdfLeavesNothingBehind() =
        withDir { dir ->
            val parent = parentIn(dir)
            val source = pdfIn(dir)
            assertRefusedAndNothingChanged(parent, "cannot be read") {
                NewProject.create(parent, source, allFields, { throw IOException("access denied") }) { _, _ -> }
            }
        }

    @Test
    fun aFailureWhileCopyingThePdfLeavesNothingBehind() =
        withDir { dir ->
            val parent = parentIn(dir)
            val source = pdfIn(dir)
            assertRefusedAndNothingChanged(parent, "disk full") {
                NewProject.create(parent, source, allFields, { pdfBytes }) { _, to ->
                    to.writeBytes(pdfBytes.copyOf(5))
                    throw IOException("disk full")
                }
            }
        }

    @Test
    fun aFailureWhileWritingTheProjectFileLeavesNothingBehind() =
        withDir { dir ->
            val parent = parentIn(dir)
            val source = pdfIn(dir)
            assertRefusedAndNothingChanged(parent, "Cannot create the project") {
                NewProject.create(parent, source, allFields, { pdfBytes }) { from, to ->
                    // The PDF is copied, then writing project.json fails: a folder sits where its temporary file goes.
                    Files.copy(from, to)
                    Files.createDirectory(to.resolveSibling("project.json.tmp"))
                }
            }
            // Nothing is blocked afterwards: the same project can be created now.
            assertEquals(allFields, NewProject.create(parent, source, allFields).metadata)
        }

    // --- projects made before S02 -------------------------------------------------------------------------

    @Test
    fun aProjectWithoutMetadataStillOpensAndCanGetMetadataLater() =
        withDir { dir ->
            val folder = dir.resolve("Old book.korrektur")
            folder.resolve("pages").createDirectories()
            folder.resolve("source.pdf").writeBytes(pdfBytes)
            // Exactly what the code of S01 wrote: no metadata.
            val s01Text =
                """
                {
                  "format": "korrektur-project",
                  "version": 1,
                  "pages": [
                    {"pdfPage": 1},
                    {"pdfPage": 2}
                  ]
                }
                """.trimIndent() + "\n"
            folder.resolve("project.json").writeText(s01Text)

            val project = Project.open(folder)
            assertNull(project.metadata)
            assertEquals(2, project.pages.size)
            project.saveProjectFile()
            assertEquals(s01Text, folder.resolve("project.json").readText(), "saving without changes keeps the bytes")
            assertEquals("Old book – korrektor", windowTitle(project))

            project.updateMetadata(Metadata("A title at last", year = "1790?"))

            assertEquals(
                s01Text.replace(
                    "  \"pages\"",
                    "  \"metadata\": {\"title\": \"A title at last\", \"year\": \"1790?\"},\n  \"pages\"",
                ),
                folder.resolve("project.json").readText(),
            )
            val reopened = Project.open(folder)
            assertEquals(Metadata("A title at last", year = "1790?"), reopened.metadata)
            assertEquals(listOf(PageInfo(1), PageInfo(2)), reopened.pages)
        }
}
