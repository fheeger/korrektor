# T089 Search and replace tests: options, many pages with undo, speed in a 1000-page book

**Type:** task · **Status:** done · **Story:** S10 · **Task:** S10.8

## What needs doing
Tests that cover several tickets (those of each ticket are in the ticket):
- A project of many pages: replace across all pages, undo, redo, save, reopen, check the text and that one more undo step is all there is; a simulated crash after the replace (the history is replayed, the text is the same).
- The real child-JVM crash test of S08 (`HistoryCrashTest`) also does a multi-page replace now.
- A 1000-page project: a whole-book search of a rare word and of a common one takes a few seconds, results arrive in batches, cancelling is immediate, and the UI thread is never blocked (`KORREKTOR_PERF_TESTS=1` for the timing, the batches and the cancel are always checked). Replace of 1000 matches over 1000 pages.
- The sample book: search `ſ`-words and a regex over the 165 pages, replace a word in all pages, undo.
- Page statuses stay as they are after a replace (S09).

## Acceptance criteria
1. Each test above exists and passes.
2. The measured numbers are in the story report.

## Approach
`SearchFlowTest`, `SearchSampleTest`, `SearchPerfTest` (search package), `HistoryChild`/`HistoryCrashTest`.

## Dependencies
T083–T088.

## Decisions
- The 1000-page book is generated straight into a project (`withBigBook`: 30 lines of 10 words per page, written with `writePage`), not imported from a PDF, which would take minutes; it is read from page files like a real book.
- "The UI thread is never blocked" is checked with a stand-in: the model's result dispatcher is a single thread on which a ticker (5 ms) runs, and the longest gap between two ticks while the search runs is measured. The limit (500 ms) is only asserted with `KORREKTOR_PERF_TESTS=1`; the gap is always printed.
- A simulated crash is a page writer that throws after the history was written (the saver writes history first, then pages, then the `saved` entry), and a new start on the same project.

## Progress
Done. `SearchBigBook.kt` (the generated book), `SearchPerfTest` (3), `SearchFlowTest` (3), `SearchSampleTest` (skipped on CI), and the child JVM of `HistoryCrashTest` now does a replace on all pages every sixth round (12 kills locally: the pages equal a replay of the history each time). Measured on this machine, a book of 1000 pages with 30 lines each: a whole-book search of a rare word (1000 hits) took 461 ms the first time, a common word (35172 hits) 207 ms; the stand-in UI thread waited at most 44 ms between two 5 ms ticks; a cancel after 1126 hits took 2 ms; replacing 1000 hits on 1000 pages took 403 to 465 ms as one step, undoing it 73 to 80 ms. The sample (165 pages): "Krieg" as a whole word has 14 hits (389 ms), replacing it by "Kampf" gave "Replaced 14 matches on 13 pages." and one undo put all back. Build green.
