# T095 The export runs in the background and writes crash-safely

**Type:** task · **Status:** done · **Story:** S11 · **Task:** S11.6

## What needs doing
- `BookExport` (in `korrektor/export/`): for a project and its editor, a page range and the options, it reads the pages (`currentLayer`: what the user sees, also what is not saved yet), renders them and writes the file with `AtomicFile`, on the IO dispatcher, never on the UI thread.
- Progress (pages done of pages in all), cancel (nothing is written, an existing file stays as it was), and errors with a message: a page that cannot be read stops the export and the message names the page (a silent gap in the text is worse); a write that fails reports the reason.
- The export never changes the project, the editor or the history.

## Acceptance criteria
1. The file is UTF-8 without BOM, with LF line endings.
2. A failing write (simulated) leaves an existing target file untouched and no `.tmp` file behind; a cancel does the same.
3. A page that cannot be read: the export fails, the message names the page, no file is written.
4. Progress counts the pages of the range; the UI thread is not used (checked with a stand-in UI thread that keeps ticking).
5. Pages with corrections that are not saved yet are exported as they are in the editor.
6. The project folder is unchanged by an export (no new file in it, the history and the pages as before).

## Approach
`BookExport.kt` with a `suspend fun run(...)` and a progress callback; the reading of a page is a function parameter (a seam for tests). Unit tests with a temp project; the speed with 1000 pages is in T098.

## Dependencies
T093, T094.

## Decisions
- The whole text is built in memory and written in one go: `AtomicFile.write` takes bytes, a 1000-page book is a few MB, and the file is replaced only when all of it is ready, so a failure or cancel never leaves half a file.
- A page that cannot be read stops the export (no partial file) and the message names the page: a gap in a text that is meant to be complete is worse than no file.
- The range is positions in the book (1..pages); the marker uses the `pdfPage` of the layer.

## Progress
Done. `BookExport.kt` (`BookExport.run`, `ExportException`), `BookExportTest` (7 tests: output bytes, unsaved corrections, project unchanged, unreadable page, failing write, cancel, threads), architecture overview. Build green.
