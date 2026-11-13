package korrektor.format

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicFileTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("atomicfile")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun names(dir: Path) = dir.listDirectoryEntries().map { it.name }.sorted()

    @Test
    fun replacesContentAndLeavesNoTempFile() =
        withDir { dir ->
            val target = dir.resolve("a.json")
            target.writeText("old")
            AtomicFile.write(target, "new ſ".toByteArray())
            assertEquals("new ſ", target.readText())
            assertEquals(listOf("a.json"), names(dir))
        }

    @Test
    fun createsMissingTarget() =
        withDir { dir ->
            val target = dir.resolve("b.json")
            AtomicFile.write(target, "x".toByteArray())
            assertEquals("x", target.readText())
            assertEquals(listOf("b.json"), names(dir))
        }

    @Test
    fun failureHalfwayKeepsOldContentAndRemovesTempFile() =
        withDir { dir ->
            val target = dir.resolve("a.json")
            target.writeText("old")
            assertFailsWith<IllegalStateException> {
                AtomicFile.write(target, "0123456789".toByteArray()) { channel, data ->
                    channel.write(ByteBuffer.wrap(data, 0, data.size / 2))
                    error("disk full")
                }
            }
            assertEquals("old", target.readText())
            assertEquals(listOf("a.json"), names(dir))
        }

    @Test
    fun failureDoesNotCreateTargetWhenItDidNotExist() =
        withDir { dir ->
            val target = dir.resolve("new.json")
            assertFailsWith<IllegalStateException> {
                AtomicFile.write(target, "abc".toByteArray()) { _, _ -> throw IllegalStateException("boom") }
            }
            assertFalse(target.exists())
            assertEquals(emptyList(), names(dir))
        }

    @Test
    fun aReadOnlySourceCanBeCopied() =
        withDir { dir ->
            // Archived PDFs are often read-only, and so is the source.pdf of every project korrektor makes.
            val source = dir.resolve("source.pdf").also { it.writeText("%PDF-1.4 read-only") }
            source.toFile().setReadOnly()
            val target = dir.resolve("copy.pdf")
            try {
                AtomicFile.copy(source, target)

                assertEquals("%PDF-1.4 read-only", target.readText())
                assertEquals(listOf("copy.pdf", "source.pdf"), names(dir))
            } finally {
                source.toFile().setWritable(true)
            }
        }

    @Test
    fun cleanupRemovesOnlyTempFiles() =
        withDir { dir ->
            val sub = dir.resolve("pages").createDirectories()
            dir.resolve("project.json").writeText("p")
            dir.resolve("project.json.tmp").writeText("half")
            sub.resolve("0001.json").writeText("page")
            sub.resolve("0001.json.tmp").writeText("half")
            sub.resolve("notes.txt").writeText("keep")

            assertEquals(2, AtomicFile.cleanupTemp(dir))

            assertEquals(listOf("pages", "project.json"), names(dir))
            assertEquals(listOf("0001.json", "notes.txt"), names(sub))
            assertTrue(sub.resolve("0001.json").exists())
        }
}
