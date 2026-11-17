# T089 Search and replace tests: options, many pages with undo, speed in a 1000-page book

**Type:** task · **Status:** ready for development · **Story:** S10 · **Task:** S10.8

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

## Progress
