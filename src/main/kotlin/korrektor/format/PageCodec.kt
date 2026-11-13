package korrektor.format

/**
 * Reads and writes a [PageLayer] as the text of a page file (`pages/0001.json`, `pages/0001.ocr.json`).
 *
 * Layout: one member per line at the top, one block per region, and exactly one line of the book per line
 * of the file, so correcting a word changes one line in a diff. See `docs/project-format.md`.
 */
object PageCodec {
    fun encode(page: PageLayer): String = PageEncoder.encode(page)

    fun decode(text: String): PageLayer = PageDecoder.decode(text)
}
