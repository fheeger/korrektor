package korrektor.search

import korrektor.format.FormatException
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The search engine: queries, hits within a line, and the search of the whole book (T083). */
class SearchTest {
    private fun hits(
        query: SearchQuery,
        vararg lines: String,
    ) = searchLayer(1, layerOf(*lines), regexOf(query)).map { Triple(it.lineNumber, it.start, it.found) }

    @Test
    fun aPlainTextIsFoundLiterallyAndIgnoringCaseByDefault() {
        assertEquals(
            listOf(Triple(1, 0, "Der"), Triple(1, 9, "der"), Triple(2, 0, "DER")),
            hits(SearchQuery("der"), "Der Mann der", "DER"),
        )
        // The characters of a regular expression mean nothing in a plain search.
        assertEquals(listOf(Triple(1, 1, ".*")), hits(SearchQuery(".*"), "a.*b"))
    }

    @Test
    fun matchCaseOnlyFindsTheSameCase() {
        assertEquals(listOf(Triple(1, 9, "der")), hits(SearchQuery("der", matchCase = true), "Der Mann der"))
        assertEquals(listOf(Triple(1, 0, "Ä")), hits(SearchQuery("ä"), "Ä"), "also for letters beyond ASCII")
    }

    @Test
    fun wholeWordsDoNotTouchLettersDigitsOrUnderscores() {
        val query = SearchQuery("der", wholeWords = true)
        assertEquals(
            listOf(Triple(1, 0, "Der"), Triple(1, 15, "der")),
            hits(query, "Der Verderber, der"),
        )
        assertEquals(emptyList(), hits(query, "derb", "Müder", "der_x", "der2", "äder"))
        assertEquals(listOf(Triple(1, 1, "der")), hits(query, "(der)"), "a punctuation mark ends a word")
        // Whole words also work for a regex, and around an alternative.
        assertEquals(
            listOf(Triple(1, 0, "an"), Triple(1, 3, "in")),
            hits(SearchQuery("an|in", wholeWords = true, regex = true), "an in Bild ein"),
        )
    }

    @Test
    fun aRegularExpressionIsSearchedAsOne() {
        assertEquals(
            listOf(Triple(1, 0, "1785"), Triple(1, 11, "1790")),
            hits(SearchQuery("""\d{4}""", regex = true), "1785 und - 1790"),
        )
        assertEquals(listOf(Triple(1, 0, "ſ")), hits(SearchQuery("[sſ]", regex = true, matchCase = true), "ſ"))
        // A regex that starts at the line start only matches there.
        assertEquals(listOf(Triple(1, 0, "Es")), hits(SearchQuery("^Es", regex = true), "Es ist Es"))
    }

    @Test
    fun theTextIsSearchedExactlyAsStored() {
        // "s" does not find "ſ": variant spellings are not equal (D5 is not part of this story).
        assertEquals(emptyList(), hits(SearchQuery("s"), "ſo ſchön"))
        assertEquals(listOf(Triple(1, 3, "ſt")), hits(SearchQuery("ſt"), "st ſt"), "ſ is only itself")
        assertEquals(listOf(Triple(1, 3, "ſt")), hits(SearchQuery("ſt", regex = true), "st ſt"))
        assertEquals(listOf(Triple(1, 0, "ſ"), Triple(1, 3, "ſ")), hits(SearchQuery("ſ"), "ſo ſchön"))
        // The text of a line is its words joined by one space, with nothing normalized.
        assertEquals(listOf(Triple(1, 1, "  ")), hits(SearchQuery("  "), "a  b"))
    }

    @Test
    fun aHitLiesWithinOneLine() {
        // The end of one line and the start of the next do not make a hit, with a plain text or a regex.
        assertEquals(emptyList(), hits(SearchQuery("Ende Anfang"), "am Ende", "Anfang des"))
        assertEquals(emptyList(), hits(SearchQuery("Ende\\s+Anfang", regex = true), "am Ende", "Anfang des"))
        assertEquals(
            listOf(Triple(1, 0, "am Ende"), Triple(2, 0, "Anfang des")),
            hits(SearchQuery(".*", regex = true), "am Ende", "Anfang des"),
        )
    }

