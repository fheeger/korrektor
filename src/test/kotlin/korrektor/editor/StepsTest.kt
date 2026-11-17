package korrektor.editor

import korrektor.format.Box
import korrektor.format.Line
import korrektor.format.LineChange
import korrektor.format.PageLayer
import korrektor.format.Region
import korrektor.format.RegionType
import korrektor.format.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StepsTest {
    private fun line(
        id: String,
        vararg words: String,
    ) = Line(
        id,
        Box(10.0, 10.0, 50.0, 10.0),
        id,
        words.mapIndexed {
            i,
            w,
            ->
            Word(w, Box(10.0 + i * 20, 10.0, 18.0, 10.0))
        },
    )

    private val l1 = line("l1", "Von", "der", "Kriegsfüh-")
    private val l2 = line("l2", "rnng", "iſt", "nuͤtzlich")
    private val l3 = line("l3", "Ende")
    private val l4 = line("l4", "Rand")

    private val layer =
        PageLayer(
            3,
            300.0,
            400.0,
            listOf(
                Region("r1", RegionType.TEXT, 1, null, listOf(l1, l2, l3)),
                Region("r2", RegionType.TEXT, 2, null, listOf(l4)),
            ),
        )
    private val empty = PageLayer(3, 300.0, 400.0, emptyList())

    /** The change of the edit applies forwards and backwards, and the inverse of the inverse is the change. */
    private fun assertRoundTrip(
        before: PageLayer,
        after: PageLayer,
    ): List<LineChange> {
        val changes = diff(3, before, after)
        assertTrue(changes.isNotEmpty(), "the edit changed nothing")
        assertEquals(after, changes.fold<LineChange, PageLayer?>(before) { l, c -> l?.let { apply(it, c) } })
        val undone =
            changes.reversed().fold<LineChange, PageLayer?>(
                after,
            ) { l, c -> l?.let { apply(it, c.inverted()) } }
        assertEquals(before, undone)
        assertEquals(changes, changes.map { it.inverted().inverted() })
        return changes
    }

    @Test
    fun typingInALineIsOneChangeOfThatLine() {
        val changes = assertRoundTrip(layer, PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich").layer)
        val change = changes.single()
        assertEquals(1, change.index)
        assertEquals(listOf(l2), change.removed)
        assertEquals(listOf("rung iſt nuͤtzlich"), change.inserted.map { it.text })
    }

    @Test
    fun aSplitReplacesOneLineByTwo() {
        val change = assertRoundTrip(layer, PageEdits.splitLine(layer, "l2", 5).layer).single()
        assertEquals(1, change.removed.size)
        assertEquals(2, change.inserted.size)
    }

    @Test
    fun aJoinReplacesTwoLinesByOne() {
        val change = assertRoundTrip(layer, PageEdits.joinWithPrevious(layer, "l2").layer).single()
        assertEquals(listOf("l1", "l2"), change.removed.map { it.id })
        assertEquals(1, change.inserted.size)
    }

    @Test
    fun aJoinAcrossTwoRegionsIsTwoChanges() {
        val changes = assertRoundTrip(layer, PageEdits.joinWithPrevious(layer, "l4").layer)
        assertEquals(listOf("r1", "r2"), changes.map { it.region })
    }

    @Test
    fun addingAndDeletingALine() {
        val added = PageEdits.addLineAfter(layer, "l1", "neu").layer
        assertEquals(0, assertRoundTrip(layer, added).single().removed.size)
        val deleted = PageEdits.deleteLine(layer, "l3").layer
        assertEquals(0, assertRoundTrip(layer, deleted).single().inserted.size)
        assertRoundTrip(layer, PageEdits.addLineAfter(layer, null, "am Ende").layer)
    }

    @Test
    fun theFirstLineOfAPageWithoutRegionsMakesTheRegionAndUndoRemovesItAgain() {
        val added = PageEdits.addLineAfter(empty, null, "erste").layer
        val change = assertRoundTrip(empty, added).single()
        assertEquals(false, change.regionBefore)
        assertEquals(empty, apply(added, change.inverted()), "no empty region is left behind")
    }

    @Test
    fun aRegionThatWasEmptiedByDeletingItsLastLineStays() {
        val one = PageLayer(3, 300.0, 400.0, listOf(Region("r1", RegionType.TEXT, 1, null, listOf(l1))))
        val deleted = PageEdits.deleteLine(one, "l1").layer
        assertEquals(1, deleted.regions.size)
        assertRoundTrip(one, deleted)
    }

    @Test
    fun equalLayersHaveNoChanges() {
        assertEquals(emptyList(), diff(3, layer, layer))
        assertEquals(emptyList(), diff(3, layer, layer.copy()))
    }

    @Test
    fun aChangeInOneRegionGivesOneChange() {
        val after = PageEdits.setLineText(layer, "l4", "Rand!").layer
        assertEquals(listOf("r2"), diff(3, layer, after).map { it.region })
    }

    @Test
    fun aChangeThatDoesNotFitIsNotApplied() {
        val change = diff(3, layer, PageEdits.setLineText(layer, "l2", "rung iſt nuͤtzlich").layer).single()
        // The line is not as the change expects.
        val other = PageEdits.setLineText(layer, "l2", "etwas anderes").layer
        assertNull(apply(other, change))
        // Out of range.
        assertNull(apply(layer, change.copy(index = 5)))
        assertNull(apply(layer, change.copy(index = 3, removed = listOf(l2, l3))))
        // A region that is not there.
        assertNull(apply(layer, change.copy(region = "r9")))
        // A region that is there but should not be.
        assertNull(apply(layer, change.copy(regionBefore = false)))
        // The page must stay valid: a line id that is there already.
        assertNull(apply(layer, LineChange(3, "r1", 0, emptyList(), listOf(l1))))
        assertNotNull(apply(layer, change))
    }

    @Test
    fun applyDoesNotChangeTheLayerItIsGiven() {
        val before = layer.copy()
        apply(layer, diff(3, layer, PageEdits.splitLine(layer, "l1", 3).layer).single())
        assertEquals(before, layer)
        assertSame(layer.regions[1], layer.regions[1])
    }

    @Test
    fun aMissingRegionIsMadeOnlyForAChangeThatMakesIt() {
        val make = LineChange(3, "r1", 0, emptyList(), listOf(l1), regionBefore = false)
        assertEquals(
            listOf("l1"),
            apply(empty, make)!!
                .regions
                .single()
                .lines
                .map { it.id },
        )
        assertNull(apply(empty, make.copy(index = 1)))
        assertNull(apply(empty, make.copy(regionBefore = true)))
    }
}
