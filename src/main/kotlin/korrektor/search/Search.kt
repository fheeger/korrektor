package korrektor.search

import korrektor.editor.PageEdits
import korrektor.format.PageLayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext

/** The number of pages that are read and searched together; the matches of such a chunk come as one batch. */
const val DEFAULT_CHUNK = 16

/**
 * The text of a line for the regex engine. A regular expression that backtracks badly can run for a very long time on
 * one line, and a coroutine cannot be cancelled while it is inside it: the engine reads every character through here,
 * and a cancelled [job] stops the search by an exception.
 */
private class Interruptible(
    private val text: CharSequence,
    private val job: Job?,
) : CharSequence {
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        if (job?.isActive == false) throw CancellationException("The search was cancelled")
        return text[index]
    }

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = Interruptible(text.subSequence(startIndex, endIndex), job)

    override fun toString(): String = text.toString()
}

/**
 * The hits of [regex] in the corrected [layer] of [page] (S10), in reading order. A hit lies within one line, and an
 * empty hit (a regex such as `x*`) is not a hit. [job], if given, is what cancels a long search inside a line.
 */
fun searchLayer(
    page: Int,
    layer: PageLayer,
    regex: Regex,
    job: Job? = null,
): List<Match> {
    val found = ArrayList<Match>()
    PageEdits.readingOrder(layer).forEachIndexed { index, line ->
        val text = line.text
        if (text.isNotEmpty()) {
            for (hit in regex.findAll(Interruptible(text, job))) {
                if (hit.value.isNotEmpty()) {
                    found += Match(page, line.id, index + 1, text, hit.range.first, hit.range.last + 1)
                }
            }
        }
    }
    return found
}

/** What the search of some pages found: the [matches] and how many pages are done after them. */
class SearchBatch(
    val matches: List<Match>,
    val pagesDone: Int,
)

/**
 * Where [searchBook] gets the pages from: [readLayer] reads one (and must be safe to call from several threads), on
 * [io]; [chunk] pages are read and searched together.
 */
class SearchSource(
    val io: CoroutineDispatcher,
    val readLayer: (Int) -> PageLayer,
    val chunk: Int = DEFAULT_CHUNK,
)

/**
 * Searches the pages [pages] one chunk after the other and reports each chunk as a batch, in page order, so that the
 * caller can show the hits while the rest is still being searched. The pages of a chunk are read and searched in
 * parallel (see [SearchSource]). A cancelled search
 * stops at once and reports nothing more. Returns the pages that could not be read: they are skipped.
 */
suspend fun searchBook(
    pages: IntRange,
    regex: Regex,
    source: SearchSource,
    onBatch: suspend (SearchBatch) -> Unit,
): List<Int> {
    val unreadable = ArrayList<Int>()
    var done = 0
    for (numbers in pages.chunked(source.chunk)) {
        coroutineContext.ensureActive()
        val job = coroutineContext[Job]
        val results =
            coroutineScope {
                numbers
                    .map { number ->
                        async(source.io) { searchPage(number, regex, source.readLayer, job) }
                    }.awaitAll()
            }
        val matches = ArrayList<Match>()
        for ((number, result) in numbers.zip(results)) {
            if (result == null) unreadable += number else matches += result
        }
        done += numbers.size
        coroutineContext.ensureActive()
        onBatch(SearchBatch(matches, done))
    }
    return unreadable
}

/** The hits of one page, or null if the page cannot be read. */
private fun searchPage(
    number: Int,
    regex: Regex,
    readLayer: (Int) -> PageLayer,
    job: Job?,
): List<Match>? {
    val layer =
        try {
            readLayer(number)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // A page file that cannot be read is skipped and counted, it must not stop the search of the rest.
            @Suppress("TooGenericExceptionCaught") ignored: Exception,
        ) {
            return null
        }
    return searchLayer(number, layer, regex, job)
}
