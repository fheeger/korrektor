# T045 Click a line on the scan to put the cursor in it

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.5

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
- **`ScanLinks(highlight, onClick)`** replaces the `highlight` parameter of `PageViewer` and `ProjectViewer` (Detekt allows five parameters, and the two belong together: what the scan shows of the text, and what it tells back). The tests of T044 use it.
- **Click detection:** `detectTapGestures` on the same box as the drag. A tap that turns into a drag (beyond the touch slop, which the drag consumes) gives no click: tested with a real press, move and release.
- **The clicked position is given as a `PagePoint` in points** (the inverse of `toScreen`), so the viewer knows nothing of lines.
- **The cursor place** is a guess from the horizontal position in the line's box as a fraction of the text length. For text of one font that is within a character or two; the user moves the cursor from there. A line whose box is wider than its text (a trailing space, a wide speck) gives a cursor too far left; accepted.
- **Overlap:** the smallest box wins (a stray word inside a line, a small neighbour), ties go to the first line in reading order. The edge of a box belongs to it (the test of `110.0` and `110.1`).
- **A click while the editor is not yet on the viewer's page does nothing** (the same guard as for the highlight).
- **A click on empty paper changes nothing:** the active line stays; it does not clear it, so the cursor in the text is not lost by a stray click.
- The word overlay (T033) and the highlight do not take part in the hit test: only the lines' boxes of the corrected layer do, so the click follows the lines the user sees in the text panel.

## Progress
Done. `pagePointAt`, `PagePoint`, `ScanLinks`, `lineAt`, `cursorIndexAt`, `BookEditor.clickOnScan`, the tap in `PageViewer`, the wiring in `App`. Tests (`ClickOnScanTest`, 12): the screen position and the box position are inverses at five scales and several positions and for a centred page, `lineAt` (one box, the edges belong to it, the smallest of overlapping boxes and the first of equal ones, outside and lines without a box give nothing), `cursorIndexAt` (start, middle, end, nearest boundary, outside the box, no box, no text), a click gives the page point under the pointer and a drag gives none and pans, in the whole app a click on a line (a quarter into it) makes it active, focuses its row and puts the cursor at the guessed character, a click on empty paper changes nothing, and with the page zoomed and moved a click on the first line still lands on it, and a click while the editor is on another page does nothing. `./gradlew build` is green. By eye: the sample tests of T047.
