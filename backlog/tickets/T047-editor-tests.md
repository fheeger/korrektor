# T047 Tests with the sample, and typing speed

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.8

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
None yet.

## Progress
Not started.
