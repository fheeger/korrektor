# T098 Export tests: the sample, both formats with options on and off, a Markdown renderer, speed in a 1000-page book

**Type:** task · **Status:** done · **Story:** S11 · **Task:** S11.7

## What needs doing
- The sample PDF (skipped when missing): imported and exported in both formats with the options on and off. Checked on stable points (the number of pages and markers, the first and the last line, the number of lines), not against a stored copy of 165 pages.
- Expected files for a small generated book (3 pages with Fraktur characters, a line without a box, an empty page, hyphens at line ends), for all combinations of options in both formats.
- Markdown through a CommonMark renderer: for every line of the book the stored text is what comes out.
- Speed: a 1000-page book exports in the background in a few seconds and a stand-in UI thread keeps ticking (limits on the time only with `KORREKTOR_PERF_TESTS=1`, like the search).
- A flow test: open a project, correct a line, export, the file has the correction and the project is unchanged.

## Acceptance criteria
Every criterion of S11 has a test or is listed in the report as checked by hand.

## Approach
`src/test/kotlin/korrektor/export/`. The book generator of the search tests (`withBigBook`) is reused.

## Dependencies
T097.

## Decisions
- The expected files of the small book (`src/test/resources/export/`, 16 files) were worked out by a separate small script that does not use the renderer, then read through by hand. `.gitattributes` pins them to LF so that a Windows checkout does not turn them into CRLF.
- The sample is not compared with a stored copy: the test works out the expected plain text again from the stored lines in the simplest way (a few lines of code), and reads the Markdown back with the strict CommonMark reader.
- The speed limits (5 s per export, 500 ms between two ticks of the UI stand-in) are only checked with `KORREKTOR_PERF_TESTS=1`. On the development machine a 1000-page export takes about 0.4 s and the UI stand-in waited at most 19 ms.

## Progress
Done. `GoldenBook.kt`, `ExportGoldenTest` (2 tests; 16 expected files), `ExportSampleTest` (3, skipped without the sample), `ExportFlowTest` (2), `ExportPerfTest` (1). With the sample: 165 pages, 4636 lines; 1000 pages as plain text 379 ms, as Markdown 330 ms. Build green.
