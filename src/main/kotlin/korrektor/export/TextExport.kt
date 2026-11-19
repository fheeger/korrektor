package korrektor.export

import korrektor.format.Metadata

/** One page of the export: its number in the PDF (for the marker) and the text of its lines in reading order. */
class ExportPage(
    val pdfPage: Int,
    val lines: List<String>,
)

/**
 * What differs between the formats: how a text, a marker and the header are written. The shared part (which blocks
 * there are and where) is [render].
 */
internal interface ExportStyle {
    /** What ends a line of a paragraph that is followed by another one when the printed line breaks are kept. */
    val lineBreak: String

    /** What lies between the marker of a page and its text: a line break, or a blank line (a paragraph of its own). */
    val markerGap: String

    /** [text] as it must be written so that it reads as itself; [atStart] if it is the first thing of a paragraph. */
    fun text(
        text: String,
        atStart: Boolean,
    ): String

    fun marker(pdfPage: Int): String

    /** The header as blocks (they are separated by a blank line). */
    fun header(metadata: Metadata): List<String>
}

/** Plain text: nothing is escaped, the header is the title and then one line for each field that is not empty. */
internal object PlainStyle : ExportStyle {
    override val lineBreak = ""
    override val markerGap = "\n"

    override fun text(
        text: String,
        atStart: Boolean,
    ) = text

    override fun marker(pdfPage: Int) = "[Page $pdfPage]"

    override fun header(metadata: Metadata): List<String> = listOf(headerLines(metadata) { it }.joinToString("\n"))
}

/** The title, then the fields that are not empty as `Label: value`, each in its own line (a text is one line). */
internal fun headerLines(
    metadata: Metadata,
    escape: (String) -> String,
): List<String> =
    listOf(escape(oneLine(metadata.title))) +
        listOf("Author" to metadata.author, "Year" to metadata.year, "Source" to metadata.source)
            .filter { it.second.isNotBlank() }
            .map { (label, value) -> "$label: ${escape(oneLine(value))}" }

/** A CR or LF inside a text would break the structure of the file: it counts as a space. */
internal fun oneLine(text: String): String = text.replace(LINE_BREAKS, " ")

private val LINE_BREAKS = Regex("\r\n|\r|\n")

/** The export of [pages] as plain text, written to [out] (see [render]). */
fun renderText(
    metadata: Metadata?,
    pages: Sequence<ExportPage>,
    options: ExportOptions,
    out: Appendable,
) = render(PlainStyle, metadata, pages, options, out)

/**
 * Writes the export of [pages] to [out], page by page, so that a whole book is never one string. A page is a block:
 * its marker (if wanted), then its lines, either each as a line of the file or joined by spaces; blocks are separated
 * by one blank line, and the file ends with one `\n`. A line with no text is left out, and so is a page with no
 * lines (but its marker is written, so that the page is not missing from the file).
 */
internal fun render(
    style: ExportStyle,
    metadata: Metadata?,
    pages: Sequence<ExportPage>,
    options: ExportOptions,
    out: Appendable,
) {
    var first = true

    fun block(text: String) {
        if (!first) out.append("\n\n")
        out.append(text)
        first = false
    }
    if (options.header && metadata != null) style.header(metadata).forEach(::block)
    for (page in pages) {
        val body = body(style, page, options.keepLineBreaks)
        val marker = if (options.pageMarkers) style.marker(page.pdfPage) else null
        when {
            marker != null && body.isNotEmpty() -> block(marker + style.markerGap + body)
            marker != null -> block(marker)
            body.isNotEmpty() -> block(body)
        }
    }
    if (!first) out.append('\n')
}

private fun body(
    style: ExportStyle,
    page: ExportPage,
    keepLineBreaks: Boolean,
): String {
    val texts = page.lines.map(::oneLine).filter { it.isNotEmpty() }
    return if (keepLineBreaks) {
        texts.joinToString(style.lineBreak + "\n") { style.text(it, atStart = true) }
    } else {
        texts.withIndex().joinToString(" ") { (i, text) -> style.text(text, atStart = i == 0) }
    }
}
