package korrektor.importer

/** How many page numbers a summary lists before it says "and N more". */
private const val MAX_LISTED_PAGES = 12

/**
 * One or two sentences for the user about the pages that could not be imported fully, for example
 * `5 pages have no text: 3, 139, 161, 163, 165.`, or null if there are none.
 */
fun ImportResult.summary(): String? {
    val noText = notes.filter { it.text == PdfImport.NO_TEXT }.map { it.page }
    val unreadable = notes.filter { it.text != PdfImport.NO_TEXT }.map { it.page }
    val sentences =
        listOfNotNull(
            noText.takeIf { it.isNotEmpty() }?.let { sentence(it, "has no text", "have no text") },
            unreadable.takeIf { it.isNotEmpty() }?.let { sentence(it, "could not be read", "could not be read") },
        )
    return sentences.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private fun sentence(
    pages: List<Int>,
    one: String,
    many: String,
): String {
    val listed = pages.take(MAX_LISTED_PAGES).joinToString(", ")
    val more = if (pages.size > MAX_LISTED_PAGES) " and ${pages.size - MAX_LISTED_PAGES} more" else ""
    return if (pages.size == 1) "1 page $one: $listed." else "${pages.size} pages $many: $listed$more."
}
