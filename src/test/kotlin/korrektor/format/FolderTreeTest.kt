package korrektor.format

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.FileInputStream
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderTreeTest {
    @Test
    fun deletesNestedFoldersAndReadOnlyFiles() {
        val dir = Files.createTempDirectory("tree")
        val inner = dir.resolve("a/b").also { it.createDirectories() }
        inner
            .resolve("file.txt")
            .also { it.writeText("x") }
            .toFile()
            .setReadOnly()

        deleteTree(dir.resolve("a"))

        assertFalse(dir.resolve("a").exists())
        dir.toFile().deleteRecursively()
    }

    @Test
    fun saysSoWhenSomethingCannotBeDeleted() {
        // Only Windows refuses to delete a file that is open, like a virus scanner holding it.
        assumeTrue(System.getProperty("os.name").lowercase().contains("win"), "Windows only")
        val dir = Files.createTempDirectory("tree")
        val folder = dir.resolve("p.korrektur").also { it.createDirectories() }
        val file = folder.resolve("source.pdf").also { it.writeText("x") }
        // java.io streams do not share the file for deletion on Windows (NIO channels do).
        FileInputStream(file.toFile()).use {
            val e = assertFailsWith<FormatException> { deleteTree(folder) }
            assertTrue("could not be deleted completely" in e.message!!, e.message)
        }
        dir.toFile().deleteRecursively()
    }
}
