# T046 Keep the active line visible by panning

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.6

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
None yet.

## Progress
Not started.