    @Test
    fun emptyHitsAreNoHits() {
        assertEquals(emptyList(), hits(SearchQuery("x*", regex = true), "abc", ""))
        assertEquals(listOf(Triple(1, 1, "xx")), hits(SearchQuery("x*", regex = true), "axx"))
    }

    @Test
    fun severalHitsInALineAreAllFoundAndDoNotOverlap() {
        assertEquals(
            listOf(Triple(1, 0, "aa"), Triple(1, 2, "aa")),
            hits(SearchQuery("aa"), "aaaaa"),
        )
        val hit = searchLayer(1, layerOf("ein Wort"), regexOf(SearchQuery("Wort"))).single()
        assertEquals("ein ", hit.before)
        assertEquals("", hit.after)
    }

    @Test
    fun linesAreNumberedInReadingOrderAcrossRegions() {
        fun line(
            id: String,
            text: String,
        ) = Line(id, null, null, listOf(Word(text)))
        // The region with the lower order is read first, wherever it is in the list.
        val layer =
            PageLayer(
                1,
                100.0,
                200.0,
                listOf(
                    Region("b", RegionType.TEXT, 2, null, listOf(line("b1", "hit"), line("b2", "hit"))),
                    Region("a", RegionType.TEXT, 1, null, listOf(line("a1", "hit"))),
                ),
            )
        val found = searchLayer(7, layer, regexOf(SearchQuery("hit")))
        assertEquals(listOf("a1", "b1", "b2"), found.map { it.lineId })
        assertEquals(listOf(1, 2, 3), found.map { it.lineNumber })
        assertTrue(found.all { it.page == 7 })
        assertEquals(emptyList(), searchLayer(1, PageLayer(1, 100.0, 200.0, emptyList()), regexOf(SearchQuery("x"))))
    }

    @Test
    fun invalidQueriesAreAMessageAndAnEmptyTextFindsNothing() {
        assertIs<Compiled.Empty>(SearchQuery("").compile())
        val invalid = assertIs<Compiled.Invalid>(SearchQuery("(", regex = true).compile())
        assertTrue(invalid.message.startsWith("Not a valid regular expression"), invalid.message)
        // The same text is fine as a plain search.
        assertIs<Compiled.Ready>(SearchQuery("(").compile())
    }

    @Test
    fun theReplacementOfAHit() {
        val plain = searchLayer(1, layerOf("ſo ſchön"), regexOf(SearchQuery("ſ"))).first()
        assertEquals("s$1", plain.replacement(regexOf(SearchQuery("ſ")), "s$1", literal = true).getOrThrow())

        val query = SearchQuery("(\\w+)ſ(\\w+)", regex = true)
        val regex = regexOf(query)
        val hit = searchLayer(1, layerOf("er Bruſt und"), regex).single()
        assertEquals("Brus-t", hit.replacement(regex, "$1s-$2", literal = false).getOrThrow())
        assertEquals("Bru\$t", hit.replacement(regex, "$1\\\$$2", literal = false).getOrThrow())
        val named = regexOf(SearchQuery("(?<a>ſ)", regex = true))
        val inBrackets = searchLayer(1, layerOf("ſ"), named).single().replacement(named, "[\${a}]", false)
        assertEquals("[ſ]", inBrackets.getOrThrow())

        val noGroup = hit.replacement(regex, "$9", literal = false)
        assertTrue(noGroup.isFailure)
        assertTrue(
            checkNotNull(noGroup.exceptionOrNull()).replacementProblem().startsWith("The replacement is not valid"),
        )
        assertTrue(hit.replacement(regex, "\\", literal = false).isFailure)
    }

