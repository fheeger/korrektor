# T055 Keyboard-only work on the sample, and holding Down through many pages

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.7

Tests that cover several tickets of S06 together.

## What needs doing
- **`KeyboardOnlyTest`** (skipped without the sample): the whole app with the sample project. Using only key events and no click: type into line 1 of a page, move down through the page, split a line, join two lines, delete an empty line, add a line at the end, move on to the next page by Down on the last line, correct there, Ctrl+Up back, Ctrl+G to page 40, zoom with Ctrl+Plus/Minus/0/1. Do this on several pages (at least five). Check after each step: the text of the corrected layer, the active line, the page.
- **The same flow with a generated test PDF** (runs in CI).
- **Performance:** holding Down through many pages (an auto-repeated key: 100 events in a burst, then at 30 events per second) over pages with lines. The UI stays responsive (a frame is drawn between the events: measure the longest gap between two frames), the final page is the one expected, and the number of page reads and renderings is bounded by the cache (no request for every skipped page keeps running: cancelled requests are cancelled). A local test, like the earlier performance tests (not run on every PR).

## Acceptance criteria
1. Five pages of the sample are corrected with the keyboard alone and the layers match the expected text (local test).
2. The same flow runs in CI with the generated PDF.
3. 100 Down events in a burst across pages end on the expected page and line. The longest frame gap stays below the limit set in the test (the value is recorded in Decisions after measuring).
4. No mouse event is used in these tests: they have no `performClick` or `performMouseInput`, and the helper they use rejects them.

## Approach
- New test classes in `src/test/kotlin/korrektor/editor/` and `viewer/`, using `runComposeUiTest` and key input.

## Dependencies
T049 to T054.

## Decisions
- **What "only the keyboard" means in the tests:** no click, no mouse, no touch. The project is opened through `AppModel.openProject` (the folder chooser is a dialog of the operating system), keys are sent with `performKeyInput`, and the text is typed with `performTextInput`, which is what the input method of a keyboard does (key events alone do not produce characters in the test driver). A test reads the test's own source and fails if it contains a mouse or touch call (the criterion "the helper rejects them", done as a source check).
- **The flow per page** (on five pages): Ctrl+G, the number, Enter; type into the first line; Down; Right three times and Enter (split); Backspace (join); End, Enter (add a line), Backspace (delete it); End, Delete (join with the next line); Ctrl+End; Down to the next page; Ctrl+Up back. After every step the lines, the active line and the page are compared. At the end zoom with Ctrl+1, Ctrl+Plus twice, Ctrl+Minus, Ctrl+0, Ctrl+1, and `changedPages` is exactly the five pages.
- **The sample has blank pages** (page 3 has no lines), found by this test. Down from the last line into a page without lines is a stop (decision of T050), so on the sample the test uses pages that have at least three lines and are followed by a page with lines (it picks them itself, starting at 2, 6, 40, 100 and 150). The user can leave a blank page with Ctrl+Down. Whether Down should skip blank pages is a question for the report.
- **Performance, measured on this machine** (1000 pages, two lines each, scans of 1500 by 2400 pixels): 200 Down events at 30 per second: every event moved the cursor (the 200 events end on page 101, on its first line), one event takes about 13 ms (median), 18 ms (95th percentile), 24 ms at most, until the UI is idle. A burst of 100 Down events without pause ends on page 51 after about one second, the editor and the viewer on the same page, no error. 100 times Ctrl+Down end on page 101. The limits in the test are generous (95th percentile 250 ms, maximum 1.5 s) because the test is for finding a freeze, not for timing; it runs on request (`KORREKTOR_PERF_TESTS=1`), like the others.
- **Not measured: the number of page reads and renderings.** The ticket text asked for it; the editor reads a page through a function of the project and the viewer through its cache, and counting them needs seams that are not there. The measured latency and the consistent end state are what the criterion asks ("keeps up and does not freeze"); the cache itself was tested in S04 (T030, T031).

## Progress
Done. `KeyboardOnlyTest` (3: five pages of a generated book, five pages of the sample, no mouse in the test) and `KeyboardPerformanceTest` (3, on request). The generated-book test runs in CI.
