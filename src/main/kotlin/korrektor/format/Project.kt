package korrektor.format

import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/**
 * How far a page is (S09). An open set like [RegionType]: a name this version does not know (a later version wrote it)
 * is kept as it is.
 */
@JvmInline
value class PageStatus(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "The page status must not be blank" }
    }

    companion object {
        val UNCHECKED = PageStatus("unchecked")
        val CORRECTED = PageStatus("corrected")
    }
}

/**
 * One page of the book in `project.json`. [pdfPage] is the page number in the source PDF (1-based). The [status] is
 * kept here and not in the page file, so that the progress of the whole book is known without reading any page.
 */
data class PageInfo(
    val pdfPage: Int,
    val extra: Map<String, JsonElement> = emptyMap(),
    val status: PageStatus = PageStatus.UNCHECKED,
) {
    init {
        require(pdfPage >= 1) { "pdfPage must be at least 1, was $pdfPage" }
        requireUnknownKeys(extra, setOf("pdfPage", "status"), "Page entry $pdfPage")
    }
}

/**
 * A project folder (`<title>.korrektur`, D03): `project.json`, `source.pdf`, `pages/`.
 *
 * Page numbers `n` are positions in [pages], starting at 1. Reading a page reads only that page's file.
 * The source PDF and the original OCR layers are never modified once written (N4, V5).
 */
