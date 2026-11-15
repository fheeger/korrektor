# T027 Zoom: fit width, fit page, steps, Ctrl+wheel, native resolution

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.2

## What needs doing
- **Zoom model** (`ViewerState`, plain Kotlin, no UI): the scale (screen pixels per PDF point), the zoom mode (fit width, fit page, or a fixed factor) and the operations: `fitWidth`, `fitPage`, `zoomIn`, `zoomOut`, `zoomBy(factor, anchor)` for the wheel, `actualSize`. A fit mode recomputes when the viewport or the page changes; a fixed zoom stays.
- **Fixed steps:** 25 %, 50 %, 75 %, 100 %, 125 %, 150 %, 200 %, 300 %, 400 %, 600 %, 800 % of actual size (1 point = 1/72 inch at 96 dpi, so 1 point = 4/3 pixels). Zoom in/out goes to the next step above or below the current scale. The upper limit is the native resolution of the scan (below).
- **Ctrl+mouse wheel** zooms in small factors (about 10 % per notch) and keeps the point under the pointer fixed. The plain wheel scrolls (T028).
- **Native resolution:** the largest useful scale is the one at which one screen pixel is one pixel of the scan. For the page's largest image the viewer reads the image's pixel size with PDFium (`FPDFPage_GetObject`, `FPDFImageObj_GetImageMetadata`: width and height in pixels), and `maxScale = imagePixelWidth / pageWidthPoints`. A page without an image uses 8 × actual size as the limit. The scale never goes beyond `maxScale`, and never below a floor of 10 % of the fit-page scale.
- **Region rendering at the right scale** (T026's `renderRegion`): the picture for the screen is always rendered at the current scale for the visible part (plus a margin, T028), never rendered once and stretched. While a new picture is being rendered, the old one stays visible (scaled to the new scale as a placeholder) and is replaced as soon as the new one is there. Fast wheel input is coalesced: only the newest scale is rendered to the end (older requests are cancelled).
- **Toolbar** above the page: "Fit width", "Fit page", "−", "+", and the current zoom as a percentage ("125 %"). Keyboard shortcuts for zoom belong to S06.

## Acceptance criteria
1. Fit width: the page fills the viewport width exactly; fit page: the whole page is visible and touches the viewport on its limiting side; both follow a changed viewport size.
2. Zoom in/out moves along the fixed steps and stops at the smallest step and at the native resolution; `zoomBy` around an anchor keeps the anchor's page position fixed (model test with exact numbers).
3. For a generated page with an image of known pixel size the limit is as computed; for a page without an image it is 8 × actual size; for the sample (if present) the limit is read from the scan and is more than 100 %.
4. At the maximum scale a pixel of the picture is a pixel of the scan: a generated page with a 4 × 4 checkerboard image, rendered at `maxScale`, shows each cell with its known colour at the cell centres and each cell at least 2 pixels wide.
5. The viewer always asks for a picture at the current scale: with a fake page source the test sees a request for the new scale after each zoom, and when many zooms arrive quickly only the last is rendered to the end.
6. The percentage in the toolbar shows the scale as a percentage of actual size, and "Fit width"/"Fit page" are marked when the mode is active.

## Approach
- Model: plain unit tests. Rendering: the fake `PageSource`. Native resolution: generated PDFs (an image of known pixel size) and, if present, the sample.
- New PDFium bindings in `PdfiumLib` for the image object functions; `PdfPage.largestImagePixelSize(): Pair<Int, Int>?`.

## Dependencies
T026.

## Decisions
None yet.

## Progress
Not started.
