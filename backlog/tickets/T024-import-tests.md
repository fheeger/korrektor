# T024 Import tests: sample PDF and generated PDFs

**Type:** task · **Status:** done · **Story:** S03 · **Task:** S03.8

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
- **Three test classes** in `src/test/kotlin/korrektor/importer/`: `ImportSampleTest` (the sample, one import shared by no test: each test imports on its own, about 3 s), `ImportGeneratedPdfsTest` (the special pages), `ImportFlowTest` (the flow without the UI, and the 1000 pages).
- **Cancel and failure with a real PDF** were already tested on the model level (`NewProjectModelTest`, T023). `ImportFlowTest` adds the same at the level of `createProject`, which the dialog runs, so both layers are covered.
- **The PDFBox comparison matches characters to words by position:** the characters of PDFBox whose baseline lies at the bottom edge of a word's box (PDFium's box bottom is the baseline) and whose middle lies between the word's left and right edge must spell the word, and span its width within 1 point. The vertical extent of the boxes is not compared: PDFium's box is the font size high, PDFBox's height comes from the font, so they are different measures, not different readings.
- **What the comparison leaves out, with reasons:**
  - ligatures: PDFBox returns `ﬁ`, PDFium spells `fi`. Only the f-ligatures are expanded on the PDFBox side; a general normalization would turn `ſ` into `s` and hide what the test must protect;
  - the notice page (a real font) has a tolerance of 2 points instead of 1: PDFBox's characters are as wide as the font's advance, PDFium's boxes as wide as the glyph;
  - words that reach beyond the page edge (specks at the scan border, e.g. a Hebrew "word" at x = -1.7 on page 36);
  - pages 140 and 156, where a letter is drawn twice: PDFium keeps both (`Daa`), PDFBox only one. The import follows PDFium; the text layer does contain the letter twice.
  Pages compared: 1, 2, 6, 7, 24, 36, 50, 100, 120, 160.
- **Two-column test:** one region, and each printed row is one line across both columns (`alpha one beta one`). This is the documented limitation until B3/B4.
- **Rotated page:** a page with `/Rotate 90` is imported with width and height swapped, and the text, which turns with the page, runs downwards (tall, narrow word box on the right side).
- **Hebrew:** PDFium reverses right-to-left runs. Recorded in `docs/pdf-text-layer.md` section 5; the generated-PDF test asserts the reversed text.
- **1000 pages** are made with PDFBox in the test (every 7th page blank), not stored.

## Results
- All tests of T024 pass; the sample tests run locally and are skipped without the file (every sample test uses `assumeTrue`). `./gradlew build koverXmlReport` is green, Detekt and ktlint without findings.
- **1000 pages** (`KORREKTOR_PERF_TESTS=1`): 6.6 s for the import, progress for every page in order, memory after each 100 pages constant at 21 MB (so one page at a time is held).
- **Overlays** (throwaway test, not committed; rendered with PDFium at 3 x, word boxes blue, line boxes red, line numbers): pages **1** (notice), **2** (title), **6**, **50**, **100** and **140** were looked at.
  - The word boxes sit on the printed words and the line numbers run in reading order on all six pages. The title page's large Fraktur lines, the drop caps and the page numbers, the running heads and the signature and catchword lines (`A 3 ... Mit`) are in the right places. On the notice page the ten bullets are an unusual character (`⊞`) in a line of its own, and each paragraph is its own line group.
  - **Problem 1, stray words widen lines:** a speck the OCR read as a word (page 6 line 7, page 100 line 9, page 140 line 8) lies at the right edge, far outside the text. It joins the line (it overlaps vertically), and the line's box becomes much wider than the printed text. Text and word boxes are right; only the line box (and, for the corrected layer, a later display of it) is wide. It will matter for a viewer that draws line boxes (S05).
  - **Problem 2, no table or footnote page** was looked at: there is none among the pages seen, and the book has no tables. The criterion "a page with a table or a footnote" is not covered; the catchword at the bottom is the only separate text block.
  - The drop caps (page 50, 100, 140) are words of their own and sit beside the first line; on page 140 the line "Da man mit einem ..." has the drop cap and the first word in one line, which is the reading order.

## Progress
Done.
