# T065 Crash tests, failed saves and speed

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.8

Tests that cover several tickets of S07 together.

## What needs doing
- **Crash while typing:** a child JVM (a test class with a `main`) opens a generated project, runs the real `AutoSaver` with the real clock, and edits one line every 50 ms with a counter (`edit 1`, `edit 2`, …), printing `<n> <time>` to its output for every edit. The test kills it with `destroyForcibly` at a random moment, 10 times. Then the project is opened: it opens, every page decodes, the page text is the state of some edit `k ≤ n`, and the time between edit `k` and edit `n` is at most 10 s (plus a small slack for the process start).
- **Crash while saving:** the same with 40 pages being changed and saved over and over (so that most kills land inside a save), 10 kills at random moments. After each: the project opens, every page file decodes, no page has a mix of two states, and leftover `*.tmp` files are removed by opening.
- **Failed saves** (integration): the project's `pages` folder is made unusable while the app model has unsaved changes, the warning shows, the folder is restored, and the changes are written; closing with a failing save shows the dialog (T062). Skipped if the platform cannot make the folder unwritable.
- **Whole flow in the window** (generated book): type, switch the page, type, close (`requestClose`); reopen the project: the corrections are there; the original OCR layers and `source.pdf` are byte for byte unchanged.
- **Speed (on request, `KORREKTOR_PERF_TESTS=1`)** in a 1000-page book: (a) the time `saveNow` takes for 1 changed page and for 50 changed pages; (b) typing while saving: the latency of `setLineText` and of a key press in the window while a save of 50 pages is running (no more than 20 ms longer than without a save); (c) that opening a project and typing do not read or write the other 999 pages (count the writes through the writer).
- The numbers measured are written into this ticket's Decisions.

## Acceptance criteria
1. 10 of 10 kills while typing: the project opens, the loss is at most 10 s (+ slack), the state is a real earlier state.
2. 10 of 10 kills while saving: the project opens, no page file is half written, no mix.
3. The failed-save flow works end to end with a real unwritable folder where the platform allows it.
4. The window flow keeps the corrections and leaves originals unchanged.
5. The speed numbers are recorded; typing during a save is not slower by more than 20 ms (95th percentile).

## Approach
`src/test/kotlin/korrektor/autosave/` with `AutosaveChild.kt` (the child's `main`), `CrashTest`, `AutosaveFlowTest`, `AutosavePerformanceTest`. The crash tests take a minute: they run in CI only if they stay under a few seconds each, otherwise on request (decide when measured, record it).

## Dependencies
T057 to T064.

## Decisions
- **Crash tests run in CI in a short form** (3 kills each, about 12 s in all); `KORREKTOR_PERF_TESTS=1` makes it 10 kills each, including one 12 s run of continuous typing. The child JVM is `AutosaveChild.kt` (real `AutoSaver`, real clock, prints `<counter> <time>`).
- **Failed-save and window-flow tests** (`AutosaveFlowTest`) run in CI. The "unusable folder" is the `pages` folder moved away and back, which works on every platform (no need for read-only permissions).
- **Speed tests** (`AutosavePerformanceTest`) run on request only. The typing test saves 500 changed pages, not 50, because a save of 50 pages takes only about 50 ms, too short to type during it.
- The first version of the typing test hung: its `async` ran on the thread that the measuring loop kept busy. The save now runs on `Dispatchers.Default`. This was a test bug, not an app bug.

## Progress
Done. Measured (1000-page book, this machine):
- Typing kills: the loss was 0.56 to 2.6 s; in the 12 s run of continuous typing about 2 s after the ten-second save. The limit is 10 s.
- Saving kills: 8 of 10 left a `*.tmp` file, so the kills did land in the middle of a write. Opening the project removed them, and every page decoded with one whole state.
- Save of 1 changed page: 7 to 9 ms. Save of 50 changed pages: 47 to 52 ms (about 1 ms a page).
- Typing (`setLineText`): 95th percentile 21 µs without a save, 33 µs during a save of 500 pages (236 edits), limit +20 ms.
- Only the changed page is written: looking at pages and saving with nothing changed write nothing; one edit writes one page of 1000.
