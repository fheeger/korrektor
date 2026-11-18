package korrektor.search

import korrektor.editor.PageEdits
import korrektor.editor.currentLayer
import kotlinx.coroutines.withContext

// Replacing from the panel (S10): "Replace selected" changes every checked hit as one step of the history, and
// "Replace" changes the hit that was gone to last, as a step of its own. The pages are read and the new text is worked
// out on the IO dispatcher; the step is recorded on the thread that called (the UI thread), where the editor's state is
// changed.

/** Replaces all checked hits in one go: one step of the history, which one Ctrl+Z takes back. */
suspend fun SearchModel.replaceSelected() = replace(selectedMatches(), single = null)

/** Replaces the hit that was gone to last, as a step of its own, and makes the one after it the current one. */
suspend fun SearchModel.replaceCurrent() {
    val index = current
    val hit = results.getOrNull(index) ?: return
    replace(listOf(hit), single = index)
}

/**
 * The "Replace" button: replaces the hit that was gone to last and goes to the one after it ([open] shows a result);
 * with no hit gone to yet it only goes to the first ([step], as F3 does).
 */
suspend fun SearchModel.replaceAndGoOn(
    open: (Int) -> Unit,
    step: (Boolean) -> Unit,
) {
    if (current !in results.indices) {
        step(true)
    } else {
        replaceCurrent()
        if (current >= 0) open(current)
    }
}

private suspend fun SearchModel.replace(
    hits: List<Match>,
    single: Int?,
) {
    val ready = compiled
    if (ready == null || hits.isEmpty() || replacing) return
    replacing = true
    outcome = null
    try {
        val with = replacement
        val mine = generation
        val plan = withContext(io) { planReplacement(hits, ready, with, editor.changed::get, editor::currentLayer) }
        if (plan.problem != null) {
            outcome = plan.problem
        } else {
            // A page that the user changed meanwhile is not replaced in; the pages where nothing changed are done.
            val unfit = plan.pages.map { it.page }.toSet() - editor.applyPlan(plan)
            val done = plan.replaced.filter { it.match.page !in unfit }
            layerAfterReplace = editor.layer
            // The query may have been changed while the pages were read: the results are those of a new search then.
            synchronized(lock) { if (mine == generation) afterReplacing(plan, done, single) }
            outcome = outcomeText(done.size, done.map { it.match.page }.distinct().size, hits.size - done.size)
        }
    } finally {
        replacing = false
    }
}

private fun outcomeText(
    replaced: Int,
    pages: Int,
    skipped: Int,
): String {
    val matches = "$replaced ${if (replaced == 1) "match" else "matches"}"
    val where = "$pages ${if (pages == 1) "page" else "pages"}"
    val left =
        if (skipped >
            0
        ) {
            " $skipped not replaced: their line had changed since the search, or their page could not be read."
        } else {
            ""
        }
    return if (replaced == 0) "Nothing replaced.$left" else "Replaced $matches on $where.$left"
}

/**
 * The results after a replacement: the hits that were replaced or skipped are gone, the others stay, and those in a
 * line that was changed are moved to where the hit is now. A hit that was checked stays checked. After a single
 * replacement ([single] is its index) the hit after it is the current one.
 */
private fun SearchModel.afterReplacing(
    plan: Plan,
    done: List<Replaced>,
    single: Int?,
) {
    val gone = (done.map { it.match } + plan.skipped).toSet()
    val moved = done.groupBy { it.match.page to it.match.lineId }
    // The text of each changed line, as the page has it now.
    val texts = HashMap<Pair<Int, String>, String>()
    for (page in plan.pages) {
        val lines = PageEdits.readingOrder(page.after).associate { it.id to it.text }
        for (key in moved.keys) if (key.first == page.page) lines[key.second]?.let { texts[key] = it }
    }
    val kept = ArrayList<Match>()
    val stillMarked = HashSet<Int>()
    var goneBefore = 0
    results.forEachIndexed { index, hit ->
        if (hit in gone) {
            if (single != null && index < single) goneBefore++
        } else {
            if (index in marked) stillMarked += kept.size
            val key = hit.page to hit.lineId
            kept += moveHit(hit, moved[key], texts[key])
        }
    }
    results.clear()
    results.addAll(kept)
    marked = stillMarked
    pagesWithHits = kept.map { it.page }.distinct().size
    lastHitPage = kept.lastOrNull()?.page ?: 0
    current = if (single == null || kept.isEmpty()) -1 else Math.floorMod(single - goneBefore, kept.size)
}

/** [hit] in a line where the hits [done] were replaced, which made [newText] of the line (both null: not changed). */
private fun moveHit(
    hit: Match,
    done: List<Replaced>?,
    newText: String?,
): Match {
    if (done == null || newText == null) return hit
    val shift = done.filter { it.match.start < hit.start }.sumOf { it.with.length - (it.match.end - it.match.start) }
    return hit.copy(lineText = newText, start = hit.start + shift, end = hit.end + shift)
}
