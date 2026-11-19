package korrektor.export

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Document
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/**
 * What a Markdown reader (CommonMark) makes of [markdown]: the paragraphs and headings with their text, a hard break as
 * `\n`. The test is strict on purpose: if the reader finds anything else (emphasis, code, a link, a list, a quote, a
 * rule, HTML), the text was not escaped enough, and this fails.
 */
fun readMarkdown(markdown: String): List<String> {
    val document =
        Parser
            .builder()
            .extensions(
                listOf(TablesExtension.create(), StrikethroughExtension.create()),
            ).build()
            .parse(markdown)
    check(document is Document)
    return generateSequence(document.firstChild) { it.next }.map(::blockText).toList()
}

private fun blockText(block: Node): String {
    check(block is Paragraph || block is Heading) { "unexpected block: ${block::class.simpleName}" }
    return generateSequence(block.firstChild) { it.next }.joinToString("") { inline ->
        when (inline) {
            is Text -> inline.literal
            is HardLineBreak -> "\n"
            is SoftLineBreak -> "\n"
            else -> error("unexpected inline: ${inline::class.simpleName}")
        }
    }
}
