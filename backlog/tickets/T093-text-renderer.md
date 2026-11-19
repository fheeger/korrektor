# T093 Export text: the renderer for plain text, with page markers, header and line breaks

**Type:** task · **Status:** ready for development · **Story:** S11 · **Task:** S11.2, S11.4

## What needs doing
- A pure renderer in `korrektor/export/`: from the metadata, the pages (the PDF page number and the lines of the corrected layer in reading order) and the options, it writes the text of the export.
- `ExportOptions(format, pageMarkers, header, keepLineBreaks)`, `ExportFormat { TEXT, MARKDOWN }` with the defaults per format (all on for plain text, all off for Markdown).
- Page markers: a line `[Page 12]` before each page, with the PDF page number of the layer.
- Header: the title, then `Author:`, `Year:`, `Source:` for the values that are not empty, then a blank line. No metadata: no header.
- Line breaks kept: each line is a line of the file. Not kept: the lines of a page are joined with a space; pages are separated by a blank line.
- LF line endings, one `\n` at the end of the file, no BOM; the text of a line is used as stored (no normalization).

## Acceptance criteria
1. A page with three lines gives three lines with breaks kept, one line when joined; hyphens at line ends stay.
2. Pages are separated by one blank line; with markers on, each page starts with `[Page n]` where n is `pdfPage` of the layer, not the position.
3. The header has the non-empty metadata fields; an empty author is left out; with no metadata or the option off there is no header.
4. Lines without a box (added by the user) are included like any other line.
5. Characters are written as stored: long s, Fraktur ligatures, combining characters are not changed.
6. A line with a CR or LF inside (it can come from a paste) becomes one line: the break is replaced by a space, so that the structure of the file stays true.
7. A line without text and a page without lines add nothing, except that a page marker is still written for an empty page.

## Approach
`ExportOptions.kt`, `TextExport.kt` (writing to an `Appendable` page by page, so that a 1000-page book is never one string). The Markdown renderer (T094) reuses the page and paragraph structure, so the renderer is built around a small `ExportStyle` with the few things that differ (marker, header, line break, escape). Unit tests with synthetic layers.

## Dependencies
None.

## Decisions
(none yet)

## Progress
Not started.
