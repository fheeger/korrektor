# T095 The export runs in the background and writes crash-safely

**Type:** task · **Status:** ready for development · **Story:** S11 · **Task:** S11.6

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
(none yet)

## Progress
Not started.
