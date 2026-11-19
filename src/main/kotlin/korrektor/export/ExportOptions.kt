package korrektor.export

/** What the corrected text is exported as (G1). */
enum class ExportFormat(
    val label: String,
    val extension: String,
) {
    TEXT("Plain text", "txt"),
    MARKDOWN("Markdown", "md"),
}

/**
 * The choices of one export. [pageMarkers] writes a `[Page n]` line before each page, [header] the metadata at the top,
 * and [keepLineBreaks] keeps each printed line as a line of its own (else the lines of a page are joined).
 */
data class ExportOptions(
    val format: ExportFormat,
    val pageMarkers: Boolean,
    val header: Boolean,
    val keepLineBreaks: Boolean,
) {
    companion object {
        /** All on for plain text, which is read as it is; all off for Markdown, which is meant to flow. */
        fun defaultsFor(format: ExportFormat): ExportOptions {
            val on = format == ExportFormat.TEXT
            return ExportOptions(format, pageMarkers = on, header = on, keepLineBreaks = on)
        }
    }
}
