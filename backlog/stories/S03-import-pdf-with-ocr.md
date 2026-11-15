# S03 Import a PDF with OCR

**Features:** A2 · **Status:** done

As a reader of historical sources, I want to import a PDF that already has OCR, with its page images and the text with positions
so that I can start correcting without running OCR myself.

A PDF with OCR contains the scanned image plus an invisible text layer with characters or words and their positions, but no lines, regions or confidence values. The import rebuilds the lines. Layout detection (B3) comes later, so each page gets one body region.

## Acceptance criteria
- The import runs right after the project is created (S02) and covers every page of the PDF, including notice pages such as the one Google Books adds at the front.
- For each page it stores the page size, one body region, the lines in reading order, and the words with text and box, following the format from S01.
- The imported text is stored as the original OCR layer and also copied as the starting point for the corrected layer.
- The text is imported as is: no normalization (V1), and no rejoining of hyphenated words (D4 comes later).
- Pages without a text layer are imported as empty pages with a note and don't stop the import.
- The import runs in the background with a progress display and can be cancelled. Cancelling leaves no half-created project.
- Sample PDF: all ~165 pages are imported. On a few spot-check pages, the word boxes sit on the printed words and the line order matches the reading order.
- A 1000-page PDF imports without freezing the UI (N1).

Known limitation: pages with several columns may have their lines mixed up until layout detection and layout fixing (B3, B4) exist.

