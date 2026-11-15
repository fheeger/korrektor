# T030 Load pages on demand, with a cache of limited size

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.5

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
None yet.

## Progress
Not started.
