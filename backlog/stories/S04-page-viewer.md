# S04 Page viewer

**Features:** C2, N1 · **Status:** in review

As a reader of historical sources, I want to view the scanned pages with zoom and pan, loading quickly even in a book with 1000+ pages
so that I can read the original comfortably while correcting.

## Acceptance criteria
- Pages are drawn from the copy of the PDF inside the project.
- The viewer shows one page at a time (no continuous scrolling).
- Zoom: fit width, fit page, fixed zoom steps, and Ctrl+mouse wheel. Zooming in shows the scan's full detail up to its native resolution. The page isn't drawn once at low resolution and then enlarged.
- Pan: when zoomed in, the page can be moved by dragging and with the scrollbars.
- Navigation: previous/next page, go to a page number, and a "page n of N" display.
- Pages load only when needed, and a cache of limited size keeps memory use under control.
- Switching pages takes less than 0.5 s in a 1000-page book (target: 0.3 s).
- The last page viewed and the zoom level are remembered for each project, in the app settings (not in the project format).
- Optional: an overlay showing the boxes around the OCR'd words can be turned on and off.

## Tasks
- [x] S04.1 Draw a single page from the project's PDF copy → [T026](../tickets/T026-render-region.md)
- [x] S04.2 Zoom: fit width, fit page, zoom steps, Ctrl+wheel, full detail up to native resolution → [T027](../tickets/T027-zoom.md)
- [x] S04.3 Pan when zoomed in → [T028](../tickets/T028-pan.md)
- [x] S04.4 Navigation: previous/next, go to page, "page n of N" → [T029](../tickets/T029-navigation.md)
- [x] S04.5 Load pages on demand, with a cache of limited size → [T030](../tickets/T030-cache.md)
- [x] S04.6 Performance test on a 1000-page PDF (<0.5 s, target 0.3 s) → [T031](../tickets/T031-viewer-performance.md)
- [x] S04.7 Remember last page and zoom per project in the app settings → [T032](../tickets/T032-remember-view.md)
- [x] S04.8 Optional: overlay showing word boxes → [T033](../tickets/T033-word-box-overlay.md)

## Report

### What was built
- **Rendering** (T026): `PdfPage.renderRegion(scale, region)` draws any part of a page at any scale (only that part is allocated); `PixelRegion`; a `PageSource` interface and `PdfPageSource` (PDFium on a thread of its own); the `PageViewer` composable.
- **Zoom** (T027): `ViewerState` with fit width, fit page, steps from 25 % to 800 % of actual size, Ctrl+wheel around the pointer, and the scan's own resolution as the upper limit (`PdfPage.largestImageDensity`). The picture is always rendered for the visible part at the current scale, never enlarged.
- **Pan** (T028): dragging (hand cursor), scrollbars where the page is larger than the window, the wheel; a margin of half a viewport is rendered so that small movements need no new picture.
- **Navigation** (T029): previous and next, "Page n of N", a field to go to a page (Enter; wrong numbers are undone). The current page is part of `ViewerState`.
- **Cache** (T030): `PageCache` (least recently used, 256 MB budget), and the next and previous page are rendered in the background once the page shown has its picture; navigating or zooming cancels this.
- **Performance test** (T031): 1000-page generated book and the sample, with the real source and cache.
- **Remembered view** (T032): page and zoom of each project in `%APPDATA%\korrektor\settings.json` (restored on open; saved after one second without change, when another project is opened and when the window closes). Nothing is written into the project.
- **Word boxes** (T033): a toggle draws the boxes of the words and lines of the corrected layer over the scan with the same transform as the picture.
- **Review fixes** (T034).

### Decisions
All in the tickets under "Decisions". The ones that shape the product:
- The viewer is built on a `PageSource` interface (`PageCache` wraps it); the project's page files are not needed to show a page. Page sizes come from PDFium (T029).
- The upper zoom limit is the scan's resolution, but at least actual size; a page without a scan zooms to 8 × actual size (T027).
- Prefetching lives in the viewer, because the neighbour's picture depends on the zoom mode and the window; `ViewerState.preview` gives the exact request, so the switch is a cache hit (T030).
- App settings are a JSON file in the user's config folder, keyed by the project folder's path; a damaged file or one from a newer version is moved aside as `settings.json.bad` (T032).
- `AppModel` owns the `ViewerState`; `ProjectScreen` takes a slot and `ProjectViewer` owns the page source (T032).
- The word-box overlay comes from the corrected layer, and is off at the start and not remembered (T033).

