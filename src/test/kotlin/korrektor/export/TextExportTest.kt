package korrektor.export

import korrektor.format.Metadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextExportTest {
    private val book =
        sequenceOf(
            ExportPage(7, listOf("Der kleine Krieg ist die Fort-", "setzung der Politik", "mit anderen Mitteln")),
            ExportPage(8, listOf("Zweite Seite")),
        )

    private fun text(
        pages: Sequence<ExportPage> = book,
        metadata: Metadata? = Metadata("Abhandlung", "Ewald", "1785", "Google Books"),
        markers: Boolean = false,
        header: Boolean = false,
        keep: Boolean = false,
    ): String {
        val options = ExportOptions(ExportFormat.TEXT, markers, header, keep)
        return StringBuilder().also { renderText(metadata, pages, options, it) }.toString()
    }

    @Test
    fun theDefaultsAreAllOnForTextAndAllOffForMarkdown() {
        assertEquals(ExportOptions(ExportFormat.TEXT, true, true, true), ExportOptions.defaultsFor(ExportFormat.TEXT))
        assertEquals(
            ExportOptions(ExportFormat.MARKDOWN, false, false, false),
            ExportOptions.defaultsFor(ExportFormat.MARKDOWN),
        )
    }

    @Test
    fun keptBreaksGiveOneFileLinePerLineAndHyphensStay() {
        val expected = "Der kleine Krieg ist die Fort-\nsetzung der Politik\nmit anderen Mitteln\n\nZweite Seite\n"
        assertEquals(expected, text(keep = true))
    }

    @Test
    fun joinedLinesAreOneLineWithSpacesAndPagesAreSeparatedByABlankLine() {
        val expected = "Der kleine Krieg ist die Fort- setzung der Politik mit anderen Mitteln\n\nZweite Seite\n"
        assertEquals(expected, text(keep = false))
    }

    @Test
    fun aMarkerUsesThePdfPageNumberAndComesBeforeEachPage() {
        val pages = sequenceOf(ExportPage(7, listOf("A", "B")), ExportPage(8, listOf("C")))
        assertEquals("[Page 7]\nA\nB\n\n[Page 8]\nC\n", text(pages, markers = true, keep = true))
        assertEquals("[Page 7]\nA B\n\n[Page 8]\nC\n", text(pages, markers = true, keep = false))
    }

    @Test
    fun theHeaderHasTheFieldsThatAreNotEmpty() {
        val pages = sequenceOf(ExportPage(1, listOf("Text")))
        assertEquals(
            "Abhandlung\nAuthor: Ewald\nYear: 1785\nSource: Google Books\n\nText\n",
            text(pages, header = true),
        )
        assertEquals("T\nYear: ca. 1790\n\nText\n", text(pages, Metadata("T", year = "ca. 1790"), header = true))
    }

    @Test
    fun noHeaderWithoutMetadataOrWithTheOptionOff() {
        val pages = sequenceOf(ExportPage(1, listOf("Text")))
        assertEquals("Text\n", text(pages, metadata = null, header = true))
        assertEquals("Text\n", text(pages, header = false))
    }

    @Test
    fun charactersAreWrittenAsStored() {
        val stored = "ſeyn ﬁnden Muͤſter ꝛc."
        assertEquals("$stored\n", text(sequenceOf(ExportPage(1, listOf(stored))), keep = true))
    }

    @Test
    fun aBreakInsideALineIsAFormOfSpace() {
        val pages = sequenceOf(ExportPage(1, listOf("a\r\nb\nc\rd", "e")))
        assertEquals("a b c d\ne\n", text(pages, keep = true))
        val one = sequenceOf(ExportPage(1, listOf("z")))
        assertEquals("T\nAuthor: x y\n\nz\n", text(one, Metadata("T", "x\ny"), header = true))
    }

    @Test
    fun emptyLinesAndPagesAddNothingButAMarkerIsStillWritten() {
        val pages =
            sequenceOf(ExportPage(1, listOf("A", "", "B")), ExportPage(2, emptyList()), ExportPage(3, listOf("C")))
        assertEquals("A\nB\n\nC\n", text(pages, keep = true))
        assertEquals("[Page 1]\nA\nB\n\n[Page 2]\n\n[Page 3]\nC\n", text(pages, markers = true, keep = true))
    }

    @Test
    fun nothingToExportIsAnEmptyFile() {
        assertEquals("", text(emptySequence()))
        assertEquals("", text(sequenceOf(ExportPage(1, emptyList()))))
    }

    @Test
    fun linesEndWithLfAndTheFileHasNoBom() {
        val result = text(book, keep = true, markers = true, header = true)
        assertFalse(result.contains('\r'))
        assertFalse(result.startsWith("﻿"))
        assertTrue(result.endsWith("Zweite Seite\n"))
    }
}
