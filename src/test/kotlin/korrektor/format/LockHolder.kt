package korrektor.format

import java.nio.file.Path

/**
 * A process for tests of the project lock (`ProjectLockProcessTest`): takes the lock of the folder in the first
 * argument, says "held" on its output, and then holds it until it is killed or its input is closed.
 */
fun main(args: Array<String>) {
    val lock = ProjectLock.acquire(Path.of(args[0]))
    println("held")
    System.out.flush()
    // Reads until the test closes the input, or the process is destroyed.
    while (System.`in`.read() >= 0) {
        // nothing to do
    }
    lock.close()
}
