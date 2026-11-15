# T033 Optional overlay: word boxes

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.8

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
None yet.

## Progress
Not started.
