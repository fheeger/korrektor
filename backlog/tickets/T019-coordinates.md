# T019 Coordinates of the format

**Type:** task · **Status:** in progress · **Story:** S03 · **Task:** S03.4

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
- **Page size = the visible page** (crop box within the media box, rotated), because that is what the user sees and what a rendering shows; the OCR boxes of the sample lie within it.
- **No clamping:** text outside the page keeps its coordinates, so nothing is silently moved.
- **Rounding:** `x`, `y`, `width` and `height` of a box are rounded one by one (like the codec does), not the edges. So a box can differ from the exact one by 0.1 in an edge.
- **The page size comes from the visible area, not from PDFium's page size call.** Both agree on every test page and on all 165 pages of the sample (checked), but the area is what the coordinates are relative to.
- **The oracle is a rendering.** The transform is checked by arithmetic worked out by hand (one test per rotation) and, independently, against PDFium's picture of the page: every word box must contain ink, and nearly all ink must lie in the boxes. The check is shown to be able to fail: boxes mirrored top to bottom or left to right do not pass.

## Progress
Done: `PageGeometry` (visible page size, `toFormat`), the spec's Coordinates section, `docs/architecture.md`, a `TestPdfs` extension (media box offset, crop box). 16 tests in `PageGeometryTest`: hand-computed values for 0°, 90°, 180° and 270°, one decimal, no clamping, invalid input; the rendering oracle on a plain page, with an offset media box, with a smaller crop box, rotated by 90°, 180° and 270°, and rotated with offset and crop box together (each with the mirrored-boxes self-test); on the sample: every page's size equals PDFBox's crop box, the first word of page 6 is where the content stream put it, and at least 90 % of the word boxes of page 6 contain printed ink.

I also drew the converted boxes over a rendering of sample page 6 and looked at it: the boxes sit on the printed Fraktur words and in reading order, including the line-end words with hyphens. The one OCR error visible there is `seinenOf`, a single box over two printed words (the analysis lists it). `./gradlew build` is green.
