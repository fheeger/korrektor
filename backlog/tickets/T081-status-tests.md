# T081 Page status tests: undo, crash, 1000 pages

**Type:** task · **Status:** done · **Story:** S09 · **Task:** S09.7

## What needs doing
Tests that cover several tickets (the ones of each ticket are in the ticket):
- Set and undo statuses across pages together with line edits, after reopening the project and after a simulated crash.
- The real child JVM crash test (S08) also flips statuses; after every kill the statuses and pages equal a replay of the history.
- A 1000-page project: opening it and counting the progress reads no page file, and takes little time; jumps between unchecked pages are instant (`KORREKTOR_PERF_TESTS=1` for the timing, the file reads are always checked).
- The sample book: mark 20 pages corrected, reopen, check the progress and the jumps.

## Acceptance criteria
1. Each test above exists and passes; the crash test fails if a status is lost (checked once by breaking the recovery).
2. The progress of a 1000-page book is computed from `project.json` alone (a project without page files opens and counts).
3. The measured numbers are in the story report.

## Approach
`StatusFlowTest`, `StatusPerformanceTest` (history package), `HistoryChild`/`HistoryCrashTest`, `HistorySampleTest` pattern.

## Dependencies
T076–T080.

## Decisions

## Progress
Done. Tests: `StatusFlowTest` (corrections and statuses across pages undone in order after saving and reopening; 1000 pages without any page file: opened and the progress counted in 288 ms), the child JVM of `HistoryCrashTest` now also sets statuses (12 kills locally, the statuses equal a replay of the history each time; with the status recovery switched off on purpose the test fails at kill 1), `StatusSampleTest` (the sample: 20 pages marked, saved, reopened, progress "20 of 165 corrected (12 