package korrektor.search

import korrektor.editor.currentLayer
import kotlinx.coroutines.delay

// The running of one search of a [SearchModel], and what it does to the model when hits arrive. Extensions, because the
// model itself has as many functions as the code checks allow.

private const val MAX_LISTED_PAGES = 5

/** The query to search with, or null if there is nothing to search (an empty text, or a message says why). */
internal fun SearchModel.readyQuery(): Compiled.Ready? =
    when (val result = query.compile()) {
        is Compiled.Ready -> result
        is Compiled.Invalid -> null.also { message = result.message }
        Compiled.Empty -> null
    }

internal suspend fun SearchModel.run(
    mine: Int,
    pages: IntRange,
    ready: Compiled.Ready,
) {
    delay(pause)
    val source = SearchSource(io, editor::currentLayer, chunk)
    val unreadable = searchBook(pages, ready.regex, source) { add(mine, it) }
    finish(mine, unreadable)
}

// A search that was cancelled or replaced may still hand over a batch: [mine] tells whether it is the current one.
internal fun SearchModel.add(
    mine: Int,
    batch: SearchBatch,
) = synchronized(lock) {
    if (mine == generation) {
        for (match in batch.matches) {
            if (match.page != lastHitPage) pagesWithHits++ // the hits come in page order
            lastHitPage = match.page
        }
        results.addAll(batch.matches)
        pagesDone = batch.pagesDone
    }
}

internal fun SearchModel.finish(
    mine: Int,
    unreadable: List<Int>,
) = synchronized(lock) {
    if (mine == generation) {
        searching = false
        if (unreadable.isNotEmpty()) {
            val shown = unreadable.take(MAX_LISTED_PAGES).joinToString(", ")
            message = "${unreadable.size} pages could not be read and were not searched: $shown"
        }
    }
}
