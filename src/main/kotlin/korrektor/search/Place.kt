package korrektor.search

/** A place in the book: the page, the line (in reading order, from 1) and the column. Places are ordered as read. */
data class Place(
    val page: Int,
    val line: Int,
    val column: Int,
) : Comparable<Place> {
    override fun compareTo(other: Place): Int = compareValuesBy(this, other, Place::page, Place::line, Place::column)
}

/** The place of a result: where the hit starts. */
val Match.place: Place get() = Place(page, lineNumber, start)
