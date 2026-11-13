# T024 Import tests: sample PDF and generated PDFs

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.8

Tests that cover several tickets of S03 together, with the real sample and with generated PDFs: the whole flow from "New project" to the written pages, special pages, and a 1000-page run.

## What needs doing
- **`ImportSampleTest`** (skipped when the sample is missing):
  - all 165 pages are imported with their own sizes (compared with PDFBox's page boxes);
  - the pages without text (3, 139, 161, 163, 165) are empty with the note; all others have lines;
  - word texts and boxes agree with an independent extraction (PDFBox) on a set of pages, within 1 point;
  - nothing is normalized: the characters `ſ`, `ß`, `ä`, line-end `-` and `=` are in the imported text, and the notice page is imported like any other;
  - the imported project reopens and every page can be read; opening and saving leaves the bytes identical.
- **Generated PDFs** (`TestPdfs`): a page without text, a page with `/Rotate 90`, two columns (import works, the lines are mixed as documented), an image-only page, invisible text, unusual Unicode, a PDF with a broken page.
- **Whole-flow tests:** `NewProject.create` + import + reopen, and the cancel and failure cases with a real PDF (the folder is as before).
- **1000 pages** (generated each run in a temp folder, not stored; runs only with `KORREKTOR_PERF_TESTS`, like the other timing checks): the import completes, progress is reported for every page, memory stays bounded (the importer holds one page at a time), and the time is printed.
- **The spot check of the story criterion** "word boxes sit on the printed words and the line order matches the reading order": a throwaway script (not a test, not committed) renders a few sample pages with PDFium, draws the imported word and line boxes over them, and writes PNGs; Claude looks at them and the story report says which pages were looked at and what was seen. Pages: 2 (title), 6, 50, a page with a table or a footnote if there is one, and the notice page.
- A table in the story report maps each S03 criterion to its test.

## Acceptance criteria
1. Every acceptance criterion of S03 that concerns logic and files (not the UI look) is covered by a test in T016–T023 or in this ticket, and these tests pass.
2. The sample tests run with the file and are skipped without it (checked on the CI report).
3. The 1000-page test passes when enabled, and its time is recorded in the ticket.
4. The overlays of at least five sample pages were looked at, and the findings (also problems) are in the ticket and in the story report.

## Approach
- Plain Kotlin tests in `src/test/kotlin/korrektor/importer/`. The PDFBox comparison for the sample is the independent oracle (PDFBox reads the same text layer through different code).
- The 1000-page PDF is made with PDFBox in a loop (one small text per page, with some pages blank).

## Dependencies
T021, T022, T023.

## Decisions
None yet.

## Progress
Not started.
