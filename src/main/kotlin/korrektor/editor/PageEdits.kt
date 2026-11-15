package korrektor.editor

import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word

/** Where the cursor goes after an edit: character [index] of the line [lineId]. */
data class Focus(
    val lineId: String,
    val index: Int,
)

/** The page after an edit, and where the cursor belongs afterwards (null if it does not move). */
data class EditResult(
    val layer: PageLayer,
    val focus: Focus? = null,
)

/**
 * Correcting a page: changing, splitting, joining, adding and deleting lines. Pure functions on an immutable
 * [PageLayer]; the old layer is never changed, and a function that changes nothing returns the same layer.
 *
 * The text of a line is its words joined by one space (S01), so the words of a text are `text.split(' ')`, and an empty
 * text has no words. Nothing is trimmed or normalized (V1): two spaces make an empty word, which joins back to two
 * spaces. Boxes follow the rules in the ticket T037: a word that is unchanged in place keeps its box, and a line keeps
 * its box unless something better can be computed.
 */
object PageEdits {
    /**
     * Changes the text of a line. The line keeps its id, `origin` and `box`. If the number of words stays the same,
     * each word keeps its box (a corrected word is still where it was); if not, the words have no boxes.
     */
    fun setLineText(
        layer: PageLayer,
        lineId: String,
        text: String,
    ): EditResult {
        val line = find(layer, lineId)
        if (text == line.text) return EditResult(layer)
        val texts = wordsOf(text)
        val words =
            if (texts.size == line.words.size) {
                texts.mapIndexed { i, t -> line.words[i].copy(text = t) }
            } else {
                texts.map { Word(it) }
            }
        return EditResult(replaceLines(layer, lineId) { listOf(it.copy(words = words)) })
    }

    /**
     * Cuts the line at character [index] (0 to the length of the text); one space at the cut is dropped (the one
     * between two words). The first part keeps the line's id, `origin` and extra fields; the second part is a new line
     * after it with a new id and no `origin`. Words that the cut goes through lose their boxes. A part whose words all
     * have boxes gets their union as its box; otherwise the first part keeps the old box (if it has any text) and the
     * second has none. The cursor goes to the start of the second part.
     */
    fun splitLine(
        layer: PageLayer,
        lineId: String,
        index: Int,
    ): EditResult {
        val line = find(layer, lineId)
        val text = line.text
        require(index in 0..text.length) { "Cannot cut a line of ${text.length} characters at $index" }
        // At the boundary of two words the space between them is the line break: one space is dropped at the cut, so
        // that Enter between two words gives two clean lines. Any other space stays (nothing is trimmed, V1).
        var leftText = text.substring(0, index)
        var rightStart = index
        if (leftText.endsWith(' ')) {
            leftText = leftText.dropLast(1)
        } else if (index < text.length &&
            text[index] == ' '
        ) {
            rightStart++
        }
        val leftTexts = wordsOf(leftText)
        val rightTexts = wordsOf(text.substring(rightStart))
        // The right part starts with the original word that has as many spaces before it as the text before the cut.
        val firstOfRight = text.substring(0, rightStart).count { it == ' ' }
        val left = leftTexts.mapIndexed { i, t -> keepBox(line.words.getOrNull(i), t) }
        val right = rightTexts.mapIndexed { i, t -> keepBox(line.words.getOrNull(firstOfRight + i), t) }
        val newId = newLineId(layer)
        val first = line.copy(box = if (left.isEmpty()) null else boxOfWords(left) ?: line.box, words = left)
        val second = Line(newId, boxOfWords(right), null, right)
        val changed = replaceLines(layer, lineId) { listOf(first, second) }
        return EditResult(changed, Focus(newId, 0))
    }

    /**
     * Appends the text of the line directly to the previous line (no separator: the last word of the one and the first
     * word of the other become one word) and removes it. The previous line keeps its id and `origin`; its box becomes
     * the union of both. The first line of the page has nothing to join to and stays. The cursor goes to the junction.
     */
    fun joinWithPrevious(
        layer: PageLayer,
        lineId: String,
    ): EditResult {
        val order = readingOrder(layer)
        val position = order.indexOfFirst { it.id == lineId }
        require(position >= 0) { "Line $lineId is not on page ${layer.pdfPage}" }
        if (position == 0) return EditResult(layer)
        val previous = order[position - 1]
        val line = order[position]
        val merged = previous.copy(box = union(previous.box, line.box), words = mergeWords(previous.words, line.words))
        val joined = replaceLines(replaceLines(layer, previous.id) { listOf(merged) }, lineId) { emptyList() }
        return EditResult(joined, Focus(previous.id, previous.text.length))
    }

    /** The same as [joinWithPrevious] for the line after [lineId]: its text is appended to this one. */
    fun joinWithNext(
        layer: PageLayer,
        lineId: String,
    ): EditResult {
        val order = readingOrder(layer)
        val position = order.indexOfFirst { it.id == lineId }
        require(position >= 0) { "Line $lineId is not on page ${layer.pdfPage}" }
        if (position == order.lastIndex) return EditResult(layer)
        return joinWithPrevious(layer, order[position + 1].id)
    }

    /**
     * Adds a line with [text] after the line [lineId] (at the end of the page if it is null), in the same region. It
     * has no box and no `origin`. On a page without regions it creates the region `r1`. The cursor goes to its start.
     */
    fun addLineAfter(
        layer: PageLayer,
        lineId: String?,
        text: String = "",
    ): EditResult {
        val newId = newLineId(layer)
        val line = Line(newId, null, null, wordsOf(text).map { Word(it) })
        val added =
            when {
                lineId != null -> replaceLines(layer, lineId) { listOf(it, line) }
                layer.regions.isEmpty() ->
                    layer.copy(
                        regions = listOf(newRegion(line)),
                    )
                else -> appendToLastRegion(layer, line)
            }
        return EditResult(added, Focus(newId, 0))
    }

    /**
     * Removes a line. The cursor goes to the end of the previous line, or to the start of the next one if it was the
     * first. A region left without lines stays, with no lines.
     */
    fun deleteLine(
        layer: PageLayer,
        lineId: String,
    ): EditResult {
        val order = readingOrder(layer)
        val position = order.indexOfFirst { it.id == lineId }
        require(position >= 0) { "Line $lineId is not on page ${layer.pdfPage}" }
        val focus =
            order.getOrNull(position - 1)?.let { Focus(it.id, it.text.length) }
                ?: order.getOrNull(position + 1)?.let { Focus(it.id, 0) }
        return EditResult(replaceLines(layer, lineId) { emptyList() }, focus)
    }

    private fun newRegion(line: Line) = Region("r1", RegionType.TEXT, 1, null, listOf(line))

    /** The lines of the page in reading order: the regions by their `order`, each with its lines in order. */
    fun readingOrder(layer: PageLayer): List<Line> = layer.regions.sortedBy { it.order }.flatMap { it.lines }
}
