package korrektor.export

import korrektor.format.Metadata

/**
 * Markdown (CommonMark and GFM): printed line breaks are hard breaks (`\` at the end of the line), a page is a
 * paragraph, its marker a paragraph of its own, and the title of the header a heading. Everything that comes from the
 * book is escaped (see [MarkdownEscape]), so that it is read as the text it is.
 */
internal object MarkdownStyle : ExportStyle {
    override val lineBreak = "\\"
    override val markerGap = "\n\n"

    override fun text(
        text: String,
        atStart: Boolean,
    ) = MarkdownEscape.text(text, atStart)

    override fun marker(pdfPage: Int) = "\\[Page $pdfPage\\]"

    override fun header(metadata: Metadata): List<String> {
        val lines = headerLines(metadata, MarkdownEscape::inline)
        val title = "# " + MarkdownEscape.closingHashes(lines.first())
        val fields = lines.drop(1)
        return if (fields.isEmpty()) listOf(title) else listOf(title, fields.joinToString("\\\n"))
    }
}

/** The text of a line as Markdown that reads as exactly that text, and nothing else. */
internal object MarkdownEscape {
    // Each of these means something to a Markdown reader, wherever it stands in a text.
    private const val INLINE_MARKUP = "\\`*_[]<>~"

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{0,31});")
    private val NUMBER = Regex("^[0-9]{1,9}(?=[.)])")
    private const val LINE_START_MARKUP = "#-+=|"

    /** [text] with its markup escaped, for use inside a line (a title, a field of the header). */
    fun inline(text: String): String {
        val result = StringBuilder()
        for ((i, c) in text.withIndex()) {
            val entity = c == '&' && ENTITY.matchesAt(text, i)
            if (c in INLINE_MARKUP || entity) result.append('\\')
            result.append(c)
        }
        return result.toString()
    }

    /**
     * [text] as one line of a paragraph. At the start of a paragraph or a kept line ([atStart]) it also cannot look
     * like a list, a heading, a rule or a table; a space or tab at either end, which a reader would strip, is written
     * as a character reference so that it is kept.
     */
    fun text(
        text: String,
        atStart: Boolean,
    ): String {
        var escaped = inline(text)
        if (atStart) escaped = atLineStart(escaped)
        return keepSpaceAtEnd(escaped)
    }

    /** The closing `#` of a heading would be read as the closing sequence of the heading; the last one is escaped. */
    fun closingHashes(heading: String): String = if (heading.endsWith("#")) heading.dropLast(1) + "\\#" else heading

    private fun atLineStart(text: String): String {
        val first = text.firstOrNull() ?: return text
        val number = NUMBER.find(text)
        return when {
            // Four spaces are code, and a list can be indented: the first space becomes a character reference.
            first == ' ' -> "&#32;" + text.drop(1)
            first == '\t' -> "&#9;" + text.drop(1)
            first in LINE_START_MARKUP -> "\\$text"
            first == ':' && text.startsWith(":-") -> "\\$text"
            number != null -> text.substring(0, number.value.length) + "\\" + text.substring(number.value.length)
            else -> text
        }
    }

    private fun keepSpaceAtEnd(text: String): String =
        when (text.lastOrNull()) {
            ' ' -> text.dropLast(1) + "&#32;"
            '\t' -> text.dropLast(1) + "&#9;"
            else -> text
        }
}
