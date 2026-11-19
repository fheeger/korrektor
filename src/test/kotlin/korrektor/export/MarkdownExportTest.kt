package korrektor.export

import korrektor.format.Metadata
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownExportTest {
    private fun markdown(
        pages: List<ExportPage>,
        metadata: Metadata? = null,
        markers: Boolean = false,
        header: Boolean = false,
        keep: Boolean = false,
    ): String {
        val options = ExportOptions(ExportFormat.MARKDOWN, markers, header, keep)
        return StringBuilder().also { renderMarkdown(metadata, pages.asSequence(), options, it) }.toString()
    }

    private fun page(vararg lines: String) = ExportPage(1, lines.toList())

    @Test
    fun textWithoutSpecialCharactersIsNotChanged() {
        val text = "Der kleine Krieg, ſeine Anführer (1785): „Muͤſter“ — ꝛc. 3 Uhr; was?"
        assertEquals("$text\n", markdown(listOf(page(text))))
    }

    @Test
    fun linesThatLookLikeMarkdownStructureStayText() {
        val lines =
            listOf(
                "1. Erstens",
                "2) Zweitens",
                "- x",
                "+ x",
                "* x",
                "# x",
                "## x",
                "> x",
                "===",
                "---",
                "***",
                "| a | b |",
                ":--- | ---",
                "    code",
                "\tcode",
                "```",
                "~~~",
                "[a]: http://x",
                "<div>",
                "&amp; x",
            )
        for (keep in listOf(true, false)) {
            val result = readMarkdown(markdown(listOf(page(*lines.toTypedArray())), keep = keep))
            val expected = if (keep) lines.joinToString("\n") else lines.joinToString(" ")
            assertEquals(listOf(expected), result, "keep=$keep")
        }
    }

    @Test
    fun emphasisCodeLinksAndHtmlShowAsTyped() {
        val lines =
            listOf(
                "*a* _a_ **a** __a__ `a` ~~a~~",
                "[a](http://b) ![i](c) <b>a</b> &amp; &#35; &copy;",
                "a\\b \\* \\\\",
            )
        val result = readMarkdown(markdown(listOf(page(*lines.toTypedArray())), keep = true))
        assertEquals(listOf(lines.joinToString("\n")), result)
    }

    @Test
    fun aBackslashStaysOneBackslash() {
        assertEquals("a\\\\b\n", markdown(listOf(page("a\\b"))))
        assertEquals(listOf("a\\b\\"), readMarkdown(markdown(listOf(page("a\\b\\")))))
    }

    @Test
    fun keptBreaksAreHardBreaksAndTheLastLineOfAParagraphHasNone() {
        assertEquals("A\\\nB\\\nC\n\nD\n", markdown(listOf(page("A", "B", "C"), page("D")), keep = true))
        assertEquals("A B C\n\nD\n", markdown(listOf(page("A", "B", "C"), page("D"))))
    }

    @Test
    fun theMarkerIsAParagraphOfItsOwnAndShowsAsPageN() {
        val pages = listOf(ExportPage(12, listOf("A", "B")), ExportPage(13, listOf("C")))
        val result = markdown(pages, markers = true, keep = true)
        assertEquals("\\[Page 12\\]\n\nA\\\nB\n\n\\[Page 13\\]\n\nC\n", result)
        assertEquals(listOf("[Page 12]", "A\nB", "[Page 13]", "C"), readMarkdown(result))
    }

    @Test
    fun theTitleIsAHeadingAndTheOtherFieldsAreOneParagraph() {
        val result =
            markdown(listOf(page("Text")), Metadata("Der *kleine* Krieg", "Ewald", "ca. 1785", "a_b"), header = true)
        assertEquals("# Der \\*kleine\\* Krieg\n\nAuthor: Ewald\\\nYear: ca. 1785\\\nSource: a\\_b\n\nText\n", result)
        assertEquals(
            listOf("Der *kleine* Krieg", "Author: Ewald\nYear: ca. 1785\nSource: a_b", "Text"),
            readMarkdown(result),
        )
    }

    @Test
    fun aTitleEndingInHashesStaysWhole() {
        val result = markdown(emptyList(), Metadata("Fall #"), header = true)
        assertEquals(listOf("Fall #"), readMarkdown(result))
    }

    @Test
    fun spacesAtTheEndsOfALineAreKept() {
        val lines = listOf("  a  ", " ", "\tb\t")
        for (keep in listOf(true, false)) {
            val expected = if (keep) lines.joinToString("\n") else lines.joinToString(" ")
            assertEquals(listOf(expected), readMarkdown(markdown(listOf(page(*lines.toTypedArray())), keep = keep)))
        }
    }

    @Test
    fun anyMixOfMarkupCharactersIsReadBackAsTheTypedLines() {
        val tokens =
            listOf(
                "*",
                "_",
                "1.",
                "-",
                "#",
                ">",
                "`",
                "[",
                "](x)",
                "<b>",
                "&amp;",
                "~~",
                "=",
                "|",
                " ",
                "\\",
                "a",
                "ſ",
                "2)",
                ":-",
                "\t",
                "+",
                "!",
            )
        val random = Random(42)
        repeat(300) { round ->
            val lines =
                List(1 + random.nextInt(4)) { List(random.nextInt(1, 7)) { tokens.random(random) }.joinToString("") }
                    .filter { it.isNotEmpty() }
            val keep = round % 2 == 0
            val result = readMarkdown(markdown(listOf(ExportPage(1, lines)), keep = keep))
            val expected = if (keep) lines.joinToString("\n") else lines.joinToString(" ")
            assertEquals(listOf(expected), result, "lines=$lines keep=$keep")
        }
    }
}
