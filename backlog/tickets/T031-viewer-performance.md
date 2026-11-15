# T031 Performance test on a 1000-page PDF

**Type:** task · **Status:** done · **Story:** S04 · **Task:** S04.6

## What needs doing
- A test with a generated 1000-page book (made in a temp folder each run, not stored; the generator of `ImportFlowTest` moves to `TestPdfs` and is shared; each page gets a large image so that rendering is not trivial, plus some text), opened in the viewer model with the real `PageSource` and the cache.
- Measured with `KORREKTOR_PERF_TESTS=1` (not on CI): the time from "go to page n" until its picture is ready, for the first page, for 50 random pages (no cache hit), for 50 neighbour steps (prefetched), and for a jump to page 1000. Median, 95th percentile and maximum are printed and recorded in the ticket.
- Assertions: the median of a page switch without cache hit is under 0.3 s (the target) and the maximum under 0.5 s (the story's limit). Switching back and forth over 200 pages keeps the heap within the cache budget plus a margin.
- The same on the sample PDF if present (165 pages, JPEG 2000 scans): it is the realistic case for page decoding; its numbers are recorded as well.
- If the targets are missed, this ticket investigates and fixes within the scope of the viewer (render size, bitmap copy, conversion to `ImageBitmap`).

## Acceptance criteria
1. With `KORREKTOR_PERF_TESTS=1` the test passes: median < 0.3 s and maximum < 0.5 s for page switches in the 1000-page book (cold) and in the sample.
2. The measured times and the machine are written into the ticket.
3. Heap use stays bounded over 200 page switches.
4. Without the variable the test is skipped.

## Approach
- `src/test/kotlin/korrektor/viewer/ViewerPerformanceTest.kt` driving the model, not the Compose UI; the conversion to `ImageBitmap` is part of what is measured by calling the same function.

## Dependencies
T030.

## Decisions
- **The test drives the models, not the Compose UI:** go to the page, learn its size, ask `nextRequest` for the picture of what is visible, wait for it from the real `PageCache(PdfPageSource)`. That is what the viewer does; what it leaves out is copying the finished picture's pixels to the screen, which takes a few milliseconds. The conversion to `ImageBitmap` is part of `PdfPageSource.render` and so is measured.
- **The generator is shared:** `TestPdfs.book(path, count, blankEvery, scanPixels)` replaces the private one of `ImportFlowTest`. With `scanPixels` every page draws the same JPEG (1500 × 2400 pixels, 20,000 dark marks on paper), stored once in the file (1.3 MB for 1000 pages), so each page has to decode and scale a real picture.
- **Window of the measurement:** 1400 × 900 pixels, fit width (a picture of the visible part plus a margin, about 1400 × 1350 pixels).
- **"Prefetched" is simulated** by rendering the next page's preview request into the cache before timing the switch, as the viewer does in the background; the prefetch of the viewer itself is tested in T030.
- **Memory:** the pixels of a picture are in native memory (Skia), so the JVM heap does not show them (it grew by 0 MB). The test therefore also reads the memory committed to the process: with a 64 MB cache it grew by about 190 to 230 MB over 200 switches, and by 193 MB over 800, i.e. it levels off and does not grow with use (the rest is PDFium's and the JVM's own buffers and the arenas of the allocator). The assertion allows the budget plus 300 MB; the exact bound on the pictures is the cache's own byte counter, which is checked after every switch.

## Results
Machine: AMD Ryzen 7 7700X (8 cores), 31 GB RAM, Windows 11, JDK 21, `KORREKTOR_PERF_TESTS=1`, 2026-11-15.

| | Generated book, 1000 pages | Sample, 165 pages (JPEG 2000) |
|---|---|---|
| First page (opens the PDF) | 49 ms | 58 ms |
| 48 random pages, nothing cached | median 43 ms, 95th percentile 44 ms, maximum 52 ms | median 66 ms, 95th percentile 75 ms, maximum 96 ms (40 pages) |
| 50 next pages, rendered in advance | 0 ms | 0 ms |
| Jump to the last page | 42 ms | 134 ms |

The target (median under 0.3 s) and the limit (maximum under 0.5 s) are met with a large margin. A page whose picture is already in the cache costs no time; the first switch after a long jump costs one render.

## Progress
Done. `ViewerPerformanceTest` (three tests, skipped without `KORREKTOR_PERF_TESTS`: the 1000-page book, the sample, and the memory over 200 switches), `TestPdfs.book`. `./gradlew build` is green (the performance tests are skipped there); with the variable all three pass.

Not checked by me (story report): the time from the click to the picture on the screen in the real window, which adds the drawing.
