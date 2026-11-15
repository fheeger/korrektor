# T030 Load pages on demand, with a cache of limited size

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.5

## What needs doing
- **`PageCache`** between the viewer and `PageSource`: pictures are kept by key (page, scale, region); the least recently used are dropped when the total size (width × height × 4 bytes) exceeds a **budget in bytes** (default 256 MB, a constructor parameter). A single picture larger than the budget is returned but not cached.
- **On demand:** nothing is rendered for a page that is not shown, except the **neighbours**: after the current page has its picture, the next and the previous page are rendered at the same scale in the background (lowest priority, cancelled at once when the user navigates or zooms). Going one page forward or back is then instant.
- **Native pages:** page handles of the PDF are not kept longer than a render; the cache holds pictures, not native pages. Page sizes come from the page files, read on demand (reading one page takes about 10 ms, S01), with a small LRU of the layers in use.
- Pictures at a large zoom are cut to the viewport plus margin (T028), so one picture is at most a few viewports in size and the byte budget stays meaningful.

## Acceptance criteria
1. The cache never holds more than its budget (test with a small budget and many pictures); the least recently used go first; a hit makes an entry the most recent.
2. Showing page 5 does not render pages 7, 20 or 1000; after page 5 is there, pages 4 and 6 are rendered in the background and a switch to them hits the cache (fake source counts renders).
3. A navigation or zoom cancels the background renders of the neighbours and of the old page.
4. A picture larger than the budget is returned but not stored.
5. After switching through 200 pages of a generated book the memory held by the cache stays within the budget (the cache's own byte counter; the heap is checked in T031).
6. The cache is dropped when the project closes (no reference to the old project's pictures remains).

## Approach
- `PageCache` is plain Kotlin with an injected size function, tested with fakes. Neighbour prefetching is tested with a fake source and a test dispatcher.

## Dependencies
T029.

## Decisions
- **The cache is a `PageSource`** that wraps another one (`PageCache(source, budgetBytes)`), so the viewer does not know about it; `DefaultPageSourceFactory` returns `PageCache(PdfPageSource(...))`.
- **Prefetching is in the viewer, not in the cache:** the picture of a neighbour depends on the zoom mode (in fit width every page has its own scale) and on the viewport, which only the viewer knows. `ViewerState.preview(size, native)` makes a copy of the state for another page and `nextRequest(null)` on it gives exactly the request the viewer will make when that page is shown, so the cache key (page, scale, region) matches and the switch is a hit. A test checks that page 6 is rendered once only.
- **Only with a cache:** `PageSource.caches` (default false). For a source without memory the prefetch would be thrown away, so the viewer does not do it; this also keeps the tests with a plain fake source unchanged.
- **Cancelling:** the prefetch is an effect keyed on the page, the picture shown and the scale, so a navigation or a zoom restarts it. A render that PDFium is already doing cannot be interrupted, so a navigation may wait for one running neighbour render (some tens of milliseconds); the ones not started are never run. One neighbour at a time, the next page first.
- **Sizes come from the source,** and the cache keeps them (and the scan resolution) per page for good; there is no separate LRU of page layers (T029 decision).
- **Budget:** 256 MB, as in the ticket; one request is at most 30 million pixels (T028), so even then the cache holds at least two pictures of a very large screen. The heap with 1000 pages is measured in T031.
- **Thread safety:** the pictures map (an access-ordered `LinkedHashMap`) is changed under a lock that is not held while rendering. Two renders of the same key at once both run and the later one replaces the earlier (the counter is corrected); it only costs time.

## Progress
Done. `PageCache`, `PageSource.caches`, `ViewerState.preview`, the prefetch in `PageViewer` (the loading effects moved to `rememberPageLoad`), the cache in `DefaultPageSourceFactory`. Tests: 9 in `PageCacheTest` (a hit, the key, LRU order and a hit refreshing, the budget over 200 pictures, a picture larger than the budget, a large picture pushing out several, sizes asked for once, close, `caches`), 5 in `PageViewerCacheTest` (only page 5 and its neighbours are rendered of 1000, the first page has one neighbour, going to a neighbour is a hit, a zoom cancels the waiting neighbours, a source without a cache gets no prefetch). `./gradlew build` is green.

Not checked by me (story report): the real effect on page switching (T031 measures it).
