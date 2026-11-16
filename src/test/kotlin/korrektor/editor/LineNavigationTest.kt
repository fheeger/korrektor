package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The moves of the cursor between lines and pages in the editor model (T050). */
class LineNavigationTest {
    private fun layer(
        number: Int,
        vararg texts: String,
    ) = PageLayer(
        number,
        300.0,
        200.0,
        if (texts.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Region(
                    "r1",
                    RegionType.TEXT,
                    1,
                    null,
                    texts.mapIndexed { i, t ->
                        Line("l${i + 1}", Box(10.0, 10.0 + 14.0 * i, 130.0, 10.0), "l${i + 1}", listOf(Word(t)))
                    },
                ),
            )
        },
    )

    private val pages =
        mapOf(1 to layer(1, "Eins", "Zwei", "Drei"), 2 to layer(2), 3 to layer(3, "Vier", "Fünf"))

    private fun editor(shown: Int = 1) =
        BookEditor({ pages.getValue(it) }, Dispatchers.Unconfined).also { runBlocking { it.show(shown) } }

    private fun BookEditor.wanted() = focusRequest?.focus

    @Test
    fun downAndUpMoveTheCursorByOneLine() {
        val editor = editor()
        editor.activate("l1")
        assertEquals(LineMove.MOVED, editor.moveLine(1))
        assertEquals("l2", editor.activeLineId)
        assertEquals("l2", editor.wanted()?.lineId)
        assertEquals(LineMove.MOVED, editor.moveLine(1))
        assertEquals("l3", editor.activeLineId)
        assertEquals(LineMove.MOVED, editor.moveLine(-1))
        assertEquals("l2", editor.activeLineId)
    }

    @Test
    fun theColumnIsKept() {
        val editor = editor()
        editor.activate("l1")
        editor.caret = 3
        editor.moveLine(1)
        assertEquals(3, editor.wanted()?.index)
    }

    @Test
    fun firstAndLastLineGoThere() {
        val editor = editor()
        editor.activate("l2")
        editor.lastLine()
        assertEquals("l3", editor.activeLineId)
        editor.firstLine()
        assertEquals("l1", editor.activeLineId)
        assertEquals("l1", editor.wanted()?.lineId)
    }

    @Test
    fun pastTheLastOrFirstLineAnotherPageIsAsFor() {
        val editor = editor()
        editor.activate("l3")
        assertEquals(LineMove.NEXT_PAGE, editor.moveLine(1))
        assertEquals("l3", editor.activeLineId, "the cursor stays until the page changes")
        editor.activate("l1")
        assertEquals(LineMove.PREVIOUS_PAGE, editor.moveLine(-1))
    }

    @Test
    fun withNoActiveLineDownGoesToTheFirstLineAndUpToTheLast() {
        val editor = editor()
        assertNull(editor.activeLineId)
        editor.moveLine(1)
        assertEquals("l1", editor.activeLineId)
        val other = editor()
        other.moveLine(-1)
        assertEquals("l3", other.activeLineId)
    }

    @Test
    fun aPageWithoutLinesHasNothingToMoveTo() {
        val editor = editor(2)
        assertEquals(LineMove.NOTHING, editor.moveLine(1))
        assertEquals(LineMove.NOTHING, editor.moveLine(-1))
        editor.firstLine()
        editor.lastLine()
        assertNull(editor.activeLineId)
        assertNull(editor.focusRequest)
    }

    @Test
    fun arrivingPutsTheCursorOnTheFirstOrLastLineOnceThePageIsShown() {
        val editor = editor()
        editor.arriveAt(3, Arrival.FIRST)
        runBlocking { editor.show(3) }
        assertEquals("l1", editor.activeLineId)
        assertEquals("l1", editor.wanted()?.lineId)
        editor.arriveAt(1, Arrival.LAST)
        runBlocking { editor.show(1) }
        assertEquals("l3", editor.activeLineId)
    }

    @Test
    fun anArrivalBelongsToItsPageAndIsUsedOnce() {
        val editor = editor()
        editor.arriveAt(3, Arrival.FIRST)
        runBlocking { editor.show(3) }
        assertEquals("l1", editor.activeLineId)
        runBlocking { editor.show(1) }
        runBlocking { editor.show(3) }
        assertNull(editor.activeLineId, "used up")
    }

    @Test
    fun anArrivalForAPageThatWasNeverShownIsDropped() {
        // Ctrl+Down to page 3, and before it is read the user goes to page 2 with the mouse; later page 3 by the mouse.
        val editor = editor()
        editor.arriveAt(3, Arrival.FIRST)
        runBlocking { editor.show(2) }
        assertNull(editor.activeLineId)
        runBlocking { editor.show(3) }
        assertNull(editor.activeLineId, "the cursor does not jump into a page that was opened with the mouse")
    }

    @Test
    fun theColumnIsKeptOverAShortLine() {
        val pages = mapOf(1 to layer(1, "abcdefghij", "ab", "abcdefghij"))
        val editor = BookEditor({ pages.getValue(it) }, Dispatchers.Unconfined)
        runBlocking { editor.show(1) }
        editor.requestFocus("l1", 8)
        editor.moveLine(1)
        assertEquals("l2", editor.activeLineId)
        assertEquals(8, editor.caret, "the short line did not take the column away")
        editor.moveLine(1)
        assertEquals("l3", editor.activeLineId)
        assertEquals(8, editor.wanted()?.index)
        editor.requestFocus("l2", 1) // a click or a split sets a new column
        assertEquals(1, editor.caret)
    }

    @Test
    fun arrivingOnAPageWithoutLinesPutsTheCursorNowhere() {
        val editor = editor()
        editor.arriveAt(2, Arrival.FIRST)
        runBlocking { editor.show(2) }
        assertNull(editor.activeLineId)
        assertNull(editor.wanted())
    }

    @Test
    fun arrivingOnAPageThatWasChangedAndKeptInMemoryWorksToo() {
        val editor = editor(3)
        editor.setLineText("l2", "Fünf!")
        runBlocking { editor.show(1) }
        editor.arriveAt(3, Arrival.LAST)
        runBlocking { editor.show(3) }
        assertEquals("l2", editor.activeLineId)
    }
}
