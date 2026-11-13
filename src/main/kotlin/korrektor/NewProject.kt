package korrektor

import korrektor.format.AtomicFile
import korrektor.format.FormatException
import korrektor.format.Metadata
import korrektor.format.Project
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * The logic behind "New project" (A1), without any UI: check the PDF, name the project folder, create the
 * project with its copy of the PDF. Reading the pages of the PDF is the import (S03); the page list stays empty.
 */
object NewProject {
    private const val PROJECT_SUFFIX = ".korrektur"
    private const val UNTITLED = "Untitled"
    private const val MAX_NAME_LENGTH = 100
    private const val PDF_MARKER = "%PDF-"

    /** A PDF may have a few bytes of junk before its marker; viewers accept the marker within the first 1024 bytes. */
    private const val HEADER_BYTES = 1024

    // Forbidden in Windows file names, and the strictest of the platforms we support.
    private val UNSAFE_CHARACTERS = Regex("[<>:\"/\\\\|?*\\p{Cntrl}]")
    private const val MAX_DEVICE_NUMBER = 9
    private val DEVICE_NAMES =
        setOf("CON", "PRN", "AUX", "NUL") + (1..MAX_DEVICE_NUMBER).flatMap { listOf("COM$it", "LPT$it") }

    /** The title suggested for [pdf]: its file name without `.pdf`, underscores as spaces. */
    fun suggestTitle(pdf: Path): String =
        pdf.name
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(".pdf".length) else it }
            .replace('_', ' ')
            .trim()
            .ifEmpty { UNTITLED }

    /** The name of the project folder for [title]: `<title>.korrektur`, safe on every file system we support. */
    fun folderName(title: String): String {
        val safe =
            title
                .replace(UNSAFE_CHARACTERS, "_")
                .trimEnd('.', ' ')
                .take(MAX_NAME_LENGTH)
                .dropLastWhile { it.isHighSurrogate() } // do not cut a character in half
                .trimEnd('.', ' ')
                .ifEmpty { UNTITLED }
        // Windows treats a device name as one even with an extension, so only the part before the first dot counts.
        val reserved = safe.substringBefore('.').trimEnd(' ').uppercase() in DEVICE_NAMES
        return (if (reserved) "_$safe" else safe) + PROJECT_SUFFIX
    }

    /**
     * Creates the project for [pdf] in a new folder inside [parent], named after the title of [metadata].
     * If anything fails, a [FormatException] says why and nothing is left behind.
     */
    fun create(
        parent: Path,
        pdf: Path,
        metadata: Metadata,
    ): Project = create(parent, pdf, metadata, ::readHeader, AtomicFile::copy)

    /** [readPdfHeader] and [copyPdf] are seams for tests: an unreadable file, and a failure while copying. */
    internal fun create(
        parent: Path,
        pdf: Path,
        metadata: Metadata,
        readPdfHeader: (Path) -> ByteArray,
        copyPdf: (Path, Path) -> Unit,
    ): Project {
        checkPdf(pdf, readPdfHeader)
        val folder = checkTarget(parent, metadata.title)
        return Project.create(folder, pdf, emptyList(), metadata, copyPdf)
    }

    private fun readHeader(pdf: Path): ByteArray = Files.newInputStream(pdf).use { it.readNBytes(HEADER_BYTES) }

    private fun checkPdf(
        pdf: Path,
        readPdfHeader: (Path) -> ByteArray,
    ) {
        checkIsFile(pdf)
        val header =
            try {
                readPdfHeader(pdf)
            } catch (e: IOException) {
                throw FormatException("$pdf cannot be read: ${e.message}", e)
            }
        if (!String(header, Charsets.ISO_8859_1).contains(PDF_MARKER)) throw FormatException("$pdf is not a PDF file")
    }

    private fun checkIsFile(pdf: Path) {
        if (!pdf.exists()) throw FormatException("The file $pdf does not exist")
        if (!Files.isRegularFile(pdf)) throw FormatException("$pdf is not a file")
    }

    private fun checkTarget(
        parent: Path,
        title: String,
    ): Path {
        if (!parent.isDirectory()) throw FormatException("The folder $parent does not exist")
        val folder = parent.resolve(folderName(title))
        if (folder.exists()) throw FormatException("A project named ${folder.name} already exists in $parent")
        return folder
    }
}
