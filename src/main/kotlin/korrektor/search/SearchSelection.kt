package korrektor.search

// The preview of a replacement (S10, replace mode): which hits are checked, and what a hit becomes. Nothing here changes
// the book: only "Replace selected" does that (see `Replace.kt`).

/** How much of the line is shown on either side of a hit in the preview: before and after take half the row each. */
private const val PREVIEW_CONTEXT = 15

/** Whether the hit at [index] is checked. */
fun SearchModel.isSelected(index: Int): Boolean = index !in excluded

/** Checks or unchecks the hit at [index]. */
fun SearchModel.setSelected(
    index: Int,
    on: Boolean,
) {
    excluded = if (on) excluded - index else excluded + index
}

/** Checks all the hits found so far. */
fun SearchModel.selectAll() {
    excluded = emptySet()
}

/** Unchecks all the hits found so far (hits that are found later are checked, as always). */
fun SearchModel.selectNone() {
    excluded = results.indices.toSet()
}

/** How many of the hits are checked. */
val SearchModel.selectedCount: Int get() = results.size - excluded.count { it < results.size }

/** The hits that are checked, in order. */
fun SearchModel.selectedMatches(): List<Match> = results.filterIndexed { index, _ -> index !in excluded }

/**
 * What [match] becomes with the replacement of the model: the snippet with the hit replaced, shown next to the one with
 * the hit as it is. A failure is the reason why the replacement is not valid. Null if there is no search (so nothing
 * to replace).
 */
fun SearchModel.preview(match: Match): Result<Snippet>? {
    val ready = compiled ?: return null
    return match
        .replacement(ready.regex, replacement, ready.literal)
        .map { match.snippet(it, PREVIEW_CONTEXT, PREVIEW_CONTEXT) }
}

/** The text of the line [match] is in after the replacement: for the preview next to the line as it is. */
fun Snippet.afterText(): String = before + (replacement ?: found) + after

/** Why the replacement cannot be used (checked on the first hit), or null if it can. */
fun SearchModel.replacementProblem(): String? =
    results.firstOrNull()?.let { preview(it)?.exceptionOrNull()?.replacementProblem() }
