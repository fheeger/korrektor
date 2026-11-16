package korrektor.format

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/** The project is open already: in another window of this app, or in another instance (S07). */
class ProjectLockedException(
    message: String,
) : Exception(message)

/**
 * The right to work on a project folder (S07): held while the project is open, so that it cannot be open twice, not
 * in two windows of one app and not in two instances. Two writers on one folder would overwrite each other's pages.
 *
 * The lock is an operating system file lock on `.lock` in the project folder. The file is only the place where the
 * lock is taken: when the process ends, also by a crash or a power cut, the system releases the lock, and the file
 * that stays behind blocks nothing, because the next [acquire] simply locks it again. That is why there is no "is
 * this lock stale?" check, which could be wrong. [close] gives the lock back and leaves the file: deleting it could
 * remove the file under a process that has just taken the lock, and then two could hold "a" lock.
 *
 * The format layer (`Project.open`) does not lock; the app does (`AppModel`), as only it keeps a project open.
 */
class ProjectLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    /** Gives the lock back. Safe to call twice. */
    override fun close() {
        try {
            lock.release()
        } catch (_: IOException) {
            // Closing the channel below releases it anyway.
        }
        try {
            channel.close()
        } catch (_: IOException) {
            // Nothing is left to do for a file that cannot be closed.
        }
    }

    companion object {
        const val FILE = ".lock"

        /**
         * Takes the lock of the project in [folder]. Throws [ProjectLockedException] if somebody else holds it, and
         * [FormatException] if the lock file cannot be made or opened (for example a read-only project folder, which
         * could not be saved to either).
         */
        fun acquire(folder: Path): ProjectLock {
            val file = folder.resolve(FILE)
            val channel =
                try {
                    FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                } catch (e: IOException) {
                    throw FormatException("$folder cannot be opened for writing: ${e.reason()}", e)
                }
            val lock = tryLock(channel)
            if (lock == null) {
                closeQuietly(channel)
                throw ProjectLockedException(
                    "The project ${folder.fileName} is already open, in another window or another instance of the app.",
                )
            }
            describe(channel)
            return ProjectLock(channel, lock)
        }

        /** The lock, or null if it is held: by another process (null) or, in this JVM, by another channel. */
        private fun tryLock(channel: FileChannel): FileLock? =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (e: IOException) {
                closeQuietly(channel)
                throw FormatException("The project cannot be locked: ${e.reason()}", e)
            }

        /** A line for a person who looks into the file; nothing reads it. A failure here is no reason to refuse. */
        private fun describe(channel: FileChannel) {
            try {
                val text = "korrektor, process ${ProcessHandle.current().pid()}, since ${Instant.now()}\n"
                channel.truncate(0)
                channel.write(java.nio.ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), 0)
            } catch (_: IOException) {
                // The lock is what counts.
            }
        }

        private fun closeQuietly(channel: FileChannel) {
            try {
                channel.close()
            } catch (_: IOException) {
                // Already closed or unusable: nothing to add to the error that is being reported.
            }
        }
    }
}
