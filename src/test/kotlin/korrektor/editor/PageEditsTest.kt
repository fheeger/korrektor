package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageCodec
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageEditsTest {
    private fun word(
        text: String,
        x: Double,
        y: Double = 10.0,
    ) = Word(text, Box(x, y, 20.0, 10.0))

    private val l1 =
        Line(
            "l1",
            Box(10.0, 10.0, 130.0, 10.0),
            "l1",
            listOf(word("Von", 10.0), word("der", 40.0), word("Kriegsfüh-", 70.0)),
        )
    private val l2 =
        Line(
            "l2",
            Box(10.0, 24.0, 90.0, 10.0),
            "l2",
            listOf(word("rnng", 10.0, 24.0), word("iſt", 40.0, 24.0), word("nuͤtzlich", 70.0, 24.0)),
        )
    private val l3 = Line("l3", Box(10.0, 38.0, 20.0, 10.0), "l3", listOf(word("Ende", 10.0, 38.0)))
    private val layer =
        PageLayer(
            3,
            300.0,
            400.0,
            listOf(Region("r1", RegionType.TEXT, 1, Box(10.0, 10.0, 130.0, 40.0), listOf(l1, l2, l3))),
        )

    private fun PageLayer.lines() = regions.flatMap { it.lines }

    private fun PageLayer.line(id: String) = lines().first { it.id == id }

    private fun PageLayer.texts() = lines().map { it.text }

    private fun assertValid(layer: PageLayer) {
        // The constructor checked the page; the file round trip checks that it can be stored as it is.
        assertEquals(layer, PageCodec.decode(PageCodec.encode(layer)))
    }

    // --- setLineText ------------------------------------------------------------------------------------------

    @Test
    fun theSameTextChangesNothing() {
        val result = PageEdits.setLineText(layer, "l2", "rnng iſt nuͤtzlich")
        assertSame(layer, result.layer)
        assertNull(result.focus)
    }

    @Test
    fun changingTheTextChangesOnlyThatLineAndNeverTheOldLayer() {
        val before = layer
        val result = PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich").layer

        assertEquals(listOf("Von der Kriegsfüh-", "rung iſt nuͤtzlich", "Ende"), result.texts())
        assertEquals(l1, result.line("l1"))
        assertEquals(l3, result.line("l3"))
        assertEquals(before, layer, "the old layer is the same as before")
        assertValid(result)
    }

    @Test
    fun withTheSameNumberOfWordsEveryWordKeepsItsBox() {
        val line = PageEdits.setLineText(layer, "l2", "rung iſt nützlich").layer.line("l2")

        assertEquals(listOf("rung", "iſt", "nützlich"), line.words.map { it.text })
        assertEquals(l2.words.map { it.box }, line.words.map { it.box })
        assertEquals(l2.box, line.box)
        assertEquals("l2", line.origin)
    }

    @Test
    fun withAnotherNumberOfWordsTheWordsHaveNoBoxesButTheLineKeepsItsBox() {
        val line = PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich und").layer.line("l2")

        assertEquals(4, line.words.size)
        assertTrue(line.words.all { it.box == null })
        assertEquals(l2.box, line.box)
        assertEquals("l2", line.id)
        assertEquals("l2", line.origin)
    }

    @Test
    fun theTextIsKeptExactlyAsTyped() {
        val texts =
            listOf(
                "two  spaces",
                " leading",
                "trailing ",
                "ſtraße ꝛc. uͤber",
                "Haupt=",
                "Kriegsfüh-",
                " ",
                "",
                "a    b",
            )
        for (text in texts) {
            val result = PageEdits.setLineText(layer, "l2", text).layer
            assertEquals(text, result.line("l2").text, "'$text'")
            assertValid(result)
        }
    }

    @Test
    fun anEmptyTextHasNoWords() {
        assertEquals(
            emptyList(),
            PageEdits
                .setLineText(layer, "l2", "")
                .layer
                .line("l2")
                .words,
        )
    }

    // --- splitLine --------------------------------------------------------------------------------------------

    @Test
    fun splittingBetweenTwoWordsDropsTheSpaceAndKeepsAllBoxes() {
        for (index in listOf(7, 8)) { // before and after the space
            val result = PageEdits.splitLine(layer, "l1", index)

            assertEquals(
                listOf("Von der", "Kriegsfüh-", "rnng iſt nuͤtzlich", "Ende"),
                result.layer.texts(),
                "at $index",
            )
            val first = result.layer.line("l1")
            val second = result.layer.line("l4")
            assertEquals(Box(10.0, 10.0, 50.0, 10.0), first.box, "the union of Von and der")
            assertEquals(Box(70.0, 10.0, 20.0, 10.0), second.box)
            assertEquals("l1", first.origin)
            assertNull(second.origin)
            assertEquals(Focus("l4", 0), result.focus)
            assertValid(result.layer)
        }
    }

    @Test
    fun splittingInsideAWordCutsItAndTheWordLosesItsBox() {
        val result = PageEdits.splitLine(layer, "l1", 10) // Von der Kr|iegsfüh-

        assertEquals(listOf("Von der Kr", "iegsfüh-"), result.layer.texts().take(2))
        assertNull(
            result.layer
                .line("l1")
                .words[2]
                .box,
            "Kr",
        )
        assertNull(
            result.layer
                .line("l4")
                .words[0]
                .box,
            "iegsfüh-",
        )
        assertEquals(
            Box(10.0, 10.0, 50.0, 10.0),
            result.layer.line("l1").box,
            "the union of Von and der, the words it has",
        )
        assertNull(result.layer.line("l4").box)
        assertValid(result.layer)
    }

    @Test
    fun splittingAtTheStartPushesTheTextDownAndAtTheEndAddsAnEmptyLine() {
        val atStart = PageEdits.splitLine(layer, "l3", 0).layer
        assertEquals(listOf("", "Ende"), atStart.texts().takeLast(2))
        assertNull(atStart.line("l3").box, "an empty first part has no place on the scan")
        assertEquals(l3.box, atStart.line("l4").box)

        val atEnd = PageEdits.splitLine(layer, "l3", 4)
        assertEquals(listOf("Ende", ""), atEnd.layer.texts().takeLast(2))
        assertEquals(l3.box, atEnd.layer.line("l3").box)
        assertNull(atEnd.layer.line("l4").box)
        assertEquals(Focus("l4", 0), atEnd.focus)
        assertValid(atStart)
        assertValid(atEnd.layer)
    }

    @Test
    fun aLineWithoutWordBoxesSplitsWithTheOldBoxOnTheFirstPartOnly() {
        val edited = PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich und").layer

        val result = PageEdits.splitLine(edited, "l2", 8).layer

        assertEquals(l2.box, result.line("l2").box)
        assertNull(result.line("l4").box)
    }

    @Test
    fun splittingAtEveryPlaceKeepsAllTheTextAndOnlyOneSpaceIsLost() {
        val text = l1.text
        for (index in 0..text.length) {
            val result = PageEdits.splitLine(layer, "l1", index).layer
            val joined = result.line("l1").text + result.line("l4").text
            assertTrue(
                joined == text || joined == text.replaceFirst(" ", "") || joined.length == text.length - 1,
                "at $index: $joined",
            )
            assertValid(result)
        }
    }

    @Test
    fun theCutMustBeInsideTheText() {
        assertFailsWith<IllegalArgumentException> { PageEdits.splitLine(layer, "l1", -1) }
        assertFailsWith<IllegalArgumentException> { PageEdits.splitLine(layer, "l1", l1.text.length + 1) }
    }

    // --- joinWithPrevious and joinWithNext --------------------------------------------------------------------

    @Test
    fun joiningAppendsWithoutASeparatorAndTakesTheUnionOfTheBoxes() {
        val result = PageEdits.joinWithPrevious(layer, "l2")

        assertEquals(listOf("Von der Kriegsfüh-rnng iſt nuͤtzlich", "Ende"), result.layer.texts())
        val merged = result.layer.line("l1")
        assertEquals(Box(10.0, 10.0, 130.0, 24.0), merged.box)
        assertEquals("l1", merged.origin)
        assertEquals(listOf("Von", "der", "Kriegsfüh-rnng", "iſt", "nuͤtzlich"), merged.words.map { it.text })
        assertEquals(Box(10.0, 10.0, 80.0, 24.0), merged.words[2].box, "the union of the two parts of the word")
        assertEquals(Focus("l1", "Von der Kriegsfüh-".length), result.focus)
        assertValid(result.layer)
    }

    @Test
    fun joiningTheFirstLineOfThePageDoesNothing() {
        val result = PageEdits.joinWithPrevious(layer, "l1")
        assertSame(layer, result.layer)
        assertNull(result.focus)
    }

    @Test
    fun joinWithNextIsTheSameFromTheOtherSide() {
        val result = PageEdits.joinWithNext(layer, "l1")
        assertEquals(PageEdits.joinWithPrevious(layer, "l2"), result)
        assertSame(layer, PageEdits.joinWithNext(layer, "l3").layer, "the last line has no next")
    }

    @Test
    fun joiningWithAnEmptyLineOrAWordlessSideKeepsTheOtherWords() {
        val withEmpty = PageEdits.addLineAfter(layer, "l1").layer // l4, empty, after l1
        val result = PageEdits.joinWithPrevious(withEmpty, "l4").layer
        assertEquals(l1.words, result.line("l1").words)

        val atStart = PageEdits.splitLine(layer, "l3", 0).layer // l3 is empty now, l4 has "Ende"
        val joined = PageEdits.joinWithPrevious(atStart, "l4").layer
        assertEquals("Ende", joined.line("l3").text)
        assertEquals(l3.box, joined.line("l3").box, "the box of the part that has one")
    }

    @Test
    fun joiningAfterASpaceDoesNotMakeAnEmptyWordBoxless() {
        val edited = PageEdits.setLineText(layer, "l1", "Von der ").layer // a trailing space: an empty word
        val joined = PageEdits.joinWithPrevious(edited, "l2").layer.line("l1")
        assertEquals("Von der rnng iſt nuͤtzlich", joined.text)
        assertEquals(l2.words[0].box, joined.words[2].box, "the word that follows the space keeps its box")
    }

    @Test
    fun aLineJoinsTheLastLineOfThePreviousRegion() {
        val two =
            PageLayer(
                3,
                300.0,
                400.0,
                listOf(
                    Region("r2", RegionType.TEXT, 2, null, listOf(Line("l3", null, null, listOf(Word("zwei"))))),
                    Region(
                        "r1",
                        RegionType.TEXT,
                        1,
                        null,
                        listOf(
                            Line("l1", null, null, listOf(Word("eins"))),
                            Line("l2", null, null, listOf(Word("mid"))),
                        ),
                    ),
                ),
            )

        val result = PageEdits.joinWithPrevious(two, "l3")

        assertEquals(
            "midzwei",
            result.layer.regions
                .first { it.id == "r1" }
                .lines
                .last()
                .text,
        )
        assertEquals(
            emptyList(),
            result.layer.regions
                .first { it.id == "r2" }
                .lines,
        )
        assertValid(result.layer)
    }

    // --- addLineAfter -----------------------------------------------------------------------------------------

    @Test
    fun anAddedLineHasNoBoxAndNoOriginAndANewId() {
        val result = PageEdits.addLineAfter(layer, "l2", "Anmerkung")

        assertEquals(listOf("l1", "l2", "l4", "l3"), result.layer.lines().map { it.id })
        val added = result.layer.line("l4")
        assertEquals("Anmerkung", added.text)
        assertNull(added.box)
        assertNull(added.origin)
        assertEquals(Focus("l4", 0), result.focus)
        assertValid(result.layer)
    }

    @Test
    fun aPageWithoutRegionsGetsRegionR1() {
        val empty = PageLayer(1, 100.0, 100.0, emptyList(), note = "No text on this page.")

        val result = PageEdits.addLineAfter(empty, null)

        val region = result.layer.regions.single()
        assertEquals("r1", region.id)
        assertEquals(RegionType.TEXT, region.type)
        assertEquals(1, region.order)
        assertEquals(listOf("l1"), region.lines.map { it.id })
        assertEquals("No text on this page.", result.layer.note, "the note stays")
        assertValid(result.layer)
    }

    @Test
    fun withoutALineIdTheLineGoesToTheEndOfTheLastRegion() {
        val result = PageEdits.addLineAfter(layer, null, "Schluss").layer
        assertEquals("Schluss", result.lines().last().text)
    }

    @Test
    fun newIdsNeverCollideWithExistingOnes() {
        val odd =
            layer.copy(
                regions =
                    listOf(
                        layer.regions[0].copy(
                            lines = listOf(l1.copy(id = "l7"), l2.copy(id = "x"), l3.copy(id = "l12")),
                        ),
                    ),
            )
        assertEquals("l13", PageEdits.addLineAfter(odd, "l7").focus!!.lineId)
        val none =
            PageLayer(
                1,
                10.0,
                10.0,
                listOf(Region("r1", RegionType.TEXT, 1, null, listOf(Line("a", null, null, emptyList())))),
            )
        assertEquals("l1", PageEdits.addLineAfter(none, "a").focus!!.lineId)
    }

    // --- deleteLine -------------------------------------------------------------------------------------------

    @Test
    fun deletingALineRemovesItAndPutsTheCursorAtTheEndOfThePreviousOne() {
        val result = PageEdits.deleteLine(layer, "l2")

        assertEquals(listOf("l1", "l3"), result.layer.lines().map { it.id })
        assertEquals(Focus("l1", l1.text.length), result.focus)
        assertValid(result.layer)
    }

    @Test
    fun deletingTheFirstLineMovesTheCursorToTheNextOne() {
        assertEquals(Focus("l2", 0), PageEdits.deleteLine(layer, "l1").focus)
    }

    @Test
    fun deletingTheOnlyLineLeavesTheRegionWithoutLines() {
        val one = PageEdits.deleteLine(PageEdits.deleteLine(layer, "l1").layer, "l2").layer
        val result = PageEdits.deleteLine(one, "l3")

        assertEquals(
            emptyList(),
            result.layer.regions
                .single()
                .lines,
        )
        assertNull(result.focus)
        assertValid(result.layer)
    }

    @Test
    fun anUnknownLineIsAnError() {
        assertFailsWith<IllegalArgumentException> { PageEdits.setLineText(layer, "l9", "x") }
        assertFailsWith<IllegalArgumentException> { PageEdits.deleteLine(layer, "l9") }
        assertFailsWith<IllegalArgumentException> { PageEdits.joinWithPrevious(layer, "l9") }
    }

    // --- any sequence ----------------------------------------------------------------------------------------

    @Test
    fun anySequenceOfEditsKeepsTheLayerValidAndStorable() {
        val random = Random(2026)
        var current = layer
        repeat(400) {
            val lines = PageEdits.readingOrder(current)
            val id = lines.randomOrNull(random)?.id
            current =
                when {
                    id == null -> PageEdits.addLineAfter(current, null, "neu").layer
                    else ->
                        when (random.nextInt(6)) {
                            0 ->
                                PageEdits
                                    .setLineText(
                                        current,
                                        id,
                                        listOf("ſo viel", "a  b", "", "ꝛc.", "Haupt=", "x").random(random),
                                    ).layer
                            1 ->
                                PageEdits
                                    .splitLine(
                                        current,
                                        id,
                                        random.nextInt(0, current.line(id).text.length + 1),
                                    ).layer
                            2 -> PageEdits.joinWithPrevious(current, id).layer
                            3 -> PageEdits.joinWithNext(current, id).layer
                            4 -> PageEdits.addLineAfter(current, id, "zu").layer
                            else -> PageEdits.deleteLine(current, id).layer
                        }
                }
            assertValid(current)
            val ids = current.lines().map { it.id }
            assertEquals(ids.size, ids.toSet().size, "ids stay unique")
        }
    }

    // --- found in the review of S05 -----------------------------------------------------------------------------

    @Test
    fun enterAtTheStartOfALineWithoutWordBoxesGivesTheTextTheLinesBox() {
        val edited = PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich und").layer // the words have no boxes now

        val result = PageEdits.splitLine(edited, "l2", 0).layer

        assertNull(result.line("l2").box, "the empty first part has no place")
        assertEquals(l2.box, result.line("l4").box, "the text keeps the line's place on the scan")
    }

    @Test
    fun joiningByMistakeAndSplittingAgainKeepsBothLinesOnTheirOwnBoxes() {
        val joined = PageEdits.joinWithPrevious(layer, "l2").layer
        val junction = "Von der Kriegsfüh-".length

        val result = PageEdits.splitLine(joined, "l1", junction).layer

        assertEquals(listOf("Von der Kriegsfüh-", "rnng iſt nuͤtzlich"), result.texts().take(2))
        val first = result.line("l1").box!!
        val second = result.line("l4").box
        assertEquals(10.0 to 10.0, first.x to first.y)
        assertEquals(10.0, first.height, 1e-9, "line 1 is one line tall again, not two")
        assertEquals(24.0, second!!.y, 1e-9, "line 2 is on its own place and can be clicked")
    }
}
