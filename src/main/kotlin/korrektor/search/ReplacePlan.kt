package korrektor.search

import korrektor.editor.BookEditor
import korrektor.editor.PageEdits
import korrektor.editor.diff
import korrektor.editor.recordStep
import korrektor.format.LineChange
import korrektor.format.PageLayer

// Replacing hits (S10): first a plan is made (pure, on a background thread: it may read page files), then the plan is
// put in place as one step of the history (on the caller's thread). The plan says how each page changes; hits whose
// line is not as the search saw it are left out.

/** How one page changes: the layer it was made from ([base], null if not in memory in the editor), and the new one. */
internal class PagePlan(
    val page: Int,
    val base: PageLayer?,
    val after: PageLayer,
    val changes: List<LineChange>,
)

/** A hit that was replaced and what it became. */
internal class Replaced(
    val match: Match,
    val with: String,
)

/**
 * What replacing some hits comes to: the [pages] that change, the hits that are [replaced], and the hits that are
 * [skipped] because their page cannot be read or their line is not as it was when the search found them. If the
 * replacement is not valid, [problem] says why and nothing else is set.
 */
internal class Plan(
    val pages: List<PagePlan>,
    val replaced: List<Replaced>,
    val skipped: List<Match>,
    val problem: String? = null,
)

/**
 * Plans the replacement of [matches] by [with] (for a regex search, [with] has `$1` and the like). [changedOf] is what
 * the editor has in memory of a page, [layerOf] the page as it is now (the file if the editor has none); both are only
 * read. The new text of a line is the old one with all its hits replaced at once, so that several hits in a line, which
 * would otherwise move each other, are all right.
 */
internal fun planReplacement(
    matches: List<Match>,
    ready: Compiled.Ready,
    with: String,
    changedOf: (Int) -> PageLayer?,
    layerOf: (Int) -> PageLayer,
): Plan {
    val pages = ArrayList<PagePlan>()
    val replaced = ArrayList<Replaced>()
    val skipped = ArrayList<Match>()
    for ((page, inPage) in matches.groupBy { it.page }.toSortedMap()) {
        val base = changedOf(page)
        val before = base ?: runCatching { layerOf(page) }.getOrNull()
        if (before == null) {
            skipped += inPage
        } else {
            val done = planPage(before, inPage, ready, with)
            replaced += done.replaced
            skipped += done.skipped
            done.problem?.let { return Plan(emptyList(), emptyList(), emptyList(), it) }
            if (done.after !== before) pages += PagePlan(page, base, done.after, diff(page, before, done.after))
        }
    }
    return Plan(pages, replaced, skipped)
}

private class PageResult(
    val after: PageLayer,
    val replaced: List<Replaced>,
    val skipped: List<Match>,
    val problem: String? = null,
)

/** The page [before] with the hits [inPage] replaced, line by line. */
private fun planPage(
    before: PageLayer,
    inPage: List<Match>,
    ready: Compiled.Ready,
    with: String,
): PageResult {
    val texts = PageEdits.readingOrder(before).associate { it.id to it.text }
    var layer = before
    val replaced = ArrayList<Replaced>()
    val skipped = ArrayList<Match>()
    for ((lineId, inLine) in inPage.groupBy { it.lineId }) {
        val text = texts[lineId]
        val (usable, stale) = inLine.partition { it.lineText == text }
        skipped += stale
        if (usable.isNotEmpty() && text != null) {
            val made = replaceInLine(text, usable.sortedBy { it.start }, ready, with)
            made.problem?.let { return PageResult(before, emptyList(), emptyList(), it) }
            replaced += made.replaced
            layer = PageEdits.setLineText(layer, lineId, made.text).layer
        }
    }
    return PageResult(layer, replaced, skipped)
}

private class LineResult(
    val text: String,
    val replaced: List<Replaced>,
    val problem: String? = null,
)

/** [text] with the [hits] (in order, not overlapping) replaced. */
private fun replaceInLine(
    text: String,
    hits: List<Match>,
    ready: Compiled.Ready,
    with: String,
): LineResult {
    val out = StringBuilder()
    val replaced = ArrayList<Replaced>()
    var position = 0
    for (hit in hits) {
        val made = hit.replacement(ready.regex, with, ready.literal)
        val replacement =
            made.getOrNull() ?: return LineResult(text, emptyList(), made.exceptionOrNull()?.replacementProblem())
        out.append(text, position, hit.start).append(replacement)
        position = hit.end
        replaced += Replaced(hit, replacement)
    }
    out.append(text, position, text.length)
    return LineResult(out.toString(), replaced)
}

/**
 * Puts [plan] in place as one step of the history (`recordStep`) and returns the pages that were changed. A page that
 * was changed in the editor since the plan was made (the user typed meanwhile) is left alone: the plan was made from
 * its old state.
 */
internal fun BookEditor.applyPlan(plan: Plan): Set<Int> =
    synchronized(changedLock) {
        val fit = plan.pages.filter { changed[it.page] === it.base }
        if (fit.isNotEmpty()) recordStep(fit.flatMap { it.changes }, fit.associate { it.page to it.after })
        fit.map { it.page }.toSet()
    }
