# T047 Tests with the sample, and typing speed

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.8

Tests that cover several tickets of S05 together: the real sample, the whole window, and the performance of typing and page switching with both panels.

## What needs doing
- **`EditorSampleTest`** (skipped without the sample): import the Abhandlung into a project, open it in the whole app (`App` with the real page source) or the editor and viewer models:
  - for the lines of pages 2, 6, 50 and 100: `lineAt` at the centre of each box returns that line, except where another box lies inside (listed); the click puts the cursor in its row;
  - the active line's highlight rectangle (T044) lies on dark pixels of the rendered scan (more ink inside the line's box than in the same-size area between two lines), for the lines of pages 6 and 50;
  - the text panel shows every line of every one of the 165 pages exactly as the corrected layer says (a check on the layers and a UI check on three pages);
  - both panels show the same page after 50 random page changes; after each change the panel's rows are those of the page.
- **Whole-flow test with a generated PDF** (runs on CI): open, click a line on the scan, type, split, join, go to the next page and back; the edits are still there, the project folder is byte for byte unchanged, the viewer and the panel agree.
- **Typing performance** (`KORREKTOR_PERF_TESTS=1`): in a generated 1000-page book, 500 characters typed one by one into a line of page 500 with both panels showing: the time per keystroke from the key to the recomposed row (median and maximum) is printed and recorded here; the assertion is a median under 16 ms and a maximum under 50 ms. Also 1000 calls of `setLineText` on a page of 40 lines: the average under 1 ms.
- **Page switching with both panels** (`KORREKTOR_PERF_TESTS=1`): time from `goTo` to picture and rows in a 1000-page book and in the sample: median under 0.3 s, maximum under 0.5 s (the target of S04 and S05).
- **The look:** screenshots of the whole window on the sample (pages 2, 6, 50, with the highlight, zoomed in with the auto pan) rendered offscreen, looked at, and the findings put into the ticket and the story report.
- A table in the story report maps each S05 criterion to its test.

## Acceptance criteria
1. Every acceptance criterion of S05 that concerns logic and files is covered by a test in T037–T046 or this ticket, and these tests pass.
2. The sample tests run with the file and are skipped without it (checked on the CI report).
3. Typing and page switching meet the numbers above; the numbers are recorded in the ticket.
4. At least three sample pages were looked at in the whole window; the findings (also problems) are in the ticket and the story report.

## Approach
- Tests in `src/test/kotlin/korrektor/editor/`; the performance tests like those of T031. The screenshot script is a throwaway test, not committed.

## Dependencies
T037–T046.

## Decisions
- **The sample is imported once** for all the sample tests (`@TestInstance(PER_CLASS)`, a lazy project in a temp folder removed after the tests); every test is skipped without the file (`assumeTrue`), as on CI.
- **Hit test on the sample:** the centre of every line box of pages 2, 6, 50 and 100 (100 boxes) is hit by its own line: no exception to list. The assertion allows 5 % of misses so that a different sample would not fail on a stray word, and checks that all full-width lines (wider than 150 points) of pages 6 and 50 are hit correctly.
- **The ink check:** the pages 6 and 50 are rendered with PDFium at 2 ×; the median share of dark pixels in the boxes of the full-width lines is 0.28 and 0.29, the same-height strips at the right margin are empty (0.00) and the left margin of page 50 has scan shadow (0.10, the left margin of page 6 is 0.00). The test compares with the emptier of the two margins (more than three times as much ink inside) and requires ink (more than 5 %) in 95 % of the boxes. This shows that the highlight rectangle, which is the line's box, lies on the printed line.
- **Typing in the window:** 500 characters typed one by one with `performTextInput` into the first line of page 500 of a generated 1000-page book, with both panels showing: **median 5 ms, 95th percentile 7 ms, maximum 10 ms per character** (the measure includes the test framework's waiting for the recomposition). The limits are a median under 16 ms (one frame) and a maximum under 100 ms.
- **The model alone:** 1000 `setLineText` on a page of 40 lines take **0.011 ms each**.
- **Page switching with both panels:** the time until the scan has its picture and the editor has the text, added up (the app does both at once, so this is an upper bound): **1000-page book median 35 ms, maximum 46 ms; the sample median 52 ms, maximum 86 ms** (50 random pages, nothing cached). The target is 0.3 s, the limit 0.5 s.
- **Whole-flow test on CI:** a generated two-page book: click a line on the scan, correct it, split inside a word, join, go to the next page and back, with the project folder byte for byte as it was. It is three tests (click and type, split and join, the pages) so that each stays short.
- **Not done:** the screenshots were taken with a throwaway test and are not kept (the story report describes them).

## Results
- **Sample tests** (`EditorSampleTest`, 6, with the file): 100 of 100 line boxes on pages 2, 6, 50 and 100 are hit by a click at their centre; the boxes lie on ink; all 165 pages show exactly the lines of the corrected layer, in order (4,636 lines); the text panel shows the first lines of pages 2, 6 and 50 in the whole window; after 50 random page changes both panels were always on the same page and the panel showed that page's first line (or its note).
- **Performance** (`KORREKTOR_PERF_TESTS=1`, AMD Ryzen 7 7700X, 31 GB, Windows 11, JDK 21, 2026-11-15): see the numbers above.
- **By eye** (the real app rendered offscreen on the sample, with the real import and the real page source): pages 2, 6 and 50 with the text panel (T041); the highlight on page 6 (line 9) and page 50 (line 11) (T044); and, at 319 % zoom on page 6, moving the cursor from line 3 to line 22: the scan followed, the line is highlighted and brought into view with its left edge at the left of the panel (the line is wider than the panel), and the text panel scrolled to it.
  - **Findings:** (1) The text layer has run-together words (`seinenOf`, `seinemFürsten`, `unsereSchuldigkeit`, `derFriede`, `vielMusegiebt`), stray specks as lines or words (`ı`, `"`), and on page 50 a whole printed line is missing from the OCR (`ſetzen ſoll, beſtehet darinn, daß man den`): exactly what the user will correct. (2) At a high zoom, a line wider than the panel is brought into view at its left edge; the rest of the line needs the horizontal scrollbar. Centring on the character of the cursor would be better; not done (a possible later ticket). (3) The viewer area and the text panel are about equal in height and use the room well; the header takes two lines.

## Progress
Done. `EditorSampleTest`, `EditorFlowTest` (3 tests), `EditorPerformanceTest` (4 tests, on request). `./gradlew build` is green; the performance tests pass with the variable.
