package korrektor.format

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Crash-safe replacement of whole files: the target either has its old or its new content, never a mix.
 */
object AtomicFile {
    private const val TEMP_EXTENSION = "tmp"

    /** Replaces (or creates) [path] with [bytes]. On failure the target is untouched and no temp file stays. */
    fun write(
        path: Path,
        bytes: ByteArray,
    ) = write(path, bytes) { channel, data ->
        val buffer = ByteBuffer.wrap(data)
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    /** [writeStep] is a seam for tests, to simulate a failure halfway through writing. */
    internal fun write(
        path: Path,
        bytes: ByteArray,
        writeStep: (FileChannel, ByteArray) -> Unit,
    ) {
        val temp = path.resolveSibling("${path.fileName}.$TEMP_EXTENSION")
        deletingTempOnFailure(temp) {
            val options =
                setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            FileChannel.open(temp, options).use {
                writeStep(it, bytes)
                // Without this, the rename could reach the disk before the content does.
                it.force(true)
            }
            // The temp file is in the same folder, because an atomic rename only works within one file system.
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Copies [source] to [target] with the same guarantee as [write]: [target] is either missing or complete. */
    fun copy(
        source: Path,
        target: Path,
    ) {
        val temp = target.resolveSibling("${target.fileName}.$TEMP_EXTENSION")
        deletingTempOnFailure(temp) {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
            // On Windows the copy of a read-only file is read-only, and flushing it below needs write access.
            temp.toFile().setWritable(true)
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // Catches Throwable on purpose: the temp file must go away for any failure, and the failure is rethrown.
    @Suppress("TooGenericExceptionCaught")
    private inline fun deletingTempOnFailure(
        temp: Path,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (e: Throwable) {
            try {
                temp.deleteIfExists()
            } catch (cleanup: IOException) {
                e.addSuppressed(cleanup)
            }
            throw e
        }
    }

    /** Deletes `*.tmp` files left in [dir] and its subfolders by an earlier crash. Returns how many it deleted. */
    fun cleanupTemp(dir: Path): Int {
        var deleted = 0
        try {
            Files.walk(dir).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == TEMP_EXTENSION }.toList().forEach {
                    it.deleteIfExists()
                    deleted++
                }
            }
        } catch (e: IOException) {
            throw FormatException("Cannot clean up temporary files in $dir: ${e.reason()}", e)
        } catch (e: UncheckedIOException) {
            // Files.walk reports a problem while it walks (an unreadable subfolder) this way.
            throw FormatException("Cannot clean up temporary files in $dir: ${e.cause?.reason() ?: e.message}", e)
        }
        return deleted
    }
}
