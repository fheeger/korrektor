package korrektor.search

/** How much of the line is shown in front of and after a hit in the list of results. */
private const val CONTEXT_BEFORE = 30
private const val CONTEXT_AFTER = 40

/**
 * What a result of the list shows (S10): the text in front of the hit, the hit, and the text after it, each cut to a
 * length that fits a row (with `…` where it is cut). [replacement] is what the hit becomes, in replace mode.
 */
data class Snippet(
    val before: String,
    val found: String,
    val after: String,
    val replacement: String? = null,
)

/**
 * The snippet of this hit; [replacement] is what it would be replaced by, or null when only finding. [contextBefore]
 * and [contextAfter] are how many characters of the line are shown on either side.
 */
fun Match.snippet(
    replacement: String? = null,
    contextBefore: Int = CONTEXT_BEFORE,
    contextAfter: Int = CONTEXT_AFTER,
): Snippet =
    Snippet(
        before = if (start > contextBefore) "…" + lineText.substring(start - contextBefore, start) else before,
        found = found,
        after = if (lineText.length - end > contextAfter) after.take(contextAfter) + "…" else after,
        replacement = replacement,
    )

/**
 * The line of text under the search field: how many hits on how many pages, or how far a running search is, or why
 * there is nothing.
 */
fun searchSummary(model: SearchModel): String {
    val hits = model.results.size
    val pages = model.pagesWithHits
    return when {
        model.searching ->
            "Searching… ${model.pagesDone} of ${model.pagesTotal} pages · ${plural(hits, "match", "matches")}"
        model.query.text.isEmpty() -> ""
        hits == 0 && model.message != null -> ""
        hits == 0 -> "No matches"
        else -> "${plural(hits, "match", "matches")} on ${plural(pages, "page", "pages")}"
    }
}

private fun plural(
    count: Int,
    one: String,
    many: String,
) = "$count ${if (count == 1) one else many}"
