# T045 Click a line on the scan to put the cursor in it

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.5

## What needs doing
- **Hit test (pure, tested):** `ViewerState.pagePointAt(screenX, screenY)` is the inverse of `toScreen` (a point of the page in points, origin top left); `lineAt(point, lines)` returns the line whose box contains the point. If boxes overlap (stray words, neighbouring lines), the smallest box wins, and the first of equals. Lines without a box are never hit.
- **Click vs drag:** a click on the scan (the primary button pressed and released without moving beyond the touch slop) is a click; dragging still pans. `PageViewer` gets an `onClick: (xPoints, yPoints) -> Unit`.
- **Effect:** a click on a line makes it the active line and puts the cursor into its row in the text panel, at the character that corresponds to the click position (the horizontal position in the box as a fraction of the text length, rounded); the panel scrolls to it and the row gets the focus (`editor.focusRequest`). A click on no line does nothing.
- Clicks work at every zoom and with the page centred or scrolled.

## Acceptance criteria
1. `pagePointAt` and `toScreen` are inverses at several scales and offsets, also for a centred page.
2. `lineAt`: a point in one box gives that line; in two overlapping boxes the smaller; outside every box none; a line without a box is never returned.
3. A click (UI test, mouse press and release at a point of a line) makes that line active and gives its row the focus with the cursor near the clicked character (within one character for a line of uniform letters); a drag of more than the slop does not.
4. A click on empty paper changes nothing.
5. On the sample, for the lines of pages 2, 6 and 50: a click at the centre of each box lands on that line, except where another box lies inside it (stray words), which is listed in the ticket (T047 tests this on the whole sample pages).

## Approach
- `detectTapGestures` beside the drag handling, or a click detected in the drag handler (down, up, no movement). Pure functions first.

## Dependencies
T041, T043 (the `focusRequest`), T044.

## Decisions
None yet.

## Progress
Not started.
