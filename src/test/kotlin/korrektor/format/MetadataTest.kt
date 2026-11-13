package korrektor.format

import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("metadata")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun pdf(dir: Path) = dir.resolve("b.pdf").also { it.writeBytes(ByteArray(100) { i -> i.toByte() }) }

    private val typed =
        Metadata(" Über den ſchönen Krieg ", "Joh. v. Ewald", "ca. 1790", "https://books.google.de/books?id=x&hl=de")

    @Test
    fun metadataSurvivesCreateOpenAndUpdateExactlyAsTyped() =
        withDir { dir ->
            val folder = dir.resolve("p.korrektur")
            val project = Project.create(folder, pdf(dir), emptyList(), typed)
            assertEquals(typed, Project.open(folder).metadata)

            val changed = Metadata("Neu", year = "1785?", source = "uͤber")
            project.updateMetadata(changed)
            assertEquals(changed, project.metadata)
            assertEquals(changed, Project.open(folder).metadata)
        }

    @Test
    fun blankTitleIsRejectedAndEmptyOptionalFieldsAreNotWritten() =
        withDir { dir ->
            assertFailsWith<IllegalArgumentException> { Metadata("  ") }
            assertFailsWith<IllegalArgumentException> { Metadata("t", extra = mapOf("year" to JsonPrimitive(1))) }
            val folder = dir.resolve("p.korrektur")
            Project.create(folder, pdf(dir), emptyList(), Metadata("Only title"))
            assertTrue("  \"metadata\": {\"title\": \"Only title\"},\n" in folder.resolve("project.json").readText())
        }

    @Test
    fun projectWithoutMetadataOpensAndSavesIdentically() =
        withDir { dir ->
            val folder = dir.resolve("p.korrektur")
            Project.create(folder, pdf(dir), listOf(PageInfo(1)))
            val before = folder.resolve("project.json").readText()
            val project = Project.open(folder)
            assertNull(project.metadata)
            project.saveProjectFile()
            assertEquals(before, folder.resolve("project.json").readText())
        }

    @Test
    fun projectWithMetadataAndUnknownFieldsOpensAndSavesIdentically() =
        withDir { dir ->
            val folder = dir.resolve("p.korrektur")
            Project.create(folder, pdf(dir), listOf(PageInfo(1)), typed)
            val file = folder.resolve("project.json")
            // Written canonically: unknown fields come after the known ones, in alphabetical order.
            val canonical =
                file.readText().replace(
                    "\"source\": \"https://books.google.de/books?id=x&hl=de\"}",
                    "\"source\": \"https://books.google.de/books?id=x&hl=de\", \"isbn\": \"123\"}",
                )
            file.writeText(canonical)
            val project = Project.open(folder)
            assertEquals(JsonPrimitive("123"), project.metadata!!.extra["isbn"])
            project.saveProjectFile()
            assertEquals(canonical, file.readText())
        }

    private fun assertOpenFails(
        folder: Path,
        json: String,
        part: String,
    ) {
        val file = folder.resolve("project.json")
        file.writeText(json)
        val e = assertFailsWith<FormatException> { Project.open(folder) }
        assertTrue(part in e.message!!, e.message)
        assertEquals(json, file.readText(), "A failed open must change nothing")
    }

    @Test
    fun invalidMetadataIsRejectedNamingTheField() =
        withDir { dir ->
            val folder = dir.resolve("p.korrektur")
            Project.create(folder, pdf(dir), emptyList())

            fun withMeta(m: String) =
                "{\n  \"format\": \"korrektur-project\",\n  \"version\": 1,\n  \"metadata\": $m,\n  \"pages\": []\n}\n"
            assertOpenFails(folder, withMeta("\"x\""), "metadata")
            assertOpenFails(folder, withMeta("{}"), "metadata.title")
            assertOpenFails(folder, withMeta("{\"title\": \" \"}"), "metadata.title")
            assertOpenFails(folder, withMeta("{\"title\": 5}"), "metadata.title")
            assertOpenFails(folder, withMeta("{\"title\": \"t\", \"year\": 1785}"), "metadata.year")
        }

    @Test
    fun updateChangesOnlyTheMetadataLineAndFailureKeepsTheOldMetadata() =
        withDir { dir ->
            val folder = dir.resolve("p.korrektur")
            val project = Project.create(folder, pdf(dir), listOf(PageInfo(1), PageInfo(2)), Metadata("Old", "A"))
            val file = folder.resolve("project.json")
            val before = file.readText().lines()
            project.updateMetadata(Metadata("New", "A"))
            val after = file.readText().lines()
            assertEquals(before.size, after.size)
            assertEquals(1, before.indices.count { before[it] != after[it] })
            assertTrue(after.any { it.startsWith("  \"metadata\": {\"title\": \"New\"") })

            // Make writing fail: a directory is in the way of the temporary file.
            Files.createDirectory(folder.resolve("project.json.tmp"))
            assertFailsWith<Exception> { project.updateMetadata(Metadata("Lost")) }
            assertEquals("New", project.metadata!!.title)
            assertEquals(after, file.readText().lines())
        }
}
