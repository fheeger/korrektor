package korrektor.importer

import korrektor.pdf.PdfChar
import korrektor.pdf.PdfRect
import korrektor.pdf.PdfTextPage

/** A word of a page's text layer, in PDF coordinates (points, origin bottom-left, see [PdfRect]). */
data class RawWord(
    val text: String,
    val box: PdfRect,
)

/**
 * Turns the characters of a text layer into words, following "Rules for the import" in
 * `docs/pdf-text-layer.md`. The text is the PDF's own: no normalization (V1), no repair of OCR errors, and no
 * joining of hyphenated words.
 */
object WordExtractor {
    /**
     * A generated space separates words on a page that has real spaces only if the gap to the next character is
     * at least this many times the font size. A real word space is about 0.45 of the font size, the gaps inside
     * letter-spaced headings are smaller (section 2 of the analysis).
     */
    private const val GAP_FACTOR = 0.6

    private const val SPACE = 0x20
    private const val HYPHEN = '-'.code

    /** The words of [text], in the order PDFium gives the characters. */
    fun words(text: PdfTextPage): List<RawWord> = split(List(text.charCount) { text.char(it) })

    /** The same on a list of characters, so the rules can be tested without a PDF. */
    internal fun split(chars: List<PdfChar>): List<RawWord> {
        val pageHasRealSpaces = chars.any { !it.generated && it.unicode == SPACE }
        val collector = Collector()
        for ((index, c) in chars.withIndex()) {
            when {
                c.generated -> {
                    val next = chars.getOrNull(index + 1)
                    if (generatedSeparates(c, next, collector.right, pageHasRealSpaces)) collector.endWord()
                }
                // PDFium's marker for a hyphen at the end of a line (and it dropped the line break): the word
                // ends with the hyphen, and the next character starts a new word.
                c.hyphen -> {
                    collector.add(HYPHEN, c)
                    collector.endWord()
                }
                Character.isWhitespace(c.unicode) -> collector.endWord()
                c.unicode < SPACE -> Unit // U+0000 and control characters are not text
                else -> collector.add(c.unicode, c)
            }
        }
        collector.endWord()
        return collector.words
    }

    private fun generatedSeparates(
        generated: PdfChar,
        next: PdfChar?,
        wordRight: Double?,
        pageHasRealSpaces: Boolean,
    ): Boolean =
        when {
            generated.unicode != SPACE -> true // the line breaks PDFium makes up
            !pageHasRealSpaces -> true // the words are separate text pieces: every gap is a word boundary
            next == null || next.generated || wordRight == null -> true // nothing to measure
            else -> next.box.left - wordRight >= GAP_FACTOR * next.fontSize
        }

    /** Collects the characters of the word being built and the finished words. */
    private class Collector {
        val words = mutableListOf<RawWord>()
        private val text = StringBuilder()
        private var left = 0.0
        private var bottom = 0.0
        var right: Double? = null
            private set
        private var top = 0.0

        /** Adds a character: [unicode] is a code point, or half of a UTF-16 pair, which joins its other half. */
        fun add(
            unicode: Int,
            c: PdfChar,
        ) {
            text.appendCodePoint(unicode)
            val box = c.box
            if (right == null) {
                left = box.left
                bottom = box.bottom
                top = box.top
                right = box.right
            } else {
                left = minOf(left, box.left)
                bottom = minOf(bottom, box.bottom)
                top = maxOf(top, box.top)
                right = maxOf(right ?: box.right, box.right)
            }
        }

        fun endWord() {
            val end = right
            if (end != null && text.isNotEmpty()) words += RawWord(text.toString(), PdfRect(left, bottom, end, top))
            text.setLength(0)
            right = null
        }
    }
}
