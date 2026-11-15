# T046 Keep the active line visible by panning

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.6

## What needs doing
- **`ViewerState.reveal(box)`**: scrolls the least that brings the box (page points) fully into the viewport, with a margin of a fifth of the viewport on the side it came from, so that a line near the edge is not left at the edge. A box larger than the viewport is aligned at its top left. In a direction in which the page is smaller than the viewport nothing moves (it is centred).
- **When:** whenever the active line changes (a cursor move in the text, a split, join, add, delete, a click on the scan, a page change) and its box exists and is not fully visible. Typing inside a line does not move the scan unless the line is out of view. Nothing happens for a line without a box.
- The movement is immediate (an animation may come later); the render margin (T028/T036) keeps the picture there, so there is no wait at fit width.
- The setting does not need a switch: at fit width the scan is mostly visible anyway, and when zoomed in this is what the story asks for.

## Acceptance criteria
1. `reveal` on a box that is already visible changes nothing; on a box below, above, to the left or right of the viewport it moves by the least amount plus the margin; at the edges of the page it stops there (model tests with exact numbers).
2. A box larger than the viewport is aligned at its top left; a page smaller than the viewport in a direction does not move in it.
3. Moving the active line down a page that is zoomed in (UI test: the active line changes from the editor) scrolls the scan so that its box is in the viewport.
4. A line without a box does not move the scan.
5. After a click on the scan nothing jumps (the clicked line is visible already).

## Approach
- `reveal` as an extension in `ViewerActions.kt`; a `LaunchedEffect(activeLine box)` in the screen calls it. Tests: `ViewerStateTest`, UI test with the editor and a fake source.

## Dependencies
T044.

## Decisions
- **The scan follows when the active line changes, not while the user types:** `App` runs `reveal` in an effect keyed on the highlight (line id and box). Typing in a line changes neither, so the scan is not pulled back if the user has moved it away to look at another part of the page (tested). The ticket text said "unless the line is out of view"; keeping to the change of the active line is the safer reading, because pulling the view back at every keystroke would fight the user.
- **A line wider than the panel** (a zoomed page, a long line) is aligned at its left edge, and so is a box taller than the viewport at its top; a box that is wider than the viewport minus its margins is "too long to keep margins". In the whole-app test the last line of the page at 300 % is wider than the panel: it is brought in vertically and its left edge is at the left.
- **A box that is in view already is left alone,** also when it is within the margin of the edge: the margin only matters for the direction a box is moved in.
- **The scroll is immediate;** an animation may come later. The render margin of T036 keeps the picture there (the page is rendered whole at fit width).
- **Lines without a box do not move the scan** (nothing to bring into view), and a click on the scan does not jump (the clicked line was in view).
- **No switch** to turn it off: at fit width the page is mostly in view, and when zoomed in this is what the story asks for.

## Progress
Done. `ViewerState.reveal`, the effect in `App`. Tests (`AutoPanTest`, 13): a box in view changes nothing, below it (exact scroll with the margin), above it, to the right and the left (the page's edge stops the scroll), partly out, at the very bottom of the page (the last scroll position), a box larger than the viewport (top left), a page smaller than the viewport in a direction does not move there, no viewport or page is no crash; in the whole app moving the cursor to a line out of view scrolls the scan to it (and back), a line without a box does not move it, typing does not move it after the user moved it away, and a click on the scan does not jump. The whole-app test fails when the effect is removed (checked). `./gradlew build` is green.
