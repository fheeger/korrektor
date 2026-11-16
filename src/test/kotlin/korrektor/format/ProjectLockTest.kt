package korrektor.format

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A project cannot be open twice, and a lock left behind by a crash blocks nothing (T064). */
class ProjectLockTest {
    private fun withFolder(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("projectlock")
        try {
            block(dir)
        } finally {
            dir.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }
    }

    @Test
    fun aSecondLockOfTheSameFolderFailsUntilTheFirstIsClosed() =
        withFolder { dir ->
            val first = ProjectLock.acquire(dir)
            val error = assertFailsWith<ProjectLockedException> { ProjectLock.acquire(dir) }
            assertTrue(error.message!!.contains("already open"), error.message)
            first.close()
            ProjectLock.acquire(dir).close() // works again
        }

    @Test
    fun closingTwiceIsHarmless() =
        withFolder { dir ->
            val lock = ProjectLock.acquire(dir)
            lock.close()
            lock.close()
            ProjectLock.acquire(dir).close()
        }

    @Test
    fun differentFoldersAreLockedIndependently() =
        withFolder { dir ->
            val a = Files.createDirectory(dir.resolve("a"))
            val b = Files.createDirectory(dir.resolve("b"))
            ProjectLock.acquire(a).use {
                ProjectLock.acquire(b).close()
            }
        }

    @Test
    fun aLockFileLeftBehindDoesNotBlock() =
        withFolder { dir ->
            ProjectLock.acquire(dir).close()
            assertTrue(dir.resolve(ProjectLock.FILE).exists(), "the file stays: it is only the place of the lock")
            ProjectLock.acquire(dir).close()
        }

    @Test
    fun aLockFileWithOtherContentOrNoContentDoesNotBlock() =
        withFolder { dir ->
            val file = dir.resolve(ProjectLock.FILE)
            file.writeText("")
            ProjectLock.acquire(dir).close()
            file.writeText("pid 1, since the beginning of time")
            ProjectLock.acquire(dir).close()
            file.writeText("x".repeat(100_000))
            ProjectLock.acquire(dir).close()
        }

    @Test
    fun theLockFileHasALineForAPerson() =
        withFolder { dir ->
            // Read after the lock is given back: Windows does not let anybody else read a locked file.
            ProjectLock.acquire(dir).close()
            val text = dir.resolve(ProjectLock.FILE).readText()
            assertTrue(text.contains("korrektor") && text.contains("process "), text)
        }

    @Test
    fun aFolderWhoseLockFileCannotBeOpenedForWritingIsRefusedWithAReason() =
        withFolder { dir ->
            val file = dir.resolve(ProjectLock.FILE)
            file.writeText("")
            file.toFile().setReadOnly()
            if (file.toFile().canWrite()) return@withFolder // this file system does not make files read-only
            val error = assertFailsWith<FormatException> { ProjectLock.acquire(dir) }
            assertTrue(error.message!!.contains("cannot be opened for writing"), error.message)
        }

    // --- two processes -------------------------------------------------------------------------------------

    private fun startHolder(dir: Path): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process =
            ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                "korrektor.format.LockHolderKt",
                dir.toString(),
            ).redirectErrorStream(true).start()
        val line = process.inputStream.bufferedReader().readLine()
        assertEquals("held", line, "the other process holds the lock")
        return process
    }

    @Test
    fun anotherProcessHoldingTheLockBlocksUsAndReleasingItFrees() =
        withFolder { dir ->
            val other = startHolder(dir)
            try {
                assertFailsWith<ProjectLockedException> { ProjectLock.acquire(dir) }
                other.outputStream.close() // the holder ends, releasing the lock
                assertTrue(other.waitFor(30, TimeUnit.SECONDS))
                ProjectLock.acquire(dir).close()
            } finally {
                other.destroyForcibly()
            }
        }

    @Test
    fun aFailedSecondTryInTheSameProcessKeepsTheFirstLockHeld() =
        withFolder { dir ->
            ProjectLock.acquire(dir).use {
                repeat(3) { assertFailsWith<ProjectLockedException> { ProjectLock.acquire(dir) } }
                assertFailsWith<ProjectLockedException> { ProjectLock.acquire(dir) }
            }
        }

    @Test
    fun aLockLeftByAKilledProcessDoesNotBlock() =
        withFolder { dir ->
            val other = startHolder(dir)
            assertFailsWith<ProjectLockedException> { ProjectLock.acquire(dir) }
            other.destroyForcibly() // a crash: no chance to clean up
            assertTrue(other.waitFor(30, TimeUnit.SECONDS))
            assertTrue(File(dir.toFile(), ProjectLock.FILE).exists(), "the file of the dead process is still there")
            // The system gives the dead process's handles back a moment after it is gone.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (true) {
                try {
                    ProjectLock.acquire(dir).close()
                    break
                } catch (e: ProjectLockedException) {
                    if (System.nanoTime() > deadline) throw e
                    Thread.sleep(100)
                }
            }
        }
}
