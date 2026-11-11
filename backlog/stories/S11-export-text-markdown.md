# S11 Export plain text and Markdown

**Features:** G1 · **Status:** ready for development

As a reader of historical sources, I want to export the corrected text as plain text or Markdown
so that I can read, search and reuse it outside the app.

S11 exports the corrected text as `.txt` or `.md`. The export never changes the project. Not part of it: normalization (D5), rejoining hyphenated words (D4), headings and chapters (E4), formatted exports (G2) and a searchable PDF (G3).

## Acceptance criteria
- You can export as plain text or Markdown, either the whole book or a page range (for example, to leave out the notice page Google Books adds at the front).
- The export uses the corrected layer exactly as stored, with no normalization (V1). Lines added without a box (S05) are included like any other line.
- Options: page markers, header, and keeping the printed line breaks. Defaults: all on for plain text, all off for Markdown.
  - Page markers: a visible line such as `[Page 12]` before each page, using the page number in the PDF.
  - Header: the metadata from A1 (title, author, year, source) at the top. In Markdown, the title becomes a heading.
  - Printed line breaks kept: each line stays a separate line. In Markdown this uses a hard break (`\`).
  - Printed line breaks off: the lines of a page are joined with spaces, and pages are separated by a blank line. Hyphens at line ends stay (D4 comes later).
- The dialog remembers your last choices for each format in the app settings. The defaults apply only to the first export.
- In Markdown, characters Markdown would interpret are escaped. For example, a line starting with "1." doesn't become a list, and `*` or `_` don't turn into italics.
- Files are UTF-8 without a BOM, with LF line endings, and are written crash-safely (S01.6).
- Unchecked pages are exported too. Before exporting you see a note, for example "38 of 165 pages are still unchecked".
- Exporting a 1000-page book runs in the background, takes a few seconds, and doesn't freeze the UI (N1).

## Tasks
- [ ] S11.1 Export dialog: format, page range, options with defaults per format, file location; remember last choices per format in the app settings
- [ ] S11.2 Plain text export
- [ ] S11.3 Markdown export with escaping
- [ ] S11.4 Options: page markers, header from the project metadata, printed line breaks kept or joined
- [ ] S11.5 Note about unchecked pages
- [ ] S11.6 Run in the background, writing crash-safely
- [ ] S11.7 Tests:
  - Export the sample PDF in both formats with options on and off, and compare with expected results.
  - Escaping edge cases.
  - Check the Markdown in a renderer.
  - Speed in a 1000-page book.
