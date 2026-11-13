package korrektor.format

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests for the findings of the end-of-story code review of S01. */
class ReviewFixesTest {
    private fun withDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("review")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    private val one = mapOf("x" to JsonPrimitive(1))

    @Test
    fun unknownFieldsMayNotReuseKnownNames() {
        assertFailsWith<IllegalArgumentException> { Word("a", extra = mapOf("t" to JsonPrimitive(1))) }
        assertFailsWith<IllegalArgumentException> {
            Line("l1", words = emptyList(), extra = mapOf("id" to JsonPrimitive(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            Region(
                "r1",
                RegionType.TEXT,
                1,
                lines = emptyList(),
                extra =
                    mapOf(
                        "lines" to JsonPrimitive(1),
                    ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PageLayer(1, 1.0, 1.0, emptyList(), mapOf("regions" to JsonPrimitive(1)))
        }
        assertFailsWith<IllegalArgumentException> { PageInfo(1, mapOf("pdfPage" to JsonPrimitive(2))) }
        Word("a", extra = one) // other names are fine
    }

    @Test
    fun pageSizeAndCoordinatesStayInARangeThatSurvivesRounding() {
        assertFailsWith<IllegalArgumentException> { PageLayer(1, 0.04, 100.0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { PageLayer(1, 100.0, 2e9, emptyList()) }
        assertFailsWith<IllegalArgumentException> { Box(2e9, 0.0, 1.0, 1.0) }
        PageLayer(1, 0.1, 100.0, emptyList())
    }

    @Test
    fun nonFiniteNumbersCannotBeWritten() {
        assertFailsWith<FormatException> { CanonicalJson.write(JsonObject(mapOf("n" to JsonPrimitive(Double.NaN)))) }
        assertFailsWith<FormatException> { CanonicalJson.write(JsonPrimitive(Double.POSITIVE_INFINITY)) }
    }

    @Test
    fun invalidUtf8IsAnErrorNotSilentlyReplaced() =
        withDir { dir ->
            val bad = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte(), '\n'.code.toByte())
            val file = dir.resolve("x.jsonl").also { it.writeBytes(bad) }
            assertFailsWith<FormatException> { HistoryLog(file).readAll() }
            assertFailsWith<FormatException> { CanonicalJson.readUtf8(file) }

            val folder = dir.resolve("p.korrektur")
            val pdf = dir.resolve("b.pdf").also { it.writeBytes(ByteArray(10)) }
            val project = Project.create(folder, pdf, listOf(PageInfo(1)))
            folder.resolve("pages/0001.json").writeBytes(bad)
            assertFailsWith<FormatException> { project.readPage(1) }
            folder.resolve("project.json").writeBytes(bad)
            assertFailsWith<FormatException> { Project.open(folder) }
        }

    @Test
    fun copyIsAtomicAndLeavesNoTempFile() =
        withDir { dir ->
            val source = dir.resolve("a.bin").also { it.writeBytes(ByteArray(100_000) { i -> i.toByte() }) }
            val target = dir.resolve("b.bin")
            AtomicFile.copy(source, target)
            assertContentEquals(source.readBytes(), target.readBytes())
            assertEquals(listOf("a.bin", "b.bin"), dir.listDirectoryEntries().map { it.fileName.toString() }.sorted())

            target.writeText("old")
            assertFailsWith<java.io.IOException> { AtomicFile.copy(dir.resolve("missing.bin"), target) }
            assertEquals("old", target.toFile().readText())
            assertEquals(listOf("a.bin", "b.bin"), dir.listDirectoryEntries().map { it.fileName.toString() }.sorted())
        }

    @Test
    fun createFailsCleanlyWhenTheParentFolderIsMissing() =
        withDir { dir ->
            val pdf = dir.resolve("b.pdf").also { it.writeBytes(ByteArray(10)) }
            val folder = dir.resolve("missing").resolve("p.korrektur")
            assertFailsWith<FormatException> { Project.create(folder, pdf, listOf(PageInfo(1))) }
            assertTrue(!dir.resolve("missing").exists())
        }
}
