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
    fun append(entry: JsonObject) = appendAll(listOf(entry))

    /**
     * Adds [entries] as new last lines, in one write and one flush: either all of them are in the file afterwards or,
     * after a crash, a part of them (a torn last line is ignored). Existing lines are never rewritten.
     */
    fun appendAll(entries: List<JsonObject>) {
        if (entries.isEmpty()) return
        val bytes = entries.joinToString("") { CanonicalJson.write(it) + "\n" }.toByteArray(Charsets.UTF_8)
        try {
            file.parent?.createDirectories()
            val options = setOf(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
            FileChannel.open(file, options).use { channel ->
                val end = completeEnd(channel)
                if (end < channel.size()) channel.truncate(end)
                channel.position(end)
                writeAt(channel, end, bytes)
            }
        } catch (e: IOException) {
            throw FormatException("Cannot write $file: ${e.reason()}", e)
        }
    }

    /** Writes [bytes] at [end], the end of the complete lines, and flushes. On failure none of them is left behind. */
    private fun writeAt(
        channel: FileChannel,
        end: Long,
        bytes: ByteArray,
    ) {
        val buffer = ByteBuffer.wrap(bytes)
        try {
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        } catch (e: IOException) {
            // Entries must be all in or all out: a part of them left behind would be written again by a retry.
            runCatching { channel.truncate(end) }
            throw e
        }
    }

    /** The offset after the last complete line: where the next entry goes, and where reading backwards starts. */
    val end: Long
        get() {
            if (!file.exists()) return 0
            try {
                return FileChannel.open(file, StandardOpenOption.READ).use { completeEnd(it) }
            } catch (e: IOException) {
                throw FormatException("Cannot read $file: ${e.reason()}", e)
            }
        }

    /** Entries read backwards: those just before an offset (oldest first), and the offset where the first begins. */
    class Block(
        val entries: List<JsonObject>,
        val start: Long,
    )

    /** The last [n] entries, oldest first. Reads blocks from the end of the file, never the whole file. */
    fun readLast(n: Int): List<JsonObject> = readBefore(end, n).entries

    /**
     * Up to [n] complete entries that end at [end] (an offset that follows a newline: [end] itself, or the `start` of
     * a [Block]), oldest first, and the offset where the first of them begins, so that the next call can go on from
     * there. Reads blocks backwards from [end], never more than the entries need.
     */
    fun readBefore(
        end: Long,
        n: Int,
    ): Block {
        require(n >= 0) { "n must not be negative" }
        if (n == 0 || end <= 0 || !file.exists()) return Block(emptyList(), end)
        try {
            FileChannel.open(file, StandardOpenOption.READ).use { channel ->
                var start = end
                val chunks = ArrayDeque<ByteArray>()
                var seen = 0
                // n complete lines need n + 1 newlines (the one before the oldest wanted line), or the file start.
                while (start > 0 && seen <= n) {
                    val size = minOf(BLOCK_SIZE.toLong(), start).toInt()
                    start -= size
                    val chunk = ByteArray(size)
                    channel.position(start)
                    channel.read(ByteBuffer.wrap(chunk)).also { bytesRead += it }
                    seen += chunk.count { it == NEWLINE }
                    chunks.addFirst(chunk)
                }
                val all = ByteArray(chunks.sumOf { it.size })
                var position = 0
                for (chunk in chunks) {
                    chunk.copyInto(all, position)
                    position += chunk.size
                }
                // Where each line starts in [all]; the piece before the first newline is cut by the block, unless the
                // file starts there. The last element is the end of [all]: the start of no line.
                val newlines = all.indices.filter { all[it] == NEWLINE }.map { it + 1 }
                val starts = (if (start == 0L) listOf(0) else emptyList()) + newlines
                val wanted = starts.zipWithNext { from, next -> from to next - 1 }.takeLast(n)
                val entries =
                    wanted.mapIndexed { i, (from, to) ->
                        parse(
                            CanonicalJson.decodeUtf8(all.copyOfRange(from, to), file),
                            "${wanted.size - i} before $end",
                        )
                    }
                return Block(entries, start + (wanted.firstOrNull()?.first ?: all.size))
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
