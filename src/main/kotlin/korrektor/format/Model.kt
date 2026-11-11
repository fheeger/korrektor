package korrektor.format

import kotlinx.serialization.json.JsonElement

/** Thrown when project data is invalid or cannot be read. The message says which part is wrong. */
class FormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A rectangle in PDF points, origin top-left. One decimal is the stored precision. */
data class Box(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(listOf(x, y, width, height).all { it.isFinite() }) { "Box values must be finite" }
    }
}

/**
 * A word. [box] is null for words typed by the user.
 * [extra] holds fields this version doesn't know, so they survive load and save.
 */
data class Word(
    val text: String,
    val box: Box? = null,
    val extra: Map<String, JsonElement> = emptyMap(),
)

/**
 * A line of the book. Its text is its [words] joined by one space; it is never normalized.
 * [origin] is the ID of the original-OCR line this one came from, null for a new line.
 * Corrected lines may have no [box] (S05).
 */
data class Line(
    val id: String,
    val box: Box? = null,
    val origin: String? = null,
    val words: List<Word>,
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Line ID must not be blank" }
        require(origin == null || origin.isNotBlank()) { "Line $id: origin must not be blank" }
    }

    val text: String get() = words.joinToString(" ") { it.text }
}

/** Region type. An open set: unknown values are kept as they are, so later features can add types. */
@JvmInline
value class RegionType(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Region type must not be blank" }
    }

    companion object {
        val TEXT = RegionType("text")
    }
}

/** A layout region with its lines in reading order. [order] is the reading order of regions on the page. */
data class Region(
    val id: String,
    val type: RegionType,
    val order: Int,
    val box: Box? = null,
    val lines: List<Line>,
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Region ID must not be blank" }
    }
}

/**
 * One layer (original OCR or corrected text) of one page. Both layers use this type.
 * Region and line IDs are unique within the page; region orders are unique too.
 */
data class PageLayer(
    val pdfPage: Int,
    val width: Double,
    val height: Double,
    val regions: List<Region>,
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(pdfPage >= 1) { "pdfPage must be at least 1, was $pdfPage" }
        require(width > 0 && height > 0) { "Page size must be positive, was $width x $height" }
        requireUnique(regions.map { it.id }, "region ID")
        requireUnique(regions.map { it.order }, "region order")
        requireUnique(regions.flatMap { r -> r.lines.map { it.id } }, "line ID")
    }

    private fun requireUnique(
        values: List<Any>,
        what: String,
    ) {
        val duplicate =
            values
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
        require(duplicate == null) { "Duplicate $what '${duplicate?.key}' on page $pdfPage" }
    }
}
