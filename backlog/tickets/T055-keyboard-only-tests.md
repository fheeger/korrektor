# T055 Keyboard-only work on the sample, and holding Down through many pages

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.7

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
(filled in while building)

## Progress
Not started.