### How each criterion was checked
| Criterion | Tests |
|---|---|
| Pages are drawn from the copy of the PDF inside the project | `ProjectViewerTest` (the source is opened on the project's own copy; it is closed when the viewer goes, so the file can be deleted), `PdfPageSourceTest` |
| One page at a time | by construction: the state has one current page; `PageViewerTest` (a page change asks for the new page only) |
| Zoom: fit width, fit page, steps, Ctrl+wheel | `ViewerStateTest` (exact numbers), `PageViewerTest` (toolbar, zoom in/fit page ask for the new scale, the old picture stays, fast zooming renders only the newest). Ctrl+wheel through the pointer event: **not automated** (the test harness does not pass a held Ctrl key on); the model function is tested |
| Full detail up to native resolution, not enlarged | `ImageDensityTest` (the density of the scan; at the density every pixel of a 400 × 400 checkerboard is one screen pixel), `ViewerStateTest` (limits), `RenderRegionTest`; by eye on the sample at the maximum zoom (see below) |
| Pan: dragging and scrollbars | `ViewerStateTest` (edges, centring, scrollbar adapter), `PageViewerTest` (drag, scrollbars only where larger, a sideways wheel delta, re-rendering only outside the region) |
| Navigation: previous/next, go to page, "page n of N" | `ViewerStateTest`, `PageViewerTest` (buttons stop at the ends, typing a number, wrong numbers undone, a switch cancels the old render, fit width follows the new page) |
| Pages load on demand, bounded cache | `PageCacheTest` (budget, LRU, oversized pictures), `PageViewerCacheTest` (only page 5 and its neighbours of 1000 are rendered, a switch is a hit, a zoom cancels the neighbours) |
| Switching takes under 0.5 s in a 1000-page book | `ViewerPerformanceTest` (with `KORREKTOR_PERF_TESTS=1`): median 43 ms and maximum 52 ms in a generated book, median 66 ms and maximum 96 ms in the sample, a prefetched page 0 ms; memory levels off |
| Last page and zoom remembered per project, not in the format | `AppSettingsTest`, `ViewRestoreTest` (restart, two projects, damaged and newer files, the project folder byte for byte unchanged). Saving on window close: **not automated** |
| Optional overlay of word boxes | `WordBoxesTest` (the transform, only boxes in view, a pixel test against a PDFium render), `PageViewerBoxesTest` |

**Looked at by eye** (the real app rendered offscreen on the sample, with the real import): page 6 at fit width, with the word boxes, at fit page and at the maximum zoom (625 %). The boxes sit on the printed words and lines; the fit-page view centres the page; at the maximum zoom the scan shows its own pixels sharply (the sample's pages are bilevel).

**Code review** (Opus 5.5, whole story): no blocker, but real bugs. Fixed in T034: a freeze when the viewer area has more than 30 million pixels (an 8K screen), Shift+wheel and sideways scrolling that did nothing in the real window (Compose reports a horizontal delta, the code read only the vertical one), a crash on very small pages (an empty `coerceIn` range), a debounced save of a view the user had already left, unreadable settings that the next save would have overwritten, an fsync on the UI thread, and docs. The new tests failed on the old code. Not fixed: see the known gaps. Areas the review found fine: the PDFium binding (struct offsets, native memory, the lock), the cache accounting, the page-source lifecycle, the effect keys and cancellation, zoom and anchor arithmetic, the overlay's transform, the unchanged project format.

**Build:** `./gradlew build koverXmlReport` green (Detekt and ktlint without findings); the performance tests are skipped there and pass with the variable. Uncovered lines of the new code are small: an interface default and a lambda, and the warning paths of the settings.

### Known gaps and uncertainties
- **The layout is a placeholder:** the metadata block takes most of the room above the viewer. S05 replaces it with the side-by-side editor.
- **No keyboard shortcuts** (S06): navigation and zoom are mouse only.
- **Ctrl+wheel and saving at window close** are wired but not covered by an automated test (see above); to check by hand.
- **Stray OCR specks** widen some line boxes in the overlay (seen on page 6 line 7, as in S03).
- **The go-to field** is taller than the buttons (default text field height); a look-and-feel point for you.
- **Settings are keyed by the absolute path** of the project folder: a moved project, or the same folder under another spelling (case on Windows), starts afresh. Two running instances of the app overwrite each other's settings (last writer wins).
- **A restored or carried-over fixed zoom is not cut to the scan's resolution** of a coarser page; it stays usable.
- **Page sizes come from PDFium**, not from the page files as the ticket text first said (T029).
- **Pictures at a large zoom:** one request is at most 30 million pixels; a very large screen at a high zoom needs the memory of that picture (the cache counts it).
- **Hardware:** timings are from one machine (Ryzen 7 7700X); a slower machine and a real JPEG 2000 scan at a higher resolution may be slower. The sample's pages are bilevel JPEG 2000 and were measured.

### How to try it
1. `./gradlew run`, create a project from `samples/Abhandlung_über_den_kleinen_Krieg.pdf` (or open one). The scan appears below the metadata.
2. Zoom with "Fit width", "Fit page", "−", "+" and Ctrl+mouse wheel (the point under the pointer stays put); zoom in until it stops: that is the scan's own resolution, and the text should look sharp.
3. Move the page by dragging, with the scrollbars, the wheel (up and down) and Shift+wheel (sideways).
4. Go to other pages with the arrow buttons and by typing a number and pressing Enter; try a wrong number. Jumping far and stepping to the next page should feel instant; the first page after a jump takes a moment.
5. Click "Word boxes": every word and every line gets a box; they must stay on the words while you zoom and move.
6. Set another page and zoom, close the window, start the app and open the project again: it comes back to that page and zoom. Look at `%APPDATA%\korrektor\settings.json`; the project folder has no new file.
7. Performance: `KORREKTOR_PERF_TESTS=1 ./gradlew test --tests '*ViewerPerformanceTest*' -i` prints the times.
