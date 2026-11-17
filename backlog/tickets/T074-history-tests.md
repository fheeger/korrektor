# T074 History tests: across pages, after reopening, after a crash, speed

**Type:** task · **Status:** ready for development · **Story:** S08 · **Task:** S08.5

Tests that cover several of the tickets before. The tickets have their own tests; these use the whole path.

## What needs doing
1. **Across pages** (`UndoFlowTest`, a real project made from a generated PDF): edit on pages 1, 2 and 3, undo three times and redo twice, in the window and on the editor, and compare every page with what it was.
2. **After reopening**: edit, save, close, open, undo all the way and redo; the pages equal the states before. Also with the last changes on a page that is never shown in the second session.
3. **After a crash** (`HistoryCrashTest`, like `CrashTest` of S07: a child JVM is killed while typing and saving): the project opens, history and pages agree (recovery), undo works, every page decodes, and the pages equal what the history says. Run with 3 kills in CI and more with `KORREKTOR_PERF_TESTS=1`.
4. **Speed** (`HistoryPerformanceTest`, `KORREKTOR_PERF_TESTS=1`): a generated history of 100,000 steps over 1000 pages (about 30 MB): time to open the project and have undo ready, time of the first undo, of 100 undos in a row, of an undo on a page far away; the latency of typing with that history loaded. Measured numbers go into the story report.
5. **The sample book** (skipped without the PDF): import, edit 50 lines over 10 pages, save, reopen, undo everything; the pages equal the imported ones.

## Acceptance criteria
1. 1 to 5 pass.
2. Undo and redo take less than 50 ms each on the long history (typical few ms); opening the project with a 100,000-step history is not slower than without it by more than 100 ms.
3. After each killed run the history and the pages agree: a check reads the history and the pages and applies the steps.

## Approach
Test files in `src/test/kotlin/korrektor/history/`. Real folders, child JVM as in `autosave/CrashTest`.

## Dependencies
T070, T072, T073.

## Decisions
Written down while working.

## Progress
Not started.
