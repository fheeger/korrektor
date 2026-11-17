package korrektor.format

import korrektor.importer.PdfImport
import korrektor.pdf.TestPage
import korrektor.pdf.TestPdfs
import korrektor.pdf.TestText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Copying a whole project to another folder (T062). */
class CopyToTest {
    private fun withProject(block: (Project, Path) -> Unit) {
        val dir = Files.createTempDirectory("copyto")
        try {
            val pdf = dir.resolve("book.pdf")
            TestPdfs.write(pdf, List(3) { n -> TestPage(texts = listOf(TestText("Seite ${n + 1}", 30f, 250f, 14f))) })
            val project = Project.create(dir.resolve("b.korrektur"), pdf, emptyList(), Metadata("B"))
            PdfImport.run(project)
            block(project, dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun files(folder: Path) =
        folder
            .walk()
            .filter {
                Files.isRegularFile(
                    it,
                )
            }.associate { folder.relativize(it).toString() to it.readBytes() }

    @Test
    fun theCopyHasEveryFileOfTheProjectAndOpens() =
        withProject { project, dir ->
            val target = dir.resolve("copy.korrektur")
            project.copyTo(target)
            val original = files(project.folder)
            val copy = files(target)
            assertEquals(original.keys, copy.keys)
            for ((name, bytes) in original) assertContentEquals(bytes, copy.getValue(name), name)
            val opened = Project.open(target)
            assertEquals(3, opened.pages.size)
            assertEquals(project.readPage(2), opened.readPage(2))
            assertEquals(project.readOriginalPage(3), opened.readOriginalPage(3))
        }

    @Test
    fun theLockAndTemporaryFilesAreLeftOut() =
        withProject { project, dir ->
            ProjectLock.acquire(project.folder).use {
                Files.writeString(project.folder.resolve("pages").resolve("0002.json.tmp"), "half")
                val target = dir.resolve("copy.korrektur")
                project.copyTo(target)
                assertFalse(target.resolve(ProjectLock.FILE).exists())
                assertFalse(target.resolve("pages").resolve("0002.json.tmp").exists())
                assertTrue(target.resolve("pages").resolve("0002.json").exists())
            }
        }

    @Test
    fun theProjectItselfIsNotChanged() =
        withProject { project, dir ->
            val before = files(project.folder)
            project.copyTo(dir.resolve("copy.korrektur"))
            val after = files(project.folder)
            assertEquals(before.keys, after.keys)
            for ((name, bytes) in before) assertContentEquals(bytes, after.getValue(name), name)
        }

    @Test
    fun aTargetThatExistsIsRefusedAndLeftAlone() =
        withProject { project, dir ->
            val target = Files.createDirectory(dir.resolve("copy.korrektur"))
            target.resolve("mine.txt").writeText("mine")
            val error = assertFailsWith<FormatException> { project.copyTo(target) }
            assertTrue(error.message!!.contains("already exists"), error.message)
            assertEquals(listOf("mine.txt"), target.toFile().list()!!.toList())
        }

    @Test
    fun aFailureLeavesNothingAtTheTarget() =
        withProject { project, dir ->
            // The project's PDF is gone: such a project cannot be copied (the copy would not open).
            project.folder.resolve("source.pdf").toFile().apply {
                setWritable(true)
                delete()
            }
            val target = dir.resolve("copy.korrektur")
            val error = assertFailsWith<FormatException> { project.copyTo(target) }
            assertTrue(error.message!!.contains("source.pdf"), error.message)
            assertFalse(target.exists(), "nothing is left")
        }
}
