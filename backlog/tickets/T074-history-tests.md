# T074 History tests: across pages, after reopening, after a crash, speed

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.5

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
- **"The pages equal what the history says" is checked exactly,** not by a smell test: the pages as imported, with every change of every entry applied in the order of the file, must equal the page files after the project is opened (`HistoryCrashTest.replayed`). It found nothing wrong, and it would catch a lost, doubled or reordered entry.
- **The crash test uses 40 pages** so that a kill often lands between the history append and the `saved` entry: with 12 pages 11 of 12 kills found everything saved; with 40 every kill of the long run had 40 to 200 entries after the last `saved`, so the recovery did real work each time.
- **The child process does what `AppModel.open` does** (recovery, loading the history, the real saver with the history writer) and keeps changing text, splitting, adding lines, undoing and redoing until it is killed; the kills go on in the same project, so later runs recover earlier ones.
- **The 100,000-step history is made by the real editor on pages in memory** (about 6 s) and written as the saver would, so the pages on disk match the history and undo really applies: nothing is faked in the file.
- **Numbers, one run on the author's Windows machine** (`KORREKTOR_PERF_TESTS=1`): history of 100,000 steps over 1000 pages 33 MB; opening the project 58 ms (167 ms for the first open of the same project without history, in a cold JVM, so no difference can be claimed); the first undo 5.3 ms, on a page never shown in the session; 100 undos in a row p95 0.37 ms, max 2.1 ms; 101 redos p95 0.04 ms; 300 more undos, back into history that is read on demand, max 8.2 ms; typing p95 0.045 ms.
- **Sample book:** 50 corrections on 10 pages (typed, split, with a long s), saved, reopened, undone in 50 steps: every one of the 165 pages equal to the import.

## Progress
Done. Tests in `src/test/kotlin/korrektor/history/`: `UndoFlowTest` (undo and redo across pages with every page compared at every step; reopen, undo to the start and redo to the end, save and open again), `HistoryCrashTest` and `HistoryChild` (a real child JVM killed 3 times in CI, 12 times with `KORREKTOR_PERF_TESTS=1`), `HistoryPerformanceTest` (on request), `HistorySampleTest` (skipped without the PDF); `UndoUiTest` got the three-page flow in the real window (Ctrl+Z, Ctrl+Y over page changes). Build, tests, ktlint and Detekt are green.
