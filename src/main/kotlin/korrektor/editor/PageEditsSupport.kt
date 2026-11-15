package korrektor.editor

import korrektor.format.Box
import korrektor.format.CanonicalJson
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Word
import kotlin.math.max
import kotlin.math.min

private val ID_NUMBER = Regex("l(\\d+)")

// --- helpers of PageEdits --------------------------------------------------------------------------------------------

internal fun wordsOf(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split(' ')

/** The word [t] with the box of [original] if it is that very word (text and all), without a box if not. */
internal fun keepBox(
    original: Word?,
    t: String,
): Word = if (original != null && original.text == t) original else Word(t)

internal fun mergeWords(
    first: List<Word>,
    second: List<Word>,
): List<Word> =
    when {
        first.isEmpty() -> second
        second.isEmpty() -> first
        else -> first.dropLast(1) + mergeWord(first.last(), second.first()) + second.drop(1)
    }

/** The word made of two that meet: an empty one adds nothing, otherwise the texts join and so do the boxes. */
private fun mergeWord(
    a: Word,
    b: Word,
): Word =
    when {
        a.text.isEmpty() -> b
        b.text.isEmpty() -> a
        else -> Word(a.text + b.text, if (a.box != null && b.box != null) union(a.box, b.box) else null)
    }

/** The union of the boxes that [words] have (a word without a box adds nothing), or null if none has one. */
internal fun boxOfWords(words: List<Word>): Box? = words.mapNotNull { it.box }.reduceOrNull(::union)

internal fun union(
    a: Box?,
    b: Box?,
): Box? =
    when {
        a == null -> b
        b == null -> a
        else -> {
            val left = min(a.x, b.x)
            val top = min(a.y, b.y)
            val right = max(a.x + a.width, b.x + b.width)
            val bottom = max(a.y + a.height, b.y + b.height)
            Box(
                CanonicalJson.round1(left),
                CanonicalJson.round1(top),
                CanonicalJson.round1(right - left),
                CanonicalJson.round1(bottom - top),
            )
        }
    }

internal fun find(
    layer: PageLayer,
    lineId: String,
): Line =
    requireNotNull(layer.regions.flatMap { it.lines }.firstOrNull { it.id == lineId }) {
        "Line $lineId is not on page ${layer.pdfPage}"
    }

/** Replaces the line [lineId] by the lines [change] returns (none: it is removed). */
internal fun replaceLines(
    layer: PageLayer,
    lineId: String,
    change: (Line) -> List<Line>,
): PageLayer =
    layer.copy(
        regions =
            layer.regions.map { region ->
                if (region.lines.none { it.id == lineId }) {
                    region
                } else {
                    region.copy(lines = region.lines.flatMap { if (it.id == lineId) change(it) else listOf(it) })
                }
            },
    )

internal fun appendToLastRegion(
    layer: PageLayer,
    line: Line,
): PageLayer {
    val last = layer.regions.maxBy { it.order }
    return layer.copy(
        regions =
            layer.regions.map {
                if (it.id ==
                    last.id
                ) {
                    it.copy(lines = it.lines + line)
                } else {
                    it
                }
            },
    )
}

/** `l<N>` with N one more than the largest number among the ids `l<number>` of the page, so ids stay unique. */
internal fun newLineId(layer: PageLayer): String {
    val largest =
        layer.regions
            .flatMap { r ->
                r.lines.map { it.id }
            }.mapNotNull {
                ID_NUMBER
                    .matchEntire(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }.maxOrNull()
            ?: 0
    return "l${largest + 1}"
}
