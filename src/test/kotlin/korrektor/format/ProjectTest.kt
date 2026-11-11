package korrektor.format

import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTest {
    // Not real PDF content: the project never parses the PDF, it only copies it. Binary bytes catch text-mode copies.
    private val pdfBytes = ByteArray(300_000) { (it * 31 % 256).toByte() }

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("project")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun pdf(dir: Path): Path = dir.resolve("book.pdf").also { it.writeBytes(pdfBytes) }

    private fun layer(
        pdfPage: Int,
        word: String = "ſo",
    ) = PageLayer(
        pdfPage,
        100.0,
        200.0,
        listOf(Region("r1", RegionType.TEXT, 1, lines = listOf(Line("l1", words = listOf(Word(word, Box(1.0, 2.0, 3.0, 4.0))))))),
    )

    private fun snapshot(folder: Path): Map<String, List<Byte>> =
        Files
            .walk(folder)
            .use { it.filter { p -> Files.isRegularFile(p) }.toList() }
            .associate { it.relativeTo(folder).toString().replace('\\', '/') to it.readBytes().toList() }

    private fun create(
        dir: Path,
        pages: Int = 2,
    ) = Project.create(dir.resolve("book.korrektur"), pdf(dir), (1..pages).map { PageInfo(it) })

    @Test
    fun createdProjectHasProjectFileCopyOfPdfAndPagesFolder() =
        withDir { dir ->
            val project = create(dir)
            val folder = dir.resolve("book.korrektur")
            assertEquals(
                "{\n  \"format\": \"korrektur-project\",\n  \"version\": 1,\n  \"pages\": [\n    {\"pdfPage\": 1},\n    {\"pdfPage\": 2}\n  ]\n}\n",
                folder.resolve("project.json").readText(),
            )
            assertContentEquals(pdfBytes, project.sourcePdf.readBytes())
            assertTrue(Files.isDirectory(folder.resolve("pages")))
            assertEquals(listOf("pages", "project.json", "source.pdf"), folder.toFile().list()!!.sorted())
        }

    @Test
    fun failedCreateLeavesNothingBehind() =
        withDir { dir ->
            val folder = dir.resolve("book.korrektur")
            assertFailsWith<FormatException> { Project.create(folder, dir.resolve("missing.pdf"), listOf(PageInfo(1))) }
            assertFalse(folder.exists())

            assertFailsWith<FormatException> {
                Project.create(folder, pdf(dir), listOf(PageInfo(1)), null) { from, to ->
                    Files.copy(from, to)
                    throw java.io.IOException("disk full")
                }
            }
            assertFalse(folder.exists())
            assertEquals(listOf("book.pdf"), dir.toFile().list()!!.toList())
        }

    @Test
    fun createRefusesAnExistingFolderAndLeavesItAlone() =
        withDir { dir ->
            val folder = dir.resolve("book.korrektur").createDirectories()
            folder.resolve("keep.txt").writeText("mine")
            assertFailsWith<FormatException> { Project.create(folder, pdf(dir), listOf(PageInfo(1))) }
            assertEquals(mapOf("keep.txt" to "mine".toByteArray().toList()), snapshot(folder))
        }

    @Test
    fun openAndSaveWithoutChangesKeepsEveryFileIdentical() =
        withDir { dir ->
            val project = create(dir)
            project.writeOriginalPage(1, layer(1))
            project.writePage(1, layer(1, "so"))
            val folder = dir.resolve("book.korrektur")
            val before = if (folder.exists()) snapshot(folder) else emptyMap()

            val reopened = Project.open(folder)
            reopened.saveProjectFile()
            reopened.writePage(1, reopened.readPage(1))

            assertEquals(before, snapshot(folder))
        }

    @Test
    fun openRemovesLeftoverTempFiles() =
        withDir { dir ->
            create(dir)
            val folder = dir.resolve("book.korrektur")
            folder.resolve("pages/0001.json.tmp").writeText("half")
            Project.open(folder)
            assertFalse(folder.resolve("pages/0001.json.tmp").exists())
        }

    private fun assertOpenFails(
        folder: Path,
        part: String,
    ) {
        folder.resolve("pages/0001.json.tmp").also { if (!it.exists() && folder.resolve("pages").exists()) it.writeText("half") }
        val before = if (folder.exists()) snapshot(folder) else emptyMap()
        val e = assertFailsWith<FormatException> { Project.open(folder) }
        assertTrue(part in e.message!!, "Expected '$part' in: ${e.message}")
        assertEquals(before, if (folder.exists()) snapshot(folder) else emptyMap(), "A failed open must change nothing")
    }

    @Test
    fun invalidProjectsFailClearlyAndChangeNothing() =
        withDir { dir ->
            create(dir)
            val folder = dir.resolve("book.korrektur")
            val projectFile = folder.resolve("project.json")
            val good = projectFile.readText()

            projectFile.writeText(good.replace("\"version\": 1", "\"version\": 2"))
            assertOpenFails(folder, "version 2")
            projectFile.writeText(good.replace("korrektur-project", "something-else"))
            assertOpenFails(folder, "not a korrektur project")
            projectFile.writeText(good.replace("\"version\": 1", "\"version\": \"1\""))
            assertOpenFails(folder, "version")
            projectFile.writeText("{ not json")
            assertOpenFails(folder, "Invalid JSON")
            projectFile.writeText(good.replace("{\"pdfPage\": 2}", "{\"pdfPage\": 0}"))
            assertOpenFails(folder, "pages[1].pdfPage")
            projectFile.deleteExisting()
            assertOpenFails(folder, "project.json")
            projectFile.writeText(good)
            folder.resolve("source.pdf").toFile().setWritable(true)
            folder.resolve("source.pdf").deleteExisting()
            assertOpenFails(folder, "source.pdf")
            assertOpenFails(dir.resolve("nowhere"), "not a project folder")
        }

    @Test
    fun unknownFieldsSurviveOpenAndSave() =
        withDir { dir ->
            create(dir)
            val folder = dir.resolve("book.korrektur")
            val withExtra =
                folder
                    .resolve("project.json")
                    .readText()
                    .replace("{\"pdfPage\": 1}", "{\"pdfPage\": 1, \"status\": \"done\"}")
                    .replace("\n}\n", ",\n  \"title\": \"Kleiner Krieg\"\n}\n")
            folder.resolve("project.json").writeText(withExtra)

            val project = Project.open(folder)
            assertEquals(JsonPrimitive("done"), project.pages[0].extra["status"])
            project.saveProjectFile()
            assertEquals(withExtra, folder.resolve("project.json").readText())
        }

    @Test
    fun originalPageIsWrittenOnlyOnce() =
        withDir { dir ->
            val project = create(dir)
            project.writeOriginalPage(1, layer(1, "first"))
            val file = dir.resolve("book.korrektur/pages/0001.ocr.json")
            val before = file.readBytes()
            assertFailsWith<FormatException> { project.writeOriginalPage(1, layer(1, "second")) }
            assertContentEquals(before, file.readBytes())
            assertEquals(layer(1, "first"), project.readOriginalPage(1))
        }

    @Test
    fun sourcePdfIsNeverModified() =
        withDir { dir ->
            val project = create(dir)
            project.writeOriginalPage(1, layer(1))
            project.writePage(1, layer(1, "x"))
            project.writePage(2, layer(2))
            project.saveProjectFile()
            Project.open(dir.resolve("book.korrektur")).saveProjectFile()
            assertContentEquals(pdfBytes, project.sourcePdf.readBytes())
            // As root the read-only flag has no effect, so there is nothing to check.
            if (System.getProperty("user.name") !=
                "root"
            ) {
                assertFalse(Files.isWritable(project.sourcePdf), "source.pdf should be read-only")
            }
        }

    @Test
    fun readingOnePageReadsOnlyThatFile() =
        withDir { dir ->
            val project = create(dir, pages = 12)
            for (n in 1..12) project.writePage(n, layer(n, "page$n"))
            val folder = dir.resolve("book.korrektur")
            for (n in (1..12).filter { it != 7 }) folder.resolve("pages/%04d.json".format(java.util.Locale.ROOT, n)).deleteExisting()
            val reread = Project.open(folder).readPage(7)
            assertEquals("page7", reread.regions[0].lines[0].text)
        }

    @Test
    fun pageAccessIsChecked() =
        withDir { dir ->
            val project = create(dir)
            assertFailsWith<FormatException> { project.readPage(3) }
            assertFailsWith<FormatException> { project.readPage(0) }
            assertFailsWith<FormatException> { project.readPage(1) }
            assertFailsWith<FormatException> { project.writePage(2, layer(1)) }
            project.writePage(1, layer(1))
            assertEquals(
                listOf("0001.json"),
                dir
                    .resolve("book.korrektur/pages")
                    .toFile()
                    .list()!!
                    .toList(),
            )
        }
}
