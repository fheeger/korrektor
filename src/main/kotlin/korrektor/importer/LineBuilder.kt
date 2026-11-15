package korrektor.importer

import korrektor.format.Box
import korrektor.format.CanonicalJson
import korrektor.format.Line
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word

/** A word with its box in the coordinates of the format (see [PageGeometry]). */
data class PlacedWord(
    val text: String,
    val box: Box,
)

/**
 * Groups the words of a page into lines in reading order and wraps them in one region. Layout detection (B3) comes
 * later, so every page gets a single body region; on a page with several columns the lines of the columns are
 * therefore mixed up until the layout is detected and can be fixed (B3, B4).
 *
 * The order in which PDFium gives the words is not trusted (about one line start in fourteen lies above the line
 * before it, see `docs/pdf-text-layer.md`), so the lines come from the positions of the words.
 */
object LineBuilder {
    /** A word joins a line if it overlaps the line's latest word vertically by this much of the smaller height. */
    private const val MIN_OVERLAP = 0.5

    /** The body region of a page with the given words, or null if there are none. Words and boxes are unchanged. */
    fun build(words: List<PlacedWord>): Region? {
        if (words.isEmpty()) return null
        val lines =
            group(words)
                .map { line -> line.sortedBy { it.box.x } }
                .sortedBy { line -> median(line.map { it.box.y + it.box.height / 2 }) }
                .mapIndexed { index, line -> toLine("l${index + 1}", line) }
        return Region(
            id = "r1",
            type = RegionType.TEXT,
            order = 1,
            box = union(lines.mapNotNull { it.box }),
            lines = lines,
        )
    }

    /**
     * Sweeps the words from left to right. Each one joins the line whose latest word it overlaps most, if that is
     * enough, and starts a new line otherwise. Comparing with the latest word, not with the whole line, lets a line
     * whose baseline drifts across the page (a skewed scan) stay together.
     */
    private fun group(words: List<PlacedWord>): List<List<PlacedWord>> {
        val lines = mutableListOf<MutableList<PlacedWord>>()
        for (word in words.sortedWith(compareBy({ it.box.x }, { it.box.y }))) {
            val best = lines.maxByOrNull { overlap(it.last().box, word.box) }
            if (best != null &&
                overlap(best.last().box, word.box) >= MIN_OVERLAP
            ) {
                best += word
            } else {
                lines += mutableListOf(word)
            }
        }
        return lines
    }

    /** How much of the smaller height of the two boxes they share vertically: 0 for none, 1 for all of it. */
    private fun overlap(
        a: Box,
        b: Box,
    ): Double {
        val shared = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
        val smaller = minOf(a.height, b.height)
        return if (smaller <= 0.0) 0.0 else shared / smaller
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 ==
            1
        ) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
    }

    private fun toLine(
        id: String,
        words: List<PlacedWord>,
    ) = Line(id = id, box = union(words.map { it.box }), words = words.map { Word(it.text, it.box) })

    /** The smallest box that holds all [boxes], with one decimal like every box. */
    private fun union(boxes: List<Box>): Box? {
        if (boxes.isEmpty()) return null
        val left = boxes.minOf { it.x }
        val top = boxes.minOf { it.y }
        val right = boxes.maxOf { it.x + it.width }
        val bottom = boxes.maxOf { it.y + it.height }
        return Box(
            CanonicalJson.round1(left),
            CanonicalJson.round1(top),
            CanonicalJson.round1(right - left),
            CanonicalJson.round1(bottom - top),
        )
    }
}
