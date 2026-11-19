# T098 Export tests: the sample, both formats with options on and off, a Markdown renderer, speed in a 1000-page book

**Type:** task · **Status:** ready for development · **Story:** S11 · **Task:** S11.7

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
(none yet)

## Progress
Not started.
