package korrektor.search

import korrektor.editor.BookEditor
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a result of the list shows and what the line under the search field says (T085). */
class SearchSnippetTest {
    private fun match(
        text: String,
        start: Int,
        end: Int,
    ) = Match(1, "l1", 1, text, start, end)

    @Test
    fun aShortLineIsShownWhole() {
        assertEquals(Snippet("ein ", "Wort", " und mehr"), match("ein Wort und mehr", 4, 8).snippet())
    }

    @Test
    fun aLongLineIsCutOnBothSidesWithADotsMark() {
        val before = "a".repeat(100)
        val after = "b".repeat(100)
        val snippet = match("${before}X$after", 100, 101).snippet("Y")
        assertEquals("…" + "a".repeat(30), snippet.before)
        assertEquals("X", snippet.found)
        assertEquals("b".repeat(40) + "…", snippet.after)
        assertEquals("Y", snippet.replacement)
    }

    @Test
    fun aHitJustWithinTheLimitsIsNotCut() {
        val snippet = match("a".repeat(30) + "X" + "b".repeat(40), 30, 31).snippet()
        assertEquals("a".repeat(30), snippet.before)
        assertEquals("b".repeat(40), snippet.after)
    }

    @Test
    fun theSummaryCountsHitsAndPagesAndSaysWhatIsGoingOn() {
        // A long pause: the search itself never starts, the test sets the state it wants to read.
        val editor = BookEditor({ error("no page") }, Dispatchers.Unconfined)
        val model = SearchModel(editor, 10, Dispatchers.Unconfined, Dispatchers.IO, pause = 60_000)
        assertEquals("", searchSummary(model), "nothing is searched for")
        model.change(SearchQuery("x"))
        model.cancel()
        assertEquals("No matches", searchSummary(model))
        model.results.add(match("x", 0, 1))
        model.pagesWithHits = 1
        assertEquals("1 match on 1 page", searchSummary(model))
        model.results.add(match("xx", 0, 1))
        model.pagesWithHits = 2
        assertEquals("2 matches on 2 pages", searchSummary(model))
        model.searching = true
        model.pagesDone = 3
        assertEquals("Searching… 3 of 10 pages · 2 matches", searchSummary(model))
        model.close()
    }
}
