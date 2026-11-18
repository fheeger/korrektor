package korrektor.search

import korrektor.editor.BookEditor
import korrektor.format.PageLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the review of S10 found (T090). */
class SearchReviewFixesTest {
    private fun original(n: Int): PageLayer = layerOf("aa x aa", "Seite $n", number = n)

    private fun searched(
        editor: BookEditor,
        query: SearchQuery,
        pages: Int = 6,
    ): SearchModel {
        val model = SearchModel(editor, pages, Dispatchers.Unconfined, Dispatchers.IO, pause = 0, chunk = 4)
        model.change(query)
        runBlocking { withTimeout(10_000) { while (model.searching) delay(2) } }
        return model
    }

    @Test
    fun aPageScopeIsNotSearchedAgainAfterItsOwnReplacement() {
        val editor = BookEditor(::original, Dispatchers.Unconfined)
        runBlocking { editor.show(2) }
        val model = searched(editor, SearchQuery("aa"))
        model.show(replace = false)
        model.changeScope(SearchScope.PAGE)
        runBlocking { withTimeout(10_000) { while (model.searching) delay(2) } }
        assertEquals(2, model.results.size)
        model.replacement = "b"
        model.current = 0
        runBlocking { model.replaceCurrent() }
        model.pageChanged() // what the panel does when the layer of the page changed
        runBlocking { delay(100) }
        assertEquals(1, model.results.size, "the results were not thrown away")
        assertEquals(0, model.current, "the next hit is still the current one")
        assertTrue(checkNotNull(model.outcome).startsWith("Replaced 1 match"))
    }

    @Test
    fun aNewQueryWhileReplacingLeavesTheNewResultsAlone() {
        val block = AtomicBoolean(false)
        val reading = CountDownLatch(1)
        val go = CountDownLatch(1)
        val read = { n: Int ->
            if (n == 3 && block.compareAndSet(true, false)) {
                reading.countDown()
                go.await(10, TimeUnit.SECONDS)
            }
            original(n)
        }
        val editor = BookEditor(read, Dispatchers.Unconfined)
        val model = searched(editor, SearchQuery("aa"))
        model.replacement = "bbbb"
        block.set(true)
        runBlocking {
            model.selectAll()
            val replacing = async(Dispatchers.Default) { model.replaceSelected() }
            assertTrue(reading.await(10, TimeUnit.SECONDS))
            model.change(SearchQuery("Seite")) // the user types a new query meanwhile
            go.countDown()
            replacing.await()
            withTimeout(10_000) { while (model.searching) delay(2) }
        }
        assertEquals(6, model.results.size)
        assertTrue(model.results.all { it.found == "Seite" && it.lineText.startsWith("Seite") })
    }

    @Test
    fun replacingTheLastHitGoesOnWithTheFirstOne() {
        val editor = BookEditor(::original, Dispatchers.Unconfined)
        val model = searched(editor, SearchQuery("Seite"), 3)
        model.replacement = "Blatt"
        model.current = 2
        runBlocking { model.replaceCurrent() }
        assertEquals(0, model.current)
        assertEquals(2, model.results.size)
    }

    @Test
    fun aCombiningMarkBelongsToItsLetterForWholeWords() {
        val regex = regexOf(SearchQuery("zu", wholeWords = true))
        assertEquals(emptyList(), searchLayer(1, layerOf("zuͤ Haus"), regex))
        assertEquals(1, searchLayer(1, layerOf("zu Haus"), regex).size)
    }
}
