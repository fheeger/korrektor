package korrektor.format

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SetPagesTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("setpages")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun newProject(dir: Path): Project {
        val pdf = dir.resolve("b.pdf").also { it.writeBytes("%PDF-1.4 stand-in".toByteArray()) }
        return Project.create(dir.resolve("p.korrektur"), pdf, emptyList(), Metadata("Title", "Author"))
    }

    private fun layer(pdfPage: Int) = PageLayer(pdfPage, 100.0, 200.0, emptyList())

    @Test
    fun theListIsWrittenAndSurvivesReopeningWithEverythingElse() =
        withDir { dir ->
            val project = newProject(dir)
            val file = project.folder.resolve("project.json")
            // A field of a later version, which must be kept.
            file.writeText(file.readText().replace("\"pages\"", "\"zzz\": 1,\n  \"pages\""))
            val opened = Project.open(project.folder)

            opened.setPages(listOf(PageInfo(1), PageInfo(2), PageInfo(3)))

            assertEquals(listOf(PageInfo(1), PageInfo(2), PageInfo(3)), opened.pages)
            val text = file.readText()
            assertTrue("{\"pdfPage\": 3}" in text && "\"zzz\": 1" in text && "\"title\": \"Title\"" in text, text)
            val reopened = Project.open(project.folder)
            assertEquals(3, reopened.pages.size)
            assertEquals(Metadata("Title", "Author"), reopened.metadata)
            assertEquals(text, file.readText(), "opening and saving again changes nothing")
        }

    @Test
    fun pageFilesCanBeWrittenForThePagesOfTheNewList() =
        withDir { dir ->
            val project = newProject(dir)
            assertFailsWith<FormatException> { project.writePage(1, layer(1)) }

            project.setPages(listOf(PageInfo(1), PageInfo(2)))
            project.writePage(1, layer(1))
            project.writePage(2, layer(2))

            assertEquals(layer(2), project.readPage(2))
            assertFailsWith<FormatException> { project.writePage(3, layer(3)) }
        }

    @Test
    fun aFailedWriteKeepsTheOldList() =
        withDir { dir ->
            val project = newProject(dir)
            project.setPages(listOf(PageInfo(1)))
            val file = project.folder.resolve("project.json")
            val before = file.readBytes()
            // A folder where the temporary file must go: writing fails.
            Files.createDirectory(project.folder.resolve("project.json.tmp"))

            assertFailsWith<IOException> { project.setPages(listOf(PageInfo(1), PageInfo(2))) }

            assertEquals(listOf(PageInfo(1)), project.pages)
            assertEquals(before.toList(), file.readBytes().toList())
        }
}
