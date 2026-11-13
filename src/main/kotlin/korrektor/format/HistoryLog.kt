package korrektor.format

import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * The undo history of one page (`history/0001.jsonl`): one canonical JSON object per line, only ever appended.
 * What an entry contains is defined by the undo feature (S08); here entries are opaque.
 *
 * A line only counts once its newline is written. A crash during [append] leaves a torn last line, which is
 * ignored when reading and cut off before the next append, so it can never damage later entries.
 */
class HistoryLog(
    private val file: Path,
) {
    /** Bytes read by [readLast] so far; tests use it to check that the whole file is not read. */
    internal var bytesRead = 0L
        private set

    /** Adds [entry] as a new last line and flushes it to disk. Existing lines are never rewritten. */
    fun append(entry: JsonObject) {
        val line = (CanonicalJson.write(entry) + "\n").toByteArray(Charsets.UTF_8)
        try {
            file.parent?.createDirectories()
            val options = setOf(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
            FileChannel.open(file, options).use { channel ->
                val end = completeEnd(channel)
                if (end < channel.size()) channel.truncate(end)
                channel.position(end)
                val buffer = ByteBuffer.wrap(line)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        } catch (e: IOException) {
            throw FormatException("Cannot write $file: ${e.reason()}", e)
        }
    }

    /** The last [n] entries, oldest first. Reads blocks from the end of the file, never the whole file. */
    fun readLast(n: Int): List<JsonObject> {
        require(n >= 0) { "n must not be negative" }
        if (n == 0 || !file.exists()) return emptyList()
        try {
            FileChannel.open(file, StandardOpenOption.READ).use { channel ->
                val end = completeEnd(channel)
                var start = end
                val chunks = ArrayDeque<ByteArray>()
                var newlines = 0
                // n complete lines need n + 1 newlines (the one before the oldest wanted line), or the file start.
                while (start > 0 && newlines <= n) {
                    val size = minOf(BLOCK_SIZE.toLong(), start).toInt()
                    start -= size
                    val chunk = ByteArray(size)
                    channel.position(start)
                    channel.read(ByteBuffer.wrap(chunk)).also { bytesRead += it }
                    newlines += chunk.count { it == NEWLINE }
                    chunks.addFirst(chunk)
                }
                val text = CanonicalJson.decodeUtf8(chunks.fold(ByteArray(0)) { a, b -> a + b }, file)
                var lines = text.split('\n').dropLast(1) // the text ends with a newline
                if (start > 0) lines = lines.drop(1) // the first line may be cut by the block
                val wanted = lines.takeLast(n)
                return wanted.mapIndexed { i, l -> parse(l, "${wanted.size - i} from the end") }
            }
        } catch (e: IOException) {
            throw FormatException("Cannot read $file: ${e.reason()}", e)
        }
    }

    /** All complete entries, oldest first. */
    fun readAll(): List<JsonObject> {
        if (!file.exists()) return emptyList()
        val text =
            try {
                CanonicalJson.readUtf8(file)
            } catch (e: IOException) {
                throw FormatException("Cannot read $file: ${e.reason()}", e)
            }
        // A last line without newline is torn; with a complete text the rest after the final newline is empty.
        val lines = text.split('\n').dropLast(1)
        return lines.mapIndexed { i, l -> parse(l, "${i + 1}") }
    }

    private fun parse(
        line: String,
        position: String,
    ): JsonObject =
        try {
            CanonicalJson.parse(line) as? JsonObject ?: throw FormatException("not an object")
        } catch (e: FormatException) {
            throw FormatException("${file.fileName}, line $position: ${e.message}", e)
        }

    /** The position just after the last newline: the end of the last complete line. */
    private fun completeEnd(channel: FileChannel): Long {
        var pos = channel.size()
        val one = ByteBuffer.allocate(1)
        while (pos > 0) {
            one.clear()
            channel.read(one, pos - 1)
            if (one.get(0) == NEWLINE) return pos
            pos--
        }
        return 0
    }

    private companion object {
        const val BLOCK_SIZE = 8 * 1024
        const val NEWLINE = '\n'.code.toByte()
    }
}
