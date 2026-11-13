package korrektor.format

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoryLogTest {
    private fun withFile(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("history")
        try {
            block(dir.resolve("history").resolve("0001.jsonl"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun entry(
        seq: Int,
        text: String = "ſo",
    ) = JsonObject(mapOf("seq" to JsonPrimitive(seq), "text" to JsonPrimitive(text)))

    @Test
    fun entriesComeBackInOrderAndAreByteStable() =
        withFile { file ->
            val log = HistoryLog(file)
            val entries = (1..5).map { entry(it, "uͤber \"q\" \\ $it") }
            entries.forEach { log.append(it) }

            assertEquals(entries, log.readAll())
            assertEquals(entries.takeLast(2), log.readLast(2))

            val bytes = file.readBytes()
            val copy = file.resolveSibling("copy.jsonl")
            val other = HistoryLog(copy)
            log.readAll().forEach { other.append(it) }
            assertContentEquals(bytes, copy.readBytes())
            assertTrue(
                file
                    .readText()
                    .lines()
                    .first()
                    .startsWith("{\"seq\": 1, \"text\": "),
            )
        }

    @Test
    fun readLastReadsOnlyABoundedBlockFromTheEnd() =
        withFile { file ->
            val total = 100_000
            Files.createDirectories(file.parent)
            val sb = StringBuilder()
            for (i in 1..total) sb.append(CanonicalJson.write(entry(i))).append('\n')
            file.writeText(sb.toString())

            val log = HistoryLog(file)
            val last = log.readLast(5)

            assertEquals((total - 4..total).map { entry(it) }, last)
            assertTrue(log.bytesRead <= 2 * 8 * 1024, "read ${log.bytesRead} bytes")
            assertTrue(file.readBytes().size > 100 * log.bytesRead)
        }

    @Test
    fun readLastHandlesEdgeCases() =
        withFile { file ->
            val log = HistoryLog(file)
            assertEquals(emptyList(), log.readLast(3))
            log.append(entry(1))
            log.append(entry(2))
            assertEquals(emptyList(), log.readLast(0))
            assertEquals(listOf(entry(1), entry(2)), log.readLast(10))
            assertEquals(listOf(entry(2)), log.readLast(1))
        }

    @Test
    fun entriesLargerThanABlockAreReadCorrectly() =
        withFile { file ->
            val log = HistoryLog(file)
            val big = (1..4).map { entry(it, "ſ".repeat(20_000) + it) }
            big.forEach { log.append(it) }
            assertEquals(big.takeLast(2), log.readLast(2))
            assertEquals(big, log.readLast(4))
        }

    @Test
    fun tornLastLineIsIgnoredAndRepairedByTheNextAppend() =
        withFile { file ->
            val log = HistoryLog(file)
            log.append(entry(1))
            log.append(entry(2))
            val bytes = file.readBytes()
            file.writeBytes(bytes.copyOf(bytes.size - 6)) // cut into the second line, including its newline

            assertEquals(listOf(entry(1)), log.readAll())
            assertEquals(listOf(entry(1)), log.readLast(5))

            log.append(entry(3))
            assertEquals(listOf(entry(1), entry(3)), log.readAll())
            assertEquals(listOf(entry(1), entry(3)), log.readLast(5))
            assertTrue(file.readText().endsWith("\n"))
        }

    @Test
    fun anEmptyFileHasNoEntries() =
        withFile { file ->
            Files.createDirectories(file.parent)
            file.writeText("")
            assertEquals(emptyList(), HistoryLog(file).readAll())
        }

    @Test
    fun aFileWithOnlyATornLineIsEmptyAndCanBeAppendedTo() =
        withFile { file ->
            Files.createDirectories(file.parent)
            file.writeText("{\"seq\": 1, \"te")
            val log = HistoryLog(file)
            assertEquals(emptyList(), log.readAll())
            assertEquals(emptyList(), log.readLast(3))
            log.append(entry(9))
            assertEquals(listOf(entry(9)), log.readAll())
        }

    @Test
    fun anInvalidLineInTheMiddleIsAnErrorNamingTheLine() =
        withFile { file ->
            val log = HistoryLog(file)
            log.append(entry(1))
            log.append(entry(2))
            file.writeText(file.readText().lines().let { "${it[0]}\nnot json\n${it[1]}\n" })

            val e = assertFailsWith<FormatException> { log.readAll() }
            assertTrue("line 2" in e.message!!, e.message)
            val e2 = assertFailsWith<FormatException> { log.readLast(3) }
            assertTrue("line" in e2.message!!, e2.message)
            assertFailsWith<FormatException> {
                file.writeText("[1]\n")
                log.readAll()
            }
        }

    @Test
    fun existingBytesAreAlwaysAPrefixAfterAppend() =
        withFile { file ->
            val log = HistoryLog(file)
            var previous = ByteArray(0)
            for (i in 1..20) {
                log.append(entry(i))
                val now = file.readBytes()
                assertContentEquals(previous, now.copyOf(previous.size))
                previous = now
            }
        }
}
