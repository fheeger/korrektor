# T031 Performance test on a 1000-page PDF

**Type:** task · **Status:** ready for development · **Story:** S04 · **Task:** S04.6

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
None yet.

## Progress
Not started.
