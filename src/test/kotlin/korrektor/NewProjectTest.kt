package korrektor

import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.Project
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewProjectTest {
    private val pdfBytes = "%PDF-1.4\n% a minimal stand-in; pages are read at import (S03)\näöü".toByteArray()

    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("newproject")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private fun pdf(
        dir: Path,
        name: String = "book.pdf",
        bytes: ByteArray = pdfBytes,
    ): Path = dir.resolve(name).also { it.writeBytes(bytes) }

    private fun target(dir: Path): Path = dir.resolve("projects").also { Files.createDirectory(it) }

    private fun refusal(block: () -> Unit): String = assertFailsWith<FormatException> { block() }.message!!

    // --- suggestTitle -------------------------------------------------------------------------------------

    @Test
    fun theTitleIsSuggestedFromTheFileName() {
        assertEquals(
            "Abhandlung über den kleinen Krieg",
            NewProject.suggestTitle(Path.of("Abhandlung_über_den_kleinen_Krieg.pdf")),
        )
        assertEquals("Book", NewProject.suggestTitle(Path.of("/some/where/Book.PDF")))
        assertEquals("Book", NewProject.suggestTitle(Path.of("Book.Pdf")))
        assertEquals("Book", NewProject.suggestTitle(Path.of("Book")))
        assertEquals("a.b", NewProject.suggestTitle(Path.of("a.b.pdf")))
        assertEquals("two words", NewProject.suggestTitle(Path.of("_two_words_.pdf")))
    }

    @Test
    fun anEmptyFileNameSuggestsUntitled() {
        assertEquals("Untitled", NewProject.suggestTitle(Path.of(".pdf")))
        assertEquals("Untitled", NewProject.suggestTitle(Path.of("___.pdf")))
    }

    // --- folderName ---------------------------------------------------------------------------------------

    @Test
    fun ordinaryTitlesKeepTheirCharacters() {
        assertEquals("Book.korrektur", NewProject.folderName("Book"))
        assertEquals(
            "Abhandlung über den kleinen Krieg.korrektur",
            NewProject.folderName("Abhandlung über den kleinen Krieg"),
        )
        assertEquals("Über den Krieg.korrektur", NewProject.folderName("Über den Krieg"))
        assertEquals("ca. 1790 (2nd ed).korrektur", NewProject.folderName("ca. 1790 (2nd ed)"))
        assertEquals("ſo uͤber.korrektur", NewProject.folderName("ſo uͤber"))
    }

    @Test
    fun forbiddenCharactersBecomeUnderscores() {
        assertEquals("a_b_c_d_e_f_g_h_i_j.korrektur", NewProject.folderName("a<b>c:d\"e/f\\g|h?i*j"))
        assertEquals("a_b_c.korrektur", NewProject.folderName("a\u0001b\u007fc"))
    }

    @Test
    fun trailingDotsAndSpacesAreRemoved() {
        assertEquals("Book.korrektur", NewProject.folderName("Book. . "))
        assertEquals("Book.korrektur", NewProject.folderName("Book..."))
        assertEquals("a.b.korrektur", NewProject.folderName("a.b"))
    }

    @Test
    fun windowsDeviceNamesGetAPrefix() {
        assertEquals("_CON.korrektur", NewProject.folderName("CON"))
        assertEquals("_nul.korrektur", NewProject.folderName("nul"))
        assertEquals("_COM1.korrektur", NewProject.folderName("COM1"))
        assertEquals("_lpt9.korrektur", NewProject.folderName("lpt9"))
        assertEquals("_con.txt.korrektur", NewProject.folderName("con.txt"))
        assertEquals("Console.korrektur", NewProject.folderName("Console"))
        assertEquals("COM10.korrektur", NewProject.folderName("COM10"))
        assertEquals("CON stant.korrektur", NewProject.folderName("CON stant"))
    }

    @Test
    fun longTitlesAreCutTo100Characters() {
        assertEquals("a".repeat(100) + ".korrektur", NewProject.folderName("a".repeat(150)))
        assertEquals("a".repeat(100) + ".korrektur", NewProject.folderName("a".repeat(100)))
        // The cut may expose a trailing space, which is removed too.
        assertEquals("a".repeat(99) + ".korrektur", NewProject.folderName("a".repeat(99) + " " + "b".repeat(10)))
        // A character made of two chars is never cut in half.
        assertEquals("a".repeat(99) + ".korrektur", NewProject.folderName("a".repeat(99) + "😀"))
    }

    @Test
    fun emptyTitlesGiveUntitled() {
        assertEquals("Untitled.korrektur", NewProject.folderName(""))
        assertEquals("Untitled.korrektur", NewProject.folderName("..."))
        assertEquals("Untitled.korrektur", NewProject.folderName("  "))
    }

    @Test
    fun leadingDotsAndSpacesAreRemoved() {
        assertEquals("Book.korrektur", NewProject.folderName(" Book"))
        assertEquals("hidden.korrektur", NewProject.folderName(".hidden"))
        assertEquals("a b.korrektur", NewProject.folderName("  . a b . "))
    }

    @Test
    fun moreWindowsDeviceNamesGetAPrefix() {
        assertEquals("_COM¹.korrektur", NewProject.folderName("COM¹"))
        assertEquals("_lpt³.korrektur", NewProject.folderName("lpt³"))
        assertEquals("_CONIN$.korrektur", NewProject.folderName("CONIN$"))
        assertEquals("_conout$.korrektur", NewProject.folderName("conout$"))
    }

    @Test
    fun longNamesAreAlsoCutToTheByteLimit() {
        // 100 of these characters are 300 bytes in UTF-8; file systems on Linux allow 255 per name.
        val name = NewProject.folderName("漢".repeat(100))
        assertEquals("漢".repeat(66) + ".korrektur", name)
        assertTrue(name.removeSuffix(".korrektur").toByteArray().size <= 200)
    }

    // --- create -------------------------------------------------------------------------------------------

    @Test
    fun aCreatedProjectHasACopyOfThePdfTheMetadataAndNoPages() =
        withDir { dir ->
            val source = pdf(dir)
            val parent = target(dir)
            val metadata = Metadata("Abhandlung", "Ewald", "1785", "https://books.google.de/books?id=MhRLAAAAcAAJ")
            val project = NewProject.create(parent, source, metadata)

            assertEquals(parent.resolve("Abhandlung.korrektur"), project.folder)
            assertContentEquals(pdfBytes, project.sourcePdf.readBytes())
            assertEquals(metadata, project.metadata)
            assertEquals(emptyList(), project.pages)

            val reopened = Project.open(project.folder)
            assertEquals(metadata, reopened.metadata)
            assertEquals(emptyList(), reopened.pages)
            // The original is untouched.
            assertContentEquals(pdfBytes, source.readBytes())
            assertTrue(Files.isWritable(source))
        }

    @Test
    fun namesWithUmlautsAndSpacesWork() =
        withDir { dir ->
            val parent = target(dir)
            val project = NewProject.create(parent, pdf(dir), Metadata("Über den Krieg"))
            assertEquals("Über den Krieg.korrektur", project.folder.name)
            assertEquals("Über den Krieg", Project.open(parent.resolve("Über den Krieg.korrektur")).metadata?.title)
        }

    @Test
    fun theTitleIsStoredAsTypedWhileTheFolderNameIsMadeSafe() =
        withDir { dir ->
            val project = NewProject.create(target(dir), pdf(dir), Metadata("What? A: book/ "))
            assertEquals("What_ A_ book_.korrektur", project.folder.name)
            assertEquals("What? A: book/ ", Project.open(project.folder).metadata?.title)
        }

    @Test
    fun theMarkerMayFollowSomeLeadingBytes() =
        withDir { dir ->
            val parent = target(dir)
            val junk = ByteArray(1000) { 'x'.code.toByte() }
            NewProject.create(parent, pdf(dir, bytes = junk + pdfBytes), Metadata("Junk first"))
            assertTrue(parent.resolve("Junk first.korrektur/source.pdf").exists())
        }

    // --- refusals -----------------------------------------------------------------------------------------

    private fun assertNothingCreated(
        parent: Path,
        message: String,
        part: String,
    ) {
        assertTrue(part in message, "Expected '$part' in: $message")
        assertEquals(emptyList(), parent.listDirectoryEntries(), "The target folder must stay empty")
    }

    @Test
    fun aFileThatIsNotAPdfIsRefused() =
        withDir { dir ->
            val parent = target(dir)
            val text = dir.resolve("notes.pdf").also { it.writeText("just some text") }
            assertNothingCreated(
                parent,
                refusal { NewProject.create(parent, text, Metadata("T")) },
                "is not a PDF file",
            )

            val empty = pdf(dir, "empty.pdf", ByteArray(0))
            assertNothingCreated(
                parent,
                refusal { NewProject.create(parent, empty, Metadata("T")) },
                "is not a PDF file",
            )

            // The marker is only looked for in the first 1024 bytes.
            val late = pdf(dir, "late.pdf", ByteArray(1100) { 'x'.code.toByte() } + pdfBytes)
            assertNothingCreated(
                parent,
                refusal { NewProject.create(parent, late, Metadata("T")) },
                "is not a PDF file",
            )
        }

    @Test
    fun aMissingFileIsRefused() =
        withDir { dir ->
            val parent = target(dir)
            val message = refusal { NewProject.create(parent, dir.resolve("missing.pdf"), Metadata("T")) }
            assertNothingCreated(parent, message, "does not exist")
        }

    @Test
    fun aFolderInPlaceOfAPdfIsRefused() =
        withDir { dir ->
            val parent = target(dir)
            val folder = dir.resolve("folder.pdf").also { Files.createDirectory(it) }
            assertNothingCreated(parent, refusal { NewProject.create(parent, folder, Metadata("T")) }, "is not a file")
        }

    @Test
    fun anUnreadableFileIsRefused() =
        withDir { dir ->
            val parent = target(dir)
            val source = pdf(dir)
            val message =
                refusal {
                    NewProject.create(parent, source, Metadata("T"), { throw IOException("access denied") }) { _, _ -> }
                }
            assertNothingCreated(parent, message, "cannot be read: access denied")
        }

    @Test
    fun aMissingTargetFolderIsRefusedAndNotCreated() =
        withDir { dir ->
            val parent = dir.resolve("not-there")
            val message = refusal { NewProject.create(parent, pdf(dir), Metadata("T")) }
            assertTrue("does not exist" in message, message)
            assertFalse(parent.exists())
        }

    @Test
    fun anExistingProjectFolderIsRefusedAndNotTouched() =
        withDir { dir ->
            val parent = target(dir)
            val first = NewProject.create(parent, pdf(dir), Metadata("Same"))
            val projectJson = first.folder.resolve("project.json").readBytes()
            val marker = first.folder.resolve("pages/marker.txt").also { it.writeText("keep") }

            val message = refusal { NewProject.create(parent, pdf(dir, "other.pdf"), Metadata("Same")) }

            assertTrue("A project named Same.korrektur already exists in $parent" == message, message)
            assertContentEquals(projectJson, first.folder.resolve("project.json").readBytes())
            assertEquals("keep", marker.toFile().readText())
            assertEquals(listOf("Same.korrektur"), parent.listDirectoryEntries().map { it.name })
        }

    @Test
    fun aFolderNameTakenByAnythingElseIsRefused() =
        withDir { dir ->
            val parent = target(dir)
            parent.resolve("Taken.korrektur").writeText("a file, not a project")
            val message = refusal { NewProject.create(parent, pdf(dir), Metadata("Taken")) }
            assertTrue("already exists" in message, message)
            assertEquals("a file, not a project", parent.resolve("Taken.korrektur").toFile().readText())
        }

    @Test
    fun aSystemErrorThatOnlyNamesAPathIsExplained() =
        withDir { dir ->
            val parent = target(dir)
            val source = pdf(dir)
            val message =
                refusal {
                    NewProject.create(parent, source, Metadata("Denied"), { pdfBytes }) { _, to ->
                        throw AccessDeniedException(to.toString())
                    }
                }
            assertNothingCreated(parent, message, "access denied")
        }

    @Test
    fun aFailureWhileCopyingLeavesNothingBehind() =
        withDir { dir ->
            val parent = target(dir)
            val source = pdf(dir)
            val message =
                refusal {
                    NewProject.create(parent, source, Metadata("Half"), { pdfBytes }) { _, to ->
                        to.writeText("half a copy")
                        throw IOException("disk full")
                    }
                }
            assertNothingCreated(parent, message, "disk full")
            assertContentEquals(pdfBytes, source.readBytes())
        }
}
