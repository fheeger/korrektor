# T021 Import a PDF into a project

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.5

Put the pieces together: read every page of a project's PDF, build its page layer, and write the original and the corrected layer, with progress and a way to stop. Without UI.

## What needs doing
- `PdfImport.run(project, progress, isCancelled): ImportResult` (package `korrektor.importer`), synchronous (the caller puts it on a background thread, T023):
  - opens `project.sourcePdf`, reads the page count, and for every page: size and words (T018, T019), lines and region (T020) into a `PageLayer`, the **original layer**;
  - the **corrected layer** is a copy of it that starts the same: the same region and line ids, and each line's `origin` set to its own id (as the spec says);
  - writes `pages/NNNN.ocr.json` (once) and `pages/NNNN.json`;
  - calls `progress(done, total)` after every page; checks `isCancelled()` before every page and stops with a `CancellationException` if it is true;
  - returns an `ImportResult` (pages imported, and per page a note if there was one: T022).
- `Project.setPages(pages: List<PageInfo>)`: sets the page list and writes `project.json` crash-safely, like `updateMetadata` (and restores the old list if writing fails). The import calls it first, with one entry per PDF page, because the page files are addressed by page number.
- Pages are numbered like the PDF (`pdfPage` = n); the format does not rely on that, the import just produces it.
- A project whose import was cancelled or failed is not cleaned up here; the caller deletes it (T023).
- `docs/architecture.md`; the spec sentence "the import (S03) fills the page list" gets a reference to the rules.

## Acceptance criteria
1. Importing a generated 3-page PDF gives `project.pages` with three entries, and for each page both files; reading them back through `Project` works.
2. For every page the corrected layer equals the original except for the `origin` of each line (set to the line's own id); the original is never written twice (a second import into the same project is refused).
3. The written files are canonical: opening and saving them without change leaves the bytes identical.
4. `progress` is called once per page with increasing `done`, ending at `total`.
5. If `isCancelled()` turns true after page 2 of 5, the import stops with a `CancellationException`, and only the pages already written exist.
6. A PDF that PDFium can't open fails with a clear message (`FormatException` or the `PdfException` text), and nothing in the project changes (the page list stays empty).
7. `Project.setPages` writes `project.json`, keeps unknown fields, and keeps the old list if writing fails.
8. On the sample PDF (if present): 165 pages, every page's files readable.

## Approach
- `ImportResult` and the cancel check are plain Kotlin, so tests can drive them without threads.
- Page ids and `origin` are assigned in one place (a function that copies a layer into its starting corrected form), so S05 and later can reuse it.
- Tests with generated PDFs plus one sample test. The whole-import tests that cover several tickets are T024.

## Dependencies
T018, T019, T020.

## Decisions
None yet.

## Progress
Not started.
