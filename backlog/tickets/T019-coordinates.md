# T019 Coordinates of the format

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.4

Convert word boxes from PDF coordinates (origin bottom-left) into the coordinates of the project format (origin top-left, one decimal), including rotated pages and page boxes that don't start at 0, 0. This also settles the question S01 left open: which box is "the page size".

## What needs doing
- `PageGeometry(bounds, rotation)` (package `korrektor.importer`), built from a `PdfPage`:
  - `width` and `height`: the size of the **visible page**, which is PDFium's page bounding box (the crop box within the media box), with width and height swapped for a page rotated by 90° or 270°. Rounded to one decimal.
  - `toFormat(RawWord box): Box`: `[x, y, width, height]` in points, origin at the top-left corner of the visible page, x to the right, y downwards, as seen when the page is shown. Handles all four rotations and a bounding box that does not start at 0, 0. The values are rounded to one decimal, because the model does not round and the codec does: a box with more decimals would change on the first save (S01 report).
  - Boxes are not clamped: text that lies partly outside the page keeps its coordinates.
- `docs/project-format.md`, section Coordinates: say that `width` and `height` are the visible page (crop box within media box, after rotation) and that boxes are in the same system.
- `docs/architecture.md`.

## Acceptance criteria
1. For an unrotated page, a word drawn at a known place gets the box a person would expect (top-left origin), within 0.1 point plus the rounding.
2. A page whose media box starts at, say, `[10 20 310 520]`, and a page whose crop box is smaller than its media box, give boxes relative to the visible page.
3. Pages rotated by 90°, 180° and 270° give boxes and page sizes as the page looks when shown (width and height swapped for 90° and 270°). This is checked against a rendering: black text is drawn, the page is rendered with PDFium, and the dark pixels lie inside the converted word boxes, which cover almost all dark pixels.
4. All returned coordinates have at most one decimal.
5. On the sample PDF (if present) the page size of every page equals the size from PDFBox's crop box, rounded to one decimal.
6. The spec states what the page size is.

## Approach
- A small affine transform per rotation; the pixel check in the tests uses `PdfPage.render` (T016) as an oracle independent of the transform's maths.
- Tests: generated PDFs for each case (`TestPdfs`), one sample-PDF test (page sizes).

## Dependencies
T016, T018.

## Decisions
- **Page size = the visible page** (crop box within media box, rotated), because that is what the user sees and what a rendering shows; the OCR boxes of the sample lie within it.
- **No clamping:** text outside the page keeps its coordinates, so nothing is silently moved.

## Progress
Not started.