    @Test
    fun aHitWhoseLineChangedHasNoReplacement() {
        val regex = regexOf(SearchQuery("a(b)", regex = true))
        val hit = Match(1, "l1", 1, "xx ab", 3, 5) // the line the search saw
        assertEquals("b", hit.replacement(regex, "$1", literal = false).getOrThrow())
        assertTrue(runCatching { hit.copy(lineText = "ab") }.isFailure, "a place outside the line is refused")
        assertTrue(hit.copy(start = 0, end = 2).replacement(regex, "$1", literal = false).isFailure)
    }

    // --- the whole book ---

    private val book: (Int) -> PageLayer =
        { n -> layerOf("Seite $n", if (n % 2 == 0) "gerade" else "ungerade", number = n) }

    @Test
    fun theBookIsSearchedInBatchesInPageOrder() =
        runBlocking {
            val batches = ArrayList<SearchBatch>()
            val unreadable =
                searchBook(1..10, regexOf(SearchQuery("gerade")), SearchSource(Dispatchers.IO, book, 4)) {
                    batches +=
                        it
                }
            assertEquals(emptyList(), unreadable)
            assertEquals(listOf(4, 8, 10), batches.map { it.pagesDone })
            val pages = batches.flatMap { b -> b.matches.map { it.page } }
            assertEquals((1..10).toList(), pages, "every page has a line with 'gerade', in order")
            assertEquals(listOf(4, 4, 2), batches.map { it.matches.size })
        }

    @Test
    fun anUnreadablePageIsCountedAndTheOthersAreSearched() =
        runBlocking {
            val read: (Int) -> PageLayer = { n ->
                if (n == 3) throw FormatException("broken") else book(n)
            }
            val found = ArrayList<Match>()
            val query = regexOf(SearchQuery("Seite"))
            val unreadable =
                searchBook(1..5, query, SearchSource(Dispatchers.IO, read, chunk = 2)) {
                    found +=
                        it.matches
                }
            assertEquals(listOf(3), unreadable)
            assertEquals(listOf(1, 2, 4, 5), found.map { it.page })
        }

    @Test
    fun cancellingStopsTheSearchAndNothingMoreIsReported() =
        runBlocking {
            val reported = java.util.Collections.synchronizedList(ArrayList<Int>())
            val firstBatch = CompletableDeferred<Unit>()
            val job =
                async(Dispatchers.Default, CoroutineStart.DEFAULT) {
                    val slow = { n: Int ->
                        Thread.sleep(5)
                        book(n)
                    }
                    searchBook(1..1000, regexOf(SearchQuery("Seite")), SearchSource(Dispatchers.IO, slow, chunk = 5)) {
                        reported += it.pagesDone
                        firstBatch.complete(Unit)
                    }
                }
            firstBatch.await()
            job.cancel()
            val started = System.nanoTime()
            assertFailsWith<CancellationException> { job.await() }
            val seen = reported.size
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "it stops promptly")
            Thread.sleep(100)
            assertEquals(seen, reported.size, "nothing is reported after the cancel")
            assertTrue(reported.last() < 1000, "the book was not finished")
        }

    @Test
    fun aRegexThatBacktracksForeverCanStillBeCancelled() =
        runBlocking {
            val started = CountDownLatch(1)
            val finished = AtomicBoolean(false)
            // (a+)+b on a line of a's: exponential, the engine would not return for hours.
            val regex = regexOf(SearchQuery("(a+)+b", regex = true))
            val layer = layerOf("a".repeat(60), number = 1)
            val job =
                async(Dispatchers.Default) {
                    try {
                        val read = { _: Int ->
                            started.countDown()
                            layer
                        }
                        searchBook(1..1, regex, SearchSource(Dispatchers.IO, read)) {}
                    } finally {
                        finished.set(true)
                    }
                }
            started.await()
            Thread.sleep(200) // inside the regex by now
            job.cancel()
            withTimeout(5_000) { runCatching { job.await() } }
            assertTrue(finished.get(), "the search ended")
        }
}
