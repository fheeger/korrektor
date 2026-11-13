package korrektor.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportSummaryTest {
    private val noText = PdfImport.NO_TEXT

    private fun result(vararg notes: PageNote) = ImportResult(165, notes.toList())

    @Test
    fun noNotesGiveNoSummary() {
        assertNull(ImportResult(165).summary())
    }

    @Test
    fun onePageWithoutTextIsSaidInTheSingular() {
        assertEquals("1 page has no text: 2.", result(PageNote(2, noText)).summary())
    }

    @Test
    fun severalPagesWithoutTextAreListed() {
        val notes = listOf(3, 139, 161, 163, 165).map { PageNote(it, noText) }
        assertEquals("5 pages have no text: 3, 139, 161, 163, 165.", ImportResult(165, notes).summary())
    }

    @Test
    fun pagesThatCouldNotBeReadAreNamedApart() {
        val unreadable = "The text of this page could not be read: broken."
        assertEquals("1 page could not be read: 4.", result(PageNote(4, unreadable)).summary())
        assertEquals(
            "2 pages could not be read: 7, 9.",
            result(PageNote(7, unreadable), PageNote(9, unreadable)).summary(),
        )
    }

    @Test
    fun bothKindsMakeTwoSentences() {
        val summary =
            result(
                PageNote(2, noText),
                PageNote(5, "The text of this page could not be read: x."),
                PageNote(8, noText),
            ).summary()
        assertEquals("2 pages have no text: 2, 8. 1 page could not be read: 5.", summary)
    }

    @Test
    fun aLongListIsCutAfterTwelvePages() {
        val notes = (1..15).map { PageNote(it, noText) }
        assertEquals(
            "15 pages have no text: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 and 3 more.",
            ImportResult(165, notes).summary(),
        )
    }
}
