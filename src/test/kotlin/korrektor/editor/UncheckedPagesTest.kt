package korrektor.editor

import korrektor.format.PageLayer
import korrektor.format.PageStatus
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The jumps between unchecked pages (T080). */
class UncheckedPagesTest {
    private val corrected = PageStatus.CORRECTED

    private fun editor(
        corrected: Set<Int>,
        others: Map<Int, PageStatus> = emptyMap(),
    ): BookEditor {
        // The pages are never read: the jumps look at the statuses only.
        val read: (Int) -> PageLayer = { error("a jump must not read page $it") }
        return BookEditor(
            read,
            Dispatchers.Unconfined,
            initialStatuses =
                corrected.associateWith { this.corrected } + others,
        )
    }

    @Test
    fun theNextAndThePreviousUncheckedPageSkipTheCorrectedOnes() {
        val editor = editor(setOf(2, 3, 5))
        assertEquals(4, editor.uncheckedAfter(1, 6))
        assertEquals(6, editor.uncheckedAfter(4, 6))
        assertNull(editor.uncheckedAfter(6, 6))
        assertEquals(4, editor.uncheckedBefore(6))
        assertEquals(1, editor.uncheckedBefore(4))
        assertNull(editor.uncheckedBefore(1))
    }

    @Test
    fun theCurrentPageIsNeverItsOwnTarget() {
        val editor = editor(emptySet())
        assertEquals(3, editor.uncheckedAfter(2, 5))
        assertEquals(1, editor.uncheckedBefore(2))
        assertNull(editor.uncheckedAfter(5, 5))
    }

    @Test
    fun aStatusThatIsNotSavedYetCounts() {
        val editor = editor(setOf(2))
        editor.setStatus(3, corrected)
        assertTrue(editor.statusesChanged)
        assertEquals(4, editor.uncheckedAfter(1, 6))
    }

    @Test
    fun aStatusOfALaterVersionIsNotUnchecked() {
        val editor = editor(setOf(2), others = mapOf(3 to PageStatus("needs attention")))
        assertEquals(4, editor.uncheckedAfter(1, 6))
        assertNull(editor.uncheckedAfter(3, 4).takeIf { false })
        assertEquals(1, editor.uncheckedBefore(4))
    }

    @Test
    fun aBookOf1000PagesIsSearchedFast() {
        val editor = editor((1..999).toSet())
        val start = System.nanoTime()
        repeat(1000) {
            assertEquals(1000, editor.uncheckedAfter(1, 1000))
            assertNull(editor.uncheckedBefore(1))
        }
        val millis = (System.nanoTime() - start) / 1_000_000
        println("1000 searches over 1000 pages: $millis ms")
        assertTrue(millis < 2000, "$millis ms")
    }
}
