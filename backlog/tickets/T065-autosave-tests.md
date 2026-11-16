# T065 Crash tests, failed saves and speed

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.8

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
(filled in while building)

## Progress
Not started.
