package korrektor.format

import kotlinx.serialization.json.JsonElement

/** Thrown when project data is invalid or cannot be read. The message says which part is wrong. */
class FormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Largest coordinate or page size accepted; far beyond any real page, and safe for one-decimal rounding. */
internal const val MAX_COORDINATE = 1e9

/** Unknown fields must not reuse the name of a known field: the file would contain it twice. */
internal fun requireUnknownKeys(
    extra: Map<String, JsonElement>,
    known: Set<String>,
    what: String,
) {
    val clash = extra.keys.firstOrNull { it in known }
    require(clash == null) { "$what: unknown field '$clash' has the name of a known field" }
}

/** A rectangle in PDF points, origin top-left. One decimal is the stored precision. */
data class Box(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    init {
        require(
            listOf(x, y, width, height).all {
                it.isFinite() && kotlin.math.abs(it) <= MAX_COORDINATE
            },
        ) { "Box values must be finite and within $MAX_COORDINATE" }
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
) {
    init {
        requireUnknownKeys(extra, setOf("t", "b"), "Word")
    }
}

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
        requireUnknownKeys(extra, setOf("id", "box", "origin", "words"), "Line $id")
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
        requireUnknownKeys(extra, setOf("id", "type", "order", "box", "lines"), "Region $id")
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
        require(
            CanonicalJson.round1(width) > 0 && CanonicalJson.round1(height) > 0 && width <= MAX_COORDINATE && height <= MAX_COORDINATE,
        ) {
            "Page size must be between 0.1 and $MAX_COORDINATE points, was $width x $height"
        }
        requireUnknownKeys(extra, setOf("pdfPage", "width", "height", "regions"), "Page $pdfPage")
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

/**
 * The metadata of a project (A1). All values are free text, stored exactly as typed (V1), so `ca. 1790` or
 * `1785?` are fine as a year. Only the [title] is required. Empty values are not written to the file.
 */
data class Metadata(
    val title: String,
    val author: String = "",
    val year: String = "",
    val source: String = "",
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) { "The title must not be blank" }
        requireUnknownKeys(extra, setOf("title", "author", "year", "source"), "Metadata")
    }
}