## Tasks
- [x] S03.1 Analyze the sample PDF: how the text layer is built (characters or words, order, coordinates, encoding) → [T016](../tickets/T016-pdfium-binding.md), [T017](../tickets/T017-text-layer-analysis.md)
- [x] S03.2 Extract words with text and boxes from a page → [T018](../tickets/T018-extract-words.md)
- [x] S03.3 Group words into lines and put lines into reading order; create one body region per page → [T020](../tickets/T020-lines-and-region.md)
- [x] S03.4 Convert coordinates into the format's coordinate system → [T019](../tickets/T019-coordinates.md)
- [x] S03.5 Write the original and corrected layers for each page → [T021](../tickets/T021-import-into-project.md)
- [x] S03.6 Run the import in the background with progress and cancel → [T023](../tickets/T023-import-in-background.md)
- [x] S03.7 Handle pages without text or with a broken text layer → [T022](../tickets/T022-pages-without-text.md)
- [x] S03.8 Tests: the sample PDF plus small generated test PDFs (page without text, rotated text, two columns to check the import doesn't fail) → [T024](../tickets/T024-import-tests.md)

Also in this story, at the user's request and without a ticket of its own: the colour scheme of the app changes from violet to yellow/brown. It is done in T023, which changes the screens anyway.

## Report

### What was built
- **PDFium binding** (T016): load a PDF, pages, sizes, rotation, boxes, characters of the text layer, rendering. Library downloaded by Gradle with a pinned checksum.
- **Analysis of the sample text layers** (T017, `docs/pdf-text-layer.md`): words are separate text pieces, PDFium generates spaces and some line breaks, hyphens at line ends are partly replaced by a marker, order is not reliable.
- **Import** (T018–T022): words with text and box, coordinates of the format (points, origin top-left of the visible page, after rotation and crop box), lines grouped by position into one body region `r1`, original layer written once and the corrected layer as a copy whose lines point to themselves, pages without text or unreadable pages as empty pages with a note.
- **UI** (T023): import runs right after "New project" on a background thread, with progress and Cancel; cancelling or failing deletes the new project. The colour scheme changed from violet to yellow/brown (done in T023 at the user's request).
- **Tests** (T024) and **review fixes** (T025).

### Decisions
All in the tickets under "Decisions"; the ones that shape the product:
- Words come from the characters PDFium reports; spaces PDFium generates are word boundaries on the Abhandlung (T017/T018). Nothing is normalized and hyphenated words are not rejoined (PDFium's hyphen marker is turned back into `-`, T018).
- The order of PDFium is not trusted; lines come from the word positions (T020).
- The page list is set before the pages are written; a crash during the import leaves a page list with only some page files (T021).
- Cancelling deletes the project folder; the check that it is a project folder stands before anything is deleted (T021/T023).
- Review decisions (T025): two review points were not fixed and are known gaps (below).

### How each criterion was checked
| Criterion | Tests |
|---|---|
| Import right after creating, all pages, notice page included | `ImportFlowTest.creatingAProjectImportsEveryPageAndTheProjectReopens`, `ImportSampleTest.all165PagesAreImportedWithTheirOwnSizes`, `...theNoticePageIsImportedLikeAnyOtherPage`, `NewProjectModelTest.aRealImportOpensTheProjectWithAllItsPagesAndTheNote` |
| Per page: size, one body region, lines in reading order, words with text and box, format of S01 | `PdfImportTest`, `PageGeometryTest`, `LineBuilderTest`, `ImportSampleTest` (sizes against PDFBox, words and boxes against PDFBox on 10 pages, files byte-stable), `ImportGeneratedPdfsTest` (rotated page) |
| Original layer stored and copied as corrected layer | `PdfImportTest.theCorrectedLayerStartsAsACopy...`, `ImportSampleTest.theCorrectedLayerStartsAsACopyOfTheOriginalOnEveryPage` |
| Text as is, no normalization, no rejoining | `ImportSampleTest.theTextIsImportedAsItIsWithoutNormalizing`, `WordExtractorTest`, `ImportGeneratedPdfsTest.unusualUnicode...` |
| Pages without text or broken: empty page with a note, import goes on | `PageNotesTest`, `ImportSampleTest.onlyThePagesWithoutTextAreEmpty...`, `ImportGeneratedPdfsTest` (picture-only, broken page) |
| Background, progress, cancel, no half-made project | `NewProjectModelTest` (progress, cancel, failure, real import), `ImportFlowTest` (cancel and failure with a real PDF, cancel during create), `FolderTreeTest`, `AppUiTest` |
| Sample: all pages; boxes sit on the words and line order matches | the sample tests above; by eye: overlays of pages 1, 2, 6, 50, 100 and 140, and `ImportSampleTest.theLinesFollowThePrintedOrderOnPagesLookedAt` (first words of the first twelve lines of pages 6, 50, 100) |
| 1000 pages without freezing the UI | `ImportFlowTest.aBookOf1000Pages...` (with `KORREKTOR_PERF_TESTS=1`: 6.6 s, memory flat at 21 MB, progress for every page); the work is off the UI thread by construction (T023) |

**Code review** (Opus 5.5, whole story): no blocking bug; eight findings. Fixed in T025: wrong note when page 1 cannot be loaded, orphan project when cancelled during the create step, `deleteTree` ignoring failures, native page leak, reading-order test that mostly confirmed itself, docs. Not fixed: the word-gap rule on rotated pages that have real spaces (see below), and the progress text (cosmetic). Areas the review found fine: coordinate conversion for all four rotations and crop box, the PDFium binding, word extraction, cancel and cleanup, the format round trip.

**Build:** `./gradlew build koverXmlReport` green (Detekt and ktlint without findings). Importer code is fully covered; uncovered are the app entry point, the file pickers and a few PDFium error paths that need a damaged PDF.

### Known gaps and uncertainties
- **Several columns** are mixed into lines across the columns until B3/B4 (documented; tested with a generated page).
- **Stray words** (specks read as words) form their own lines or join a line and then make its box much wider (pages 6, 100, 140). Text and word boxes are right; it will matter for the viewer in S05.
- **Rotated pages with real spaces:** the word-gap rule measures along the page's x axis; on a page rotated by 90° or 270° with upright text, real spaces and generated spaces it may glue words. No sample has such a page.
- **Right-to-left letters** come out reversed from PDFium (only the foreign-script specks of the OCR are affected).
- **A letter drawn twice** (pages 140, 156) is in the text twice, as PDFium reports it.
- **No table or footnote page** was among the pages looked at; the book has none.
- **A crash during the import** leaves a project with a page list and only some page files (T021 decision); the app does not offer to resume.
- **Progress text** shows pages done ("Importing page n of N"), and "Creating the project…" until the first page is done.
- Clausewitz (99 pages) was used for the analysis only, not for import tests.

### How to try it
1. `./gradlew run`, click "New project…", choose `samples/Abhandlung_über_den_kleinen_Krieg.pdf`, keep the suggested title, "Create".
2. The progress bar counts the pages; the project opens, and a notice says `5 pages have no text: 3, 139, 161, 163, 165.`
3. Try "Cancel" part way: the new `.korrektur` folder must be gone. Try it on a 1000-page PDF too: the window must stay responsive.
4. Look at the colour scheme (yellow/brown); this and the progress display are the parts only a person can judge.
5. The files: `<project>.korrektur/pages/0006.ocr.json` (the original, never changed) and `0006.json` (the corrected layer, a copy).
6. With the sample present: `./gradlew test --tests '*ImportSampleTest*'`; the 1000-page run: `KORREKTOR_PERF_TESTS=1 ./gradlew test --tests '*ImportFlowTest*'`.
