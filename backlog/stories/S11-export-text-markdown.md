# S11 Export plain text and Markdown

**Features:** G1 · **Status:** in review

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
- [x] S11.1 Export dialog: format, page range, options with defaults per format, file location; remember last choices per format in the app settings → [T096](../tickets/T096-export-settings.md) (remembering), [T097](../tickets/T097-export-dialog.md) (dialog)
- [x] S11.2 Plain text export → [T093](../tickets/T093-text-renderer.md)
- [x] S11.3 Markdown export with escaping → [T094](../tickets/T094-markdown-export.md)
- [x] S11.4 Options: page markers, header from the project metadata, printed line breaks kept or joined → [T093](../tickets/T093-text-renderer.md)
- [x] S11.5 Note about unchecked pages → [T097](../tickets/T097-export-dialog.md)
- [x] S11.6 Run in the background, writing crash-safely → [T095](../tickets/T095-export-job.md)
- [x] S11.7 Tests: → [T098](../tickets/T098-export-tests.md)
  - Export the sample PDF in both formats with options on and off, and compare with expected results.
  - Escaping edge cases.
  - Check the Markdown in a renderer.
  - Speed in a 1000-page book.

## Report

### What was built
- **Export dialog:** **Ctrl+E**, *File ▸ Export…* (a new File menu) or the "Export…" button in the project header. Format (plain text or Markdown), page range (the whole book at first), three options (page markers, header, printed line breaks kept), the note about unchecked pages, a progress bar and Cancel (T097, T096).
- **Renderer** (`korrektor.export`, no UI): the same structure for both formats, with an `ExportStyle` for what differs. Plain text (T093), Markdown with escaping (T094).
- **Export job** (`BookExport`): reads the pages from the editor (unsaved corrections included), renders and replaces the file with `AtomicFile`, all on the IO dispatcher. An unreadable page stops the export and names the page; Cancel leaves the old file (T095).
- **Remembered choices:** the options of the last export per format under `export` in `settings.json` (T096).
- Tests (T098) and review fixes (T099).

### Decisions
- A page is one block: marker, line break, lines. In Markdown the marker is a paragraph of its own. Blocks are separated by one blank line; the file ends with one LF.
- Lines with no text are left out (a blank line would split a Markdown paragraph). A CR or LF inside a line becomes a space.
- Markdown escapes `\ ` * _ [ ] < > ~` everywhere and `&` where it starts an entity; at the start of a paragraph or kept line also `# - + = |`, `:-` and a number before `.` or `)`; a space or tab at the ends of a line is written as a character reference. A few needless backslashes are accepted: a wrong italic is worse.
- Only the options are remembered, not the last format; each format keeps its own options while the dialog is open.
- The unchecked note counts a page as unchecked if it has no status; a status unknown to this version counts as checked (as in S09). The note says the pages are exported as they are.
- While an export runs the choices are locked, the dialog and the window cannot be closed; Cancel stops it.
- New test dependency: commonmark-java with its GFM table and strikethrough extensions (test scope only, BSD 2-clause, in `docs/third-party.md`).

### How each criterion was checked
- **Plain text or Markdown, whole book or range:** `ExportGoldenTest` (16 expected files, a range), `ExportSampleTest` (range 2..165 on the sample), `ExportDialogUiTest`.
- **Corrected layer as stored, no normalization, lines without a box:** `TextExportTest`, `ExportGoldenTest` (long s, combining letter, a line without a box), `ExportSampleTest` (plain text worked out again from the stored lines, for all 8 option combinations).
- **Options and defaults (markers use the PDF page number, header, breaks kept or joined, `\` hard break):** `TextExportTest`, `MarkdownExportTest`, `ExportGoldenTest`, `ExportModelTest`.
- **Remembered per format, defaults only the first time:** `ExportSettingsTest`, `ExportModelTest`, `ExportDialogUiTest`.
- **Markdown escaping:** `MarkdownExportTest`, including 300 random lines of markup characters, and the sample, read back by a strict CommonMark + GFM reader that fails on anything but plain paragraphs and headings.
- **UTF-8 without BOM, LF, crash-safe:** `BookExportTest` (bytes, a failing write and a cancel leave the old file and no temp file).
- **Unchecked pages exported, with a note:** `ExportModelTest`, `ExportDialogUiTest`.
- **Background, 1000 pages, UI free:** `ExportPerfTest`; the work does not run on the calling thread (`BookExportTest`). Measured: 1000 pages × 30 lines in about 0.4 s as plain text and as Markdown; the UI stand-in waited at most 19 ms between two ticks of 5 ms. The time limits are only asserted with `KORREKTOR_PERF_TESTS=1`.
- **The project is not changed by an export:** `BookExportTest`, `ExportFlowTest` (files, history, statuses unchanged).
- **Menu, key, list:** `MenusTest`, `ShortcutsTest`, `ShortcutListTest`.

### Code review (Opus 5.5, whole story)
Three findings, fixed in T099: (1) a name typed without the extension was extended after the system's overwrite check, so a file could be replaced without asking (the picker now puts the extended name back into the dialog); (2) closing the window during an export could leave a `.tmp` file (the window now stays open while an export runs); (3) the Markdown tests read with plain CommonMark, so the escapes for tables and strikethrough were not tested (the reader now has the GFM extensions). Everything else was checked and found fine.

### Known gaps
- Not tried by hand: the real window (the look of the dialog) and the system save dialog, including the overwrite question and the extension loop of T099. `ExportPicker.kt` is the only new code without test coverage.
- A GFM viewer turns bare addresses such as `www.example.de` into links; the text stays the same.
- The whole text is built in memory before it is written (a few MB for 1000 pages).
- Quitting the app by killing it during the write leaves the old file but may leave a `.tmp` next to it.

### How to try it
Open the sample project and press **Ctrl+E**. Choose *Plain text*, set the first page to 2 (leaves out the Google Books notice), keep all three options and click "Export…"; choose a file. The note shows how many pages are still unchecked. Open the file: `[Page 2]`, the lines as printed, `ſ` unchanged. Export again as *Markdown* (all options off at first) and open the file in a Markdown viewer: one paragraph per page. Switch *Keep the printed line breaks* on and the lines break again. Type a name without extension in the save dialog and check that the question for an existing file is asked.
