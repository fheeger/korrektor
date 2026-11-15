package korrektor.pdf

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Something went wrong reading a PDF. The message says what, in words for the user. */
class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * PDFium as a whole: it is loaded and initialised on first use, and every call into it runs under one lock,
 * because PDFium is not thread-safe (D02). Wrapper classes call [call] around everything that touches a handle.
 */
internal object Pdfium {
    private val lock = ReentrantLock()
    private var ready = false
    private var failure: PdfException? = null

    /** Runs [block] with exclusive access to PDFium. */
    fun <T> call(block: () -> T): T =
        lock.withLock {
            ensureReady()
            block()
        }

    private fun ensureReady() {
        failure?.let { throw it }
        if (ready) return
        try {
            PdfiumLib.load()
            PdfiumLib.initLibrary()
            ready = true
        } catch (e: UnsatisfiedLinkError) {
            val problem =
                PdfException(
                    "The PDFium library could not be loaded (${e.message}). " +
                        "The build downloads it to build/pdfium; run it again with network access.",
                    e,
                )
            failure = problem
            throw problem
        }
    }
}
