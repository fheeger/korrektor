# T044 Highlight the active line on the scan

**Type:** task · **Status:** done · **Story:** S05 · **Task:** S05.4

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
- **The viewer is given a `LineHighlight`** (the line id and its box), not a bare box, so the overlay can say which line it marks (its description, "Highlighted line: l2", is what the tests read) and so that the editor, not the viewer, decides what is marked.
- **`highlightFor(viewerPage)`** returns nothing while `editor.page` is not the viewer's page: when the page changes, the viewer moves first and the editor reads the new page a moment later; without this check the old line's box would be drawn for a moment over the new picture.
- **Order of drawing:** picture, highlight, word boxes: the outlines of the original word boxes stay visible inside the tint.
- **Colours:** fill `#FFD54F` at 40 % opacity, outline `#B26A00`, two pixels. Chosen to be seen on the dark Fraktur and the cream page alike (the screenshots show it on pages 6 and 50). The user may want other colours.
- **The word overlay now comes from the original layer** (`readOriginalPage`), as the story says; the T033 test that read the corrected layer now checks that the overlay shows the original's 5 words while the corrected file has 2.
- **What the screenshots show:** the highlight sits exactly on the line of the cursor on pages 6 (line 9) and 50 (line 11) with the cursor in the same row of the text panel. On page 50 the printed line "ſetzen ſoll, beſtehet darinn, daß man den" is **missing from the OCR layer** (the panel's line 9 is only a speck `"`): nothing to highlight there, and a line the user will add.

## Progress
Done. `LineHighlight`, `HighlightOverlay`, `PageViewer`/`ProjectViewer` take a highlight, `BookEditor.activeLine` and `highlightFor`, `App` wires them; the overlay reads the original layer. Tests (`HighlightTest`, 8, and the changed one of T033): the box of the active line is marked and nothing else (no active line, a line without a box, another page than the editor is on, the first half after a split by its box and not the second), the highlight is there when asked and gone when not, it is drawn where `toScreen` puts it and follows zoom and movement (pixel colours inside and just outside the box at two zooms), in the whole app clicking a row marks its line on the scan, another row moves the mark, and another page removes it, and an added line is not marked. `./gradlew build` is green.
