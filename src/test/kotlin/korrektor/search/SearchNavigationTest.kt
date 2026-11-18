package korrektor.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which hit comes next or before (T086). */
class SearchNavigationTest {
    private fun hit(
        page: Int,
        line: Int,
        start: Int = 0,
    ) = Match(page, "l$line", line, "xxxxxxxx", start, start + 1)

    // Pages 2, 2 and 5; the second on page 2 is in line 3.
    private val results = listOf(hit(2, 1), hit(2, 3), hit(5, 2))

    private val atStart = Place(1, 0, 0)

    @Test
    fun withNoResultsThereIsNowhereToGo() {
        assertNull(stepIndex(emptyList(), -1, forward = true, cursor = atStart))
        assertNull(stepIndex(emptyList(), 0, forward = false, cursor = atStart))
    }

    @Test
    fun fromTheCurrentHitItGoesOneOnAndWrapsAtBothEnds() {
        assertEquals(1, stepIndex(results, 0, forward = true, cursor = atStart))
        assertEquals(2, stepIndex(results, 1, forward = true, cursor = atStart))
        assertEquals(0, stepIndex(results, 2, forward = true, cursor = atStart), "after the last, the first")
        assertEquals(1, stepIndex(results, 2, forward = false, cursor = atStart))
        assertEquals(2, stepIndex(results, 0, forward = false, cursor = atStart), "before the first, the last")
    }

    @Test
    fun withoutACurrentHitItTakesTheOneAfterOrBeforeTheCursor() {
        assertEquals(0, stepIndex(results, -1, forward = true, cursor = Place(1, 5, 0)), "from page 1: the first")
        assertEquals(
            1,
            stepIndex(results, -1, forward = true, cursor = Place(2, 1, 0)),
            "the hit at the cursor is passed",
        )
        assertEquals(1, stepIndex(results, -1, forward = true, cursor = Place(2, 2, 7)))
        assertEquals(0, stepIndex(results, -1, forward = true, cursor = Place(5, 2, 0)), "after the last: wraps")
        assertEquals(1, stepIndex(results, -1, forward = false, cursor = Place(5, 2, 0)))
        assertEquals(2, stepIndex(results, -1, forward = false, cursor = Place(2, 1, 0)), "before the first: wraps")
        assertEquals(2, stepIndex(results, -1, forward = false, cursor = Place(9, 1, 0)))
    }

    @Test
    fun placesAreOrderedAsTheyAreRead() {
        assertEquals(
            listOf(Place(1, 9, 9), Place(2, 1, 0), Place(2, 1, 4), Place(2, 2, 0)),
            listOf(Place(2, 2, 0), Place(1, 9, 9), Place(2, 1, 4), Place(2, 1, 0)).sorted(),
        )
    }

    @Test
    fun aCurrentHitOutsideTheListIsTreatedAsNone() {
        assertEquals(0, stepIndex(results, 7, forward = true, cursor = atStart))
    }
}
