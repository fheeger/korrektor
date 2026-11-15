# T044 Highlight the active line on the scan

**Type:** task · **Status:** ready for development · **Story:** S05 · **Task:** S05.4

## What needs doing
- **The highlight:** the box of the active line (`editor.activeLineId`, its `box` in the current corrected layer) is drawn over the scan: a translucent yellow fill with a darker outline, with the same transform as the picture (`ViewerState.toScreen`, T033). A line without a box is not highlighted (and nothing else is).
- `PageViewer` takes a `highlight: Box?` parameter (page points) and draws it above the picture and below the word overlay's outlines; `ProjectScreen` passes the active line's box.
- **The word overlay (T033) shows the original OCR boxes** (the story says so): `ProjectViewer` reads them from `readOriginalPage` instead of the corrected layer, because edited lines have no word boxes and the overlay must not change while the user types. The tests of T033 that said "corrected layer" change to "original layer".
- The highlight follows zoom and panning (it is drawn from the state, not from the picture), and disappears when the page changes.

## Acceptance criteria
1. The active line's box is drawn on the scan at the place the transform gives (model test of the rectangle; UI test: the description of the highlight node says which line, or that there is none).
2. No highlight when no line is active, or for a line without a box (a line that was added); after a split the first half is highlighted by its box.
3. The highlight moves with zoom and scroll (the rectangle follows `toScreen` at several scales and offsets).
4. The word overlay comes from the original layer: editing a line does not change it (UI test: edit, the number of drawn word boxes is the same).
5. A pixel test with PDFium as in T033: the highlighted rectangle of a generated line lies on the printed shape.
6. By eye on the sample: the highlight sits on the right line on pages 2, 6, 50 (screenshots, story report).

## Approach
- A pure function for the highlight rectangle (`toScreen`) already exists; the canvas drawing is thin. Update `PageViewerBoxesTest` and `WordBoxesTest` for the original-layer boxes.

## Dependencies
T038, T041 (the active line), T033.

## Decisions
None yet.

## Progress
Not started.
