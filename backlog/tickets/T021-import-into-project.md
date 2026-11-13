# T021 Import a PDF into a project

**Type:** task · **Status:** in progress · **Story:** S03 · **Task:** S03.5

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
- **The page list is set first** (`Project.setPages`), with one entry per PDF page, because the page files are addressed by page number and `Project` checks that a page exists before it writes it. A project that is cancelled or fails half way therefore has a page list and only some page files; the caller deletes such a project (T023). A crashed import leaves the same: reading a missing page gives "Page n has no file", which is what it is.
- **A second import is refused** (`FormatException`) when the project already has pages: the original layer is written once and never replaced, and an import must not silently overwrite what the user has corrected since.
- **The PDF errors are not wrapped:** `PdfException` (its message is for the user) passes through; the caller (T023) handles it next to `FormatException` and `IOException`. The project is not changed when the PDF cannot be opened, because that happens before `setPages`.
- **Cancelling is a `CancellationException`** (the one coroutines also use), asked before every page, so a cancel takes effect after at most one page.
- **`Project.setPages` shares its "write, or put the old value back" logic with `updateMetadata`** in a small private helper, which also keeps `Project` under Detekt's limit of functions per class.
- **Pages are written one after the other, each through the crash-safe write,** so the whole sample (330 files) takes under a second; 1000 pages are measured in T024.
- Pages without text become empty pages (`regions: []`); the note for them is T022.

## Progress
Done: `PdfImport.run`, `ImportResult`, `Project.setPages`; the spec sentence and `docs/architecture.md`. 12 new tests: 3 for `setPages` (writes the list and keeps metadata and unknown fields; page files can be written for the new pages and not beyond; a failed write keeps the old list) and 9 for the import (every page in both layers with the right sizes and lines, the corrected layer equals the original with `origin` = own id, canonical files and unchanged bytes on opening and saving, progress once per page, cancelling after 2 of 5 pages leaves exactly 2 pages, cancelling at once leaves none, a second import is refused, a text file as PDF fails with its message and changes nothing, and the sample: 165 pages, all readable, pages 3/139/161/163/165 empty, page 6 starts with `Vorrede. 5`, in 0.8 s). `./gradlew build` is green.
