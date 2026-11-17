package korrektor.format

/**
 * One change of the book's text that the history can undo and redo (S08). A step of the history is a list of changes,
 * which may lie on several pages (a "replace all"). The changes of a step are made in order, each on the state that the
 * one before left. Later kinds of change (the page status, S09) are further cases.
 */
sealed interface Change {
    /** The page of the book (from 1) that the change is on. */
    val page: Int

    /** The change that undoes this one. */
    fun inverted(): Change
}

/**
 * In the region [region] of the page, the lines [removed] (as they were, with words and boxes) that start at position
 * [index] of the region's lines are replaced by the lines [inserted]. Every edit of S05 and S06 is one of these:
 * typing in a line replaces it, a split replaces one line by two, a join two by one, adding a line inserts one,
 * deleting removes one.
 *
 * [regionBefore] and [regionAfter] say whether the region exists before and after the change: the only edit that
 * makes a region is adding the first line of a page without regions, and undoing it makes the region go again, so that
 * undo restores the page exactly.
 */
data class LineChange(
    override val page: Int,
    val region: String,
    val index: Int,
    val removed: List<Line>,
    val inserted: List<Line>,
    val regionBefore: Boolean = true,
    val regionAfter: Boolean = true,
) : Change {
    init {
        require(page >= 1) { "page must be at least 1, was $page" }
        require(index >= 0) { "index must not be negative, was $index" }
        require(region.isNotBlank()) { "The region id must not be blank" }
    }

    override fun inverted(): LineChange =
        copy(removed = inserted, inserted = removed, regionBefore = regionAfter, regionAfter = regionBefore)
}
