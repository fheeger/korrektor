# T029 Navigation: previous/next, go to page, "page n of N"

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.4

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
None yet.

## Progress
Not started.
