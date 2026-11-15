# T033 Optional overlay: word boxes

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.8

## What needs doing
- A toggle "Word boxes" in the viewer toolbar (off by default, not remembered) draws the boxes of the words of the current page over the picture: a thin outline for each word and a stronger one for each line (like the overlays of the S03 spot check).
- The boxes come from the page's **corrected layer** (`project.readPage(n)`), in the coordinates of the format (points, origin top-left, after rotation), and are scaled and offset with the same transform as the picture, so they stay on the words while zooming and panning.
- The page file is read in the background (T029). A page without text has no boxes and shows nothing extra.
- Only boxes that intersect the viewport are drawn.

## Acceptance criteria
1. With the toggle on, the boxes of a page are drawn; with it off, nothing is drawn (UI test with a fake layer: the number of drawn rectangles equals the number of words in view).
2. The transform of the overlay equals the transform of the picture at several scales and offsets (model test: a word box in points maps to the same screen rectangle as the region of the picture it covers).
3. A page without text shows no boxes and no error.
4. Boxes outside the viewport are not drawn (count test).
5. Checked by eye on the sample (story report): the boxes sit on the printed words at fit width and at a high zoom.

## Approach
- A pure function `toScreen(box, scale, offset)` in `ViewerState`, tested directly; the canvas drawing is thin.

## Dependencies
T026, T028 (the transform with the offset), T029.

## Decisions
- **The boxes come through a `PageBoxSource`** that `PageViewer` takes (default: none), so the viewer needs neither a project nor the page files; `ProjectViewer` supplies one that reads `project.readPage(n)` on the IO threads and takes the boxes of the lines and of their words from all regions. It is the **corrected** layer (as in the ticket): after S05 the boxes follow what the user changed.
- **Only when switched on:** the page file is read when the toggle is on (and again on a page change), not for every page shown. A page file that cannot be read gives no boxes and no message: the overlay is an aid, the page is shown anyway.
- **The transform** is `origin + point × scale` (`ViewerState.toScreen`), the same one the picture uses: the format measures in points from the top left of the visible page after rotation, and so does the viewer. A pixel test confirms it with PDFium (a red rectangle on a generated page, its box computed as the import computes it, the middle of the overlay's rectangle is red in the picture rendered for the visible part, at three scales).
- **While the picture of the new scale is on its way** the boxes already move with the new scale, the picture follows within a moment. (The old picture is stretched in the meantime, so the two agree.)
- **The description** of the overlay node ("Word boxes: 3, line boxes: 1") says how many are drawn; it is what the tests read, and it helps screen readers little but harms nothing.
- **Colours:** words in the theme's primary (brown), lines in its error colour (red) so that the two differ at a glance; 1 and 2 pixel strokes. The user may want other colours (a loose ticket).
- **The toggle is a filter chip "Word boxes"** in the toolbar, after the zoom; `ViewerState.showWordBoxes` is plain state, not part of the saved view (tested).
- **The code is in its own file** (`BoxOverlay.kt`; Detekt allows eleven functions per file).

## Progress
Done. `PageBoxes`, `PageBoxSource`, `toScreen`, `visibleBoxes`, `showWordBoxes`, the overlay and the toggle, the provider in `ProjectViewer`. Tests: 6 in `WordBoxesTest` (placement with scale and scrolling, the transform at five scales and three positions, a centred page, only boxes in the viewport, the overlay is not part of the saved view, and the pixel test against a PDFium render), 5 in `PageViewerBoxesTest` (off until switched on and then the number of boxes in view, scrolling changes which are in view, a page without text, unreadable boxes are no error, the boxes are those of the corrected layer of a project and not of the original). `./gradlew build` is green.

Not checked by me (story report): how the boxes look on the real sample at fit width and at a high zoom (by eye).
