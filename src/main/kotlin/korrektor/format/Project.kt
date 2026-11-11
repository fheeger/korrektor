package korrektor.format

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/** One page of the book in `project.json`. [pdfPage] is the page number in the source PDF (1-based). */
data class PageInfo(
    val pdfPage: Int,
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(pdfPage >= 1) { "pdfPage must be at least 1, was $pdfPage" }
        requireUnknownKeys(extra, setOf("pdfPage"), "Page entry $pdfPage")
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
    val pages: List<PageInfo>,
    private val extra: Map<String, JsonElement>,
) {
    val sourcePdf: Path get() = folder.resolve(SOURCE_PDF)

    /** Writes `project.json` again. Without changes, the bytes are identical to what was read. */
    fun saveProjectFile() = AtomicFile.write(folder.resolve(PROJECT_FILE), encodeProjectFile(pages, extra).toByteArray(Charsets.UTF_8))

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
        if (file.exists()) throw FormatException("The original OCR of page $n is already stored and is never overwritten")
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
        return decodeChecked(file, n, readText(file))
    }

    private fun decodeChecked(
        file: Path,
        n: Int,
        text: String,
    ): PageLayer {
        val layer =
            try {
                PageCodec.decode(text)
            } catch (e: FormatException) {
                throw FormatException("${file.fileName}: ${e.message}", e)
            }
        checkPdfPage(n, layer)
        return layer
    }

    private fun write(
        file: Path,
        n: Int,
        layer: PageLayer,
    ) {
        checkPdfPage(n, layer)
        AtomicFile.write(file, PageCodec.encode(layer).toByteArray(Charsets.UTF_8))
    }

    private fun checkPdfPage(
        n: Int,
        layer: PageLayer,
    ) {
        val expected = pages[n - 1].pdfPage
        if (layer.pdfPage != expected) throw FormatException("Page $n belongs to PDF page $expected, but the layer says ${layer.pdfPage}")
    }

    companion object {
        const val FORMAT = "korrektur-project"

        /** The newest format version this app reads and writes. */
        const val VERSION = 1

        const val PROJECT_FILE = "project.json"
        const val SOURCE_PDF = "source.pdf"
        const val PAGES_DIR = "pages"

        /**
         * Creates the project folder [folder] with a copy of [sourcePdf]. If anything fails, nothing is left behind.
         * [folder] must not exist yet.
         */
        fun create(
            folder: Path,
            sourcePdf: Path,
            pages: List<PageInfo>,
        ): Project = create(folder, sourcePdf, pages, AtomicFile::copy)

        /** [copyPdf] is a seam for tests, to simulate a failure while copying. */
        internal fun create(
            folder: Path,
            sourcePdf: Path,
            pages: List<PageInfo>,
            copyPdf: (Path, Path) -> Unit,
        ): Project {
            if (!Files.isRegularFile(sourcePdf)) throw FormatException("The PDF $sourcePdf does not exist")
            if (folder.exists()) throw FormatException("$folder already exists")
            try {
                Files.createDirectory(folder)
                try {
                    copyPdf(sourcePdf, folder.resolve(SOURCE_PDF))
                    folder.resolve(PAGES_DIR).createDirectories()
                    val project = Project(folder, pages, emptyMap())
                    project.saveProjectFile()
                    // Last step: a read-only file would make cleanup after a failure harder. Best effort: not every file system supports it.
                    project.sourcePdf.toFile().setReadOnly()
                    return project
                } catch (e: Throwable) {
                    deleteTree(folder)
                    throw e
                }
            } catch (e: IOException) {
                throw FormatException("Cannot create the project at $folder: ${e.message}", e)
            }
        }

        /** Opens an existing project. Nothing is changed if it can't be opened. */
        fun open(folder: Path): Project {
            if (!folder.isDirectory()) throw FormatException("$folder is not a project folder")
            val file = folder.resolve(PROJECT_FILE)
            if (!file.exists()) throw FormatException("$folder has no $PROJECT_FILE")
            val (pages, extra) = decodeProjectFile(readText(file))
            if (!folder.resolve(SOURCE_PDF).exists()) throw FormatException("$folder has no $SOURCE_PDF")
            AtomicFile.cleanupTemp(folder)
            return Project(folder, pages, extra)
        }

        private fun readText(file: Path): String =
            try {
                CanonicalJson.readUtf8(file)
            } catch (e: IOException) {
                throw FormatException("Cannot read $file: ${e.message}", e)
            }

        private fun deleteTree(folder: Path) {
            folder.toFile().walkBottomUp().forEach {
                it.setWritable(true)
                it.delete()
            }
        }

        private fun encodeProjectFile(
            pages: List<PageInfo>,
            extra: Map<String, JsonElement>,
        ): String {
            val members = mutableListOf("  \"format\": ${CanonicalJson.quote(FORMAT)}", "  \"version\": $VERSION")
            members +=
                if (pages.isEmpty()) {
                    "  \"pages\": []"
                } else {
                    "  \"pages\": [\n" + pages.joinToString(",\n") { "    " + encodePage(it) } + "\n  ]"
                }
            extra.entries.sortedBy { it.key }.forEach { members += "  ${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }
            return "{\n" + members.joinToString(",\n") + "\n}\n"
        }

        private fun encodePage(page: PageInfo): String {
            val members = mutableListOf("\"pdfPage\": ${page.pdfPage}")
            page.extra.entries
                .sortedBy { it.key }
                .forEach { members += "${CanonicalJson.quote(it.key)}: ${CanonicalJson.write(it.value)}" }
            return "{" + members.joinToString(", ") + "}"
        }

        private fun decodeProjectFile(text: String): Pair<List<PageInfo>, Map<String, JsonElement>> {
            val root = CanonicalJson.parse(text) as? JsonObject ?: throw FormatException("$PROJECT_FILE: expected an object")
            val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (format != FORMAT) throw FormatException("$PROJECT_FILE: this is not a korrektur project (format is ${format ?: "missing"})")
            val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            if (version == null || version < 1) throw FormatException("$PROJECT_FILE: \"version\" must be a positive integer")
            if (version > VERSION) {
                throw FormatException("This project has format version $version; this app supports up to $VERSION. Please update the app.")
            }
            val pages =
                (
                    root["pages"] as? kotlinx.serialization.json.JsonArray
                        ?: throw FormatException("$PROJECT_FILE: \"pages\" must be an array")
                ).mapIndexed { i, p -> decodePage(p, i) }
            return pages to root.filterKeys { it !in setOf("format", "version", "pages") }
        }

        private fun decodePage(
            element: JsonElement,
            index: Int,
        ): PageInfo {
            val o = element as? JsonObject ?: throw FormatException("$PROJECT_FILE: pages[$index] must be an object")
            val pdfPage = (o["pdfPage"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            if (pdfPage == null || pdfPage < 1) throw FormatException("$PROJECT_FILE: pages[$index].pdfPage must be a positive integer")
            return PageInfo(pdfPage, o.filterKeys { it != "pdfPage" })
        }
    }
}
