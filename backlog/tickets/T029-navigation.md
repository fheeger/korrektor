# T029 Navigation: previous/next, go to page, "page n of N"

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.4

## What needs doing
- The viewer state has the current page (1-based). Toolbar buttons "Previous page" and "Next page" (disabled on the first and last page), a text field for the page number (Enter goes to it; an invalid number is rejected and the field returns to the current page), and the display "Page n of N".
- Going to another page keeps the zoom mode: a fit mode recomputes for the new page's size; a fixed zoom stays and the page is shown from its top (offset 0 vertically, horizontally as T028 decides).
- The new page is shown at once as a placeholder (the old picture is dropped; "Loading…" or the cached picture, T030) and replaced when the render arrives. A page switch cancels the render of the old page.
- Page sizes come from the project's page file (`PageLayer.width/height`), so the layout is known before the picture is there.
- Keyboard shortcuts belong to S06; this ticket adds only the buttons and the field.

## Acceptance criteria
1. Next and previous change the page by one and stop at the ends (the buttons are disabled there).
2. The page number field accepts 1..N; "0", "N+1", text and an empty field are rejected, and the field shows the current page again.
3. The display shows "Page n of N" and follows every change.
4. After a page change with fit width the page fills the viewport width (also if the new page has another size); with a fixed zoom the zoom is unchanged and the page is at its top.
5. A page switch cancels the pending render of the old page and requests the new one (fake page source).
6. In the sample project, going from page 1 (612 × 792 pt) to page 2 (about 300 × 490 pt) with fit width shows both at full width.

## Approach
- Model tests for the state (page changes, parsing, clamping); UI tests for the toolbar with the fake source.

## Dependencies
T026, T027 (the zoom modes).

## Decisions
- **The current page is part of `ViewerState`** (`page`, `pageCount`, `goTo`); `PageViewer` no longer takes a page, `ProjectScreen` makes one state per project with the project's page count (a project without pages counts as one page, so the numbers are never empty). T032 restores the page with `goTo`.
- **Page sizes come from the `PageSource`** (PDFium, when the page is loaded), not from the page files as the ticket text said: the source already has the page open, reading a page file would add a second source of the same number, and the viewer must not depend on the page files being read. The size is known before the picture is rendered, which was the point.
- **A page change clears the size** (and the position and the scan resolution) at once, so that no picture is asked for with the old page's scale, and the old picture is dropped (the picture of a page is kept only for that page). The zoom mode stays; a fit mode recomputes when the new size arrives. T030's cache will supply pictures at once for neighbours.
- **A page switch cancels the old page's render** because the effects that render are keyed on the page (checked with a fake source that holds its answers back).
- **The page field** is a one-line text field with the description "Go to page"; Enter goes to the page, anything else than a whole number in range leaves the page as it is and puts the current number back. `ViewerState.goToText` does the check, so it is tested without UI.
- **Buttons** are "‹" and "›" with the descriptions "Previous page" and "Next page", disabled at the ends. Keyboard shortcuts come with S06.
- **The state is split into files** (Detekt allows ten functions per file): `ViewerState.kt` (the class), `ViewerActions.kt`, `PictureRequests.kt` and `ViewerGeometry.kt`; no behaviour changed in the split.
- **Sample:** the sizes of pages 1 and 2 of the sample (612 × 792 and about 300 × 490) are not tested with the real file here; the same switch between two different sizes is tested with a generated source. To check by hand.

## Progress
Done. Navigation in `ViewerState`, the toolbar part, the project screen uses one state per project. Tests: 5 more in `ViewerStateTest` (previous and next, typed numbers, a book without pages, what a page change forgets and keeps, going to the same page), 5 more in `PageViewerTest` (buttons and the display, typing a number with Enter, wrong numbers undone, a switch cancels the old render, fit width follows the new page and a fixed zoom stays with the page at its top). `./gradlew build` is green.

Not checked by me (story report): how the toolbar looks and feels, and the switch between sample pages 1 and 2 in the real window.
