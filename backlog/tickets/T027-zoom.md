# T027 Zoom: fit width, fit page, steps, Ctrl+wheel, native resolution

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.2

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
- **Native resolution** is `PdfPage.largestImageDensity()`: for the picture with the most pixels drawn directly on the page, `sqrt(pixels / (width in points × height in points))`, so it does not matter whether the picture is turned. The binding uses `FPDFPage_GetObject`, `FPDFPageObj_GetBounds` and `FPDFImageObj_GetImagePixelSize` (a simpler call than the metadata function of the ticket text, with the same result). Pictures inside form objects are not looked at; the Google Books pages draw the scan directly.
- **Limits:** `maxScale = max(native, actual size)`: a scan coarser than the screen can still be shown at 100 %. Without a scan: 8 × actual size. `minScale` is a tenth of the fit-page scale.
- **The fit modes are not limited by `maxScale`**: they fit the window. If a fit scale is above the maximum, zoom in does nothing and zoom out goes to the next step below; zooming in never lowers the scale.
- **The last zoom-in stop is the maximum itself** (between two steps if need be), so the scan's full resolution is always reachable.
- **The offset and the anchor are in this ticket** (the ticket text had them under pan): zooming around the pointer needs the position. T028 adds dragging, scrollbars, Shift+wheel and the rendering margin. The plain wheel already scrolls vertically here (a few lines), so that a zoomed page can be looked at before T028.
- **`wheel(notches, ctrl, x, y)`** is a function on `ViewerState`, tested with exact numbers. The test harness does not pass a held Ctrl key on to the pointer event, so the one line that reads the modifier (`isCtrlPressed`) is not covered by an automated test; the plain wheel is tested through the UI. To check by hand.
- **Rendering:** the visible region at the current scale (`state.visibleRegion`); a new request cancels the old one (`collectLatest`), and the old picture is drawn stretched to the new scale and position until the new one is there. A zoom beyond the stops is clamped, so a picture is at most the viewport in size (a 4K screen is 8 million pixels, below the limit of one image).
- **Toolbar:** "Fit width" and "Fit page" are filter chips (selected when active), "−" and "+" have the descriptions "Zoom out" and "Zoom in", the percentage is of actual size (96 dpi).
- **ViewerState** has ten functions (Detekt's limit): `zoomBy` and `wheel` are extension functions in the same file.

## Progress
Done. `PdfiumLib` image object functions, `PdfPage.largestImageDensity`, `PageSource.nativeScale`, `ViewerState`, the toolbar, the wheel and the region rendering in `PageViewer`. Tests: 6 in `ImageDensityTest` (no picture, known density, the largest of two, a rotated page, every pixel of a 400 × 400 checkerboard at the density is one screen pixel, the sample's scan is finer than the screen), 22 in `ViewerStateTest` (fit modes, steps, limits, anchor, centring, region, wheel), 8 more in `PageViewerTest` (toolbar and fit modes, zoom in and fit page ask for the new scale, the old picture stays, fast zooming renders only the newest scale to the end, the plain wheel scrolls), 1 in `PdfPageSourceTest`. `./gradlew build` is green.

Not checked by me (story report): how zooming feels in the real window, Ctrl+wheel (see Decisions), and the look of a zoomed scan.