class Project private constructor(
    val folder: Path,
    pages: List<PageInfo>,
    metadata: Metadata?,
    private val extra: Map<String, JsonElement>,
) {
    /**
     * The pages of the book, in order. Empty until the text of the PDF is imported (S03). Replaced as a whole, also by
     * the saver on another thread (a status), so a reader always sees one complete list.
     */
    @Volatile
    var pages: List<PageInfo> = pages
        private set

    /** The status of every page that is not unchecked, by page number (from 1). */
    val statuses: Map<Int, PageStatus>
        get() =
            pages
                .withIndex()
                .filter { it.value.status != PageStatus.UNCHECKED }
                .associate { it.index + 1 to it.value.status }

    /** The metadata, or null for a project that has none (made before S02). */
    var metadata: Metadata? = metadata
        private set

    val sourcePdf: Path get() = folder.resolve(SOURCE_PDF)

    /** The undo history of the whole book (S08): `history/book.jsonl`. It does not exist until something is saved. */
    val historyLog: HistoryLog get() = HistoryLog(folder.resolve(HISTORY_DIR).resolve(HISTORY_FILE))

    /** Writes `project.json` again. Without changes, the bytes are identical to what was read. */
    fun saveProjectFile() {
        val text = ProjectFile.encode(pages, metadata, extra)
        AtomicFile.write(folder.resolve(PROJECT_FILE), text.toByteArray(Charsets.UTF_8))
    }

    /** Replaces the metadata and writes `project.json`. If writing fails, the old metadata stays. */
    fun updateMetadata(new: Metadata) {
        val old = metadata
        metadata = new
        saveOrRevert { metadata = old }
    }

    /**
     * Sets the status of every page (S09): [all] says it for the pages that are not unchecked, every other page is
     * unchecked. Writes `project.json` only; if that fails, the old statuses stay.
     */
    fun setStatuses(all: Map<Int, PageStatus>) {
        val old = pages
        pages = old.mapIndexed { i, page -> page.copy(status = all[i + 1] ?: PageStatus.UNCHECKED) }
        saveOrRevert { pages = old }
    }

    /**
     * Replaces the list of pages and writes `project.json`. If writing fails, the old list stays. The import uses
     * it to say which pages the book has before the page files are written.
     */
    fun setPages(new: List<PageInfo>) {
        val old = pages
        pages = new
        saveOrRevert { pages = old }
    }

    /** The corrected layer of page [n]. */
    fun readPage(n: Int): PageLayer = read(pageFile(n, ".json"), n)

    fun writePage(
        n: Int,
        layer: PageLayer,
    ) = write(pageFile(n, ".json"), n, layer)

    /** The original OCR layer of page [n]. */
    fun readOriginalPage(n: Int): PageLayer = read(pageFile(n, ".ocr.json"), n)

    /** Writes the original OCR layer of page [n]. It can be written only once; the original is never changed. */
    fun writeOriginalPage(
        n: Int,
        layer: PageLayer,
    ) {
        val file = pageFile(n, ".ocr.json")
        if (file.exists()) {
            throw FormatException("The original OCR of page $n is already stored and is never overwritten")
        }
        write(file, n, layer)
    }

    private fun pageFile(
        n: Int,
        suffix: String,
    ): Path {
        if (n !in 1..pages.size) throw FormatException("Page $n does not exist; the project has ${pages.size} pages")
        return folder.resolve(PAGES_DIR).resolve(String.format(Locale.ROOT, "%04d%s", n, suffix))
    }

    private fun read(
        file: Path,
        n: Int,
    ): PageLayer {
        if (!file.exists()) throw FormatException("Page $n has no file ${file.fileName}")
        val text = readText(file)
        val layer =
            try {
                PageCodec.decode(text)
            } catch (e: FormatException) {
                throw FormatException("${file.fileName}: ${e.message}", e)
            }
        checkPdfPage(pages, n, layer)
        return layer
    }

    private fun write(
        file: Path,
        n: Int,
        layer: PageLayer,
    ) {
        checkPdfPage(pages, n, layer)
        AtomicFile.write(file, PageCodec.encode(layer).toByteArray(Charsets.UTF_8))
    }

    companion object {
        private fun checkPdfPage(
            pages: List<PageInfo>,
            n: Int,
            layer: PageLayer,
        ) {
            val expected = pages[n - 1].pdfPage
            if (layer.pdfPage != expected) {
                throw FormatException("Page $n belongs to PDF page $expected, but the layer says ${layer.pdfPage}")
            }
        }

        /** Writes `project.json`. If that fails, runs [revert] to put the old value back, and rethrows. */
        private inline fun Project.saveOrRevert(revert: () -> Unit) {
            try {
                saveProjectFile()
            } catch (
                // The old value must come back after any failure; the failure is rethrown.
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                revert()
                throw e
            }
        }

        const val FORMAT = "korrektur-project"

        /** The newest format version this app reads and writes. */
        const val VERSION = 1

        const val PROJECT_FILE = "project.json"
        const val SOURCE_PDF = "source.pdf"
        const val PAGES_DIR = "pages"
        const val HISTORY_DIR = "history"
        const val HISTORY_FILE = "book.jsonl"

        /**
         * Creates the project folder [folder] with a copy of [sourcePdf]. If anything fails, nothing is left behind.
         * [folder] must not exist yet.
         */
        fun create(
            folder: Path,
            sourcePdf: Path,
            pages: List<PageInfo>,
            metadata: Metadata? = null,
        ): Project = create(folder, sourcePdf, pages, metadata, AtomicFile::copy)

        /** [copyPdf] is a seam for tests, to simulate a failure while copying. */
        internal fun create(
            folder: Path,
            sourcePdf: Path,
            pages: List<PageInfo>,
            metadata: Metadata?,
            copyPdf: (Path, Path) -> Unit,
        ): Project {
            checkCanCreate(folder, sourcePdf)
            try {
                Files.createDirectory(folder)
                try {
                    copyPdf(sourcePdf, folder.resolve(SOURCE_PDF))
                    folder.resolve(PAGES_DIR).createDirectories()
                    val project = Project(folder, pages, metadata, emptyMap())
                    project.saveProjectFile()
                    // Last step: a read-only file would make cleanup after a failure harder.
                    // Best effort: not every file system supports it.
                    project.sourcePdf.toFile().setReadOnly()
                    return project
                } catch (
                    // Nothing may be left behind after any failure; the failure is rethrown.
                    @Suppress("TooGenericExceptionCaught") e: Throwable,
                ) {
                    deleteTree(folder)
                    throw e
                }
            } catch (e: IOException) {
                throw FormatException("Cannot create the project at $folder: ${e.reason()}", e)
            }
        }

        private fun checkCanCreate(
            folder: Path,
            sourcePdf: Path,
        ) {
            if (!Files.isRegularFile(sourcePdf)) throw FormatException("The PDF $sourcePdf does not exist")
            if (folder.exists()) throw FormatException("$folder already exists")
        }

        /** Opens an existing project. Nothing is changed if it can't be opened. */
        fun open(folder: Path): Project {
            val parsed = ProjectFile.decode(readText(projectFileOf(folder)))
            if (!folder.resolve(SOURCE_PDF).exists()) throw FormatException("$folder has no $SOURCE_PDF")
            AtomicFile.cleanupTemp(folder)
            return Project(folder, parsed.pages, parsed.metadata, parsed.extra)
        }

        private fun projectFileOf(folder: Path): Path {
            if (!folder.isDirectory()) throw FormatException("$folder is not a project folder")
            return folder.resolve(PROJECT_FILE).also {
                if (!it.exists()) throw FormatException("$folder has no $PROJECT_FILE")
            }
        }

        private fun readText(file: Path): String =
            try {
                CanonicalJson.readUtf8(file)
            } catch (e: IOException) {
                throw FormatException("Cannot read $file: ${e.reason()}", e)
            }
    }
}
