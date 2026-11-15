# T026 Render a page region and show a page in the project screen

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.1

The viewer needs pictures of a page at any scale, and of any part of it, drawn from the project's own copy of the PDF. This ticket adds that, and shows one page (fit to the window width) in the project screen. Zoom, pan and navigation come in the next tickets.

## What needs doing
- **`PdfPage.renderRegion(scale, x, y, width, height)`** (in `korrektor.pdf`): an image of `width` × `height` pixels that shows the part of the page that starts at pixel (`x`, `y`) when the whole page is `pageWidth × scale` pixels wide. Implemented with PDFium's `FPDF_RenderPageBitmap` (the page is scaled to the full size and offset by `-x`, `-y`, so only the visible part is allocated). `render(widthPixels)` stays and is used for whole pages. Sizes that are not positive or too large for one bitmap (`MAX_BITMAP_PIXELS`) are refused.
- **Rotation and crop box:** the image shows the page as the user sees it, like the coordinates of the format (rotation and crop box applied). PDFium does this for rendering; a test makes sure it agrees with `PageGeometry`.
- **`PageViewer` composable** in `korrektor`: shows the picture of the current page, drawn from `project.sourcePdf`, fitted to the width of its area. For now the page is page 1 of the project.
  - The rendering runs on a background dispatcher (one thread, since PDFium takes one lock anyway, D02), never on the UI thread. While the picture is not there yet, the area shows "Loading…".
  - The picture is converted with `toComposeImageBitmap()` and drawn at 1 image pixel per screen pixel (density-aware: the size is asked in pixels, not dp).
- **`ProjectScreen`** shows the viewer next to the existing project information; the layout is a placeholder that S05 replaces with the side-by-side editor. The project's `PdfDocument` is opened when the project screen appears and closed when it goes away or the project changes.

## Acceptance criteria
1. `renderRegion` of the whole page at scale 1 equals `render(width)` (same pixels).
2. `renderRegion` of a part of a page equals the same part cut out of a render of the whole page at the same scale (a generated PDF with a known pattern; compared pixel by pixel, for a pattern with straight edges).
3. A rotated page (90°) and a page with a crop box are drawn as the user sees them: a generated page with a coloured corner has that corner where `PageGeometry` says.
4. Invalid arguments (zero size, too many pixels) fail with a clear message.
5. The project screen shows the first page of the sample project from the copy of the PDF inside the project; the UI thread is not blocked while it renders (a test with a slow fake renderer sees the UI free and shows "Loading…").
6. The PDF is closed when the project screen leaves the composition (no leaked handle: the project folder can be deleted afterwards on Windows).

## Approach
- Unit tests on generated PDFs (`TestPdfs`: add a helper for a page with a filled rectangle at a known place). The pixel comparisons are done on small pages.
- The viewer takes a `PageSource` interface (`suspend fun render(page, scale, region): ImageBitmap`) so UI tests use a fake; the real one wraps `PdfDocument` and a single-thread dispatcher.
- Not checked automatically (story report): how the page looks.

## Dependencies
None (S03 is done).

## Decisions
None yet.

## Progress
Not started.
