# T059 Save triggers: after 1 s without typing, at least every 10 s, on page switch

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.2

## What needs doing
- **`AutoSaver.run()`**, a suspend function that never returns (like `keepViewSaved`), run from an effect while a project is open. It waits for `editor.onUnsaved` (T057) and saves:
  - **1 s after the last change** (`SAVE_DELAY_MS = 1000`);
  - but **no later than 10 s after the first change that is still unsaved** (`SAVE_MAX_WAIT_MS = 10000`), so continuous typing is written at least every 10 s;
  - **at once** when asked to flush: `requestFlush()` (non-blocking), called when the page changes. Closing the project or the app flushes in T062.
- The clock is injectable (`now: () -> Long`, default `System.nanoTime`-based milliseconds), and delays use `kotlinx.coroutines.delay`, so tests run in virtual time (`runTest`).
- Changes during a save start the timer again; nothing is lost because the saved layer is compared by identity (T057).
- **Wiring in `App`:** `LaunchedEffect(saver) { saver.run() }`; `LaunchedEffect(shownPage) { saver.requestFlush() }`. `AppModel` makes the saver with the project and the editor (`AppModel.saver`), and connects `editor.onUnsaved`.

## Acceptance criteria
1. One edit is written 1 s later (not before: at 999 ms nothing is written).
2. Edits every 500 ms for 30 s: the first write is at 10 s after the first edit, and then at least every 10 s; never more than 10 s between a change and the next save start (virtual time, exact numbers).
3. A pause of 1 s ends the wait: edits, 900 ms pause, edit again: still one write, 1 s after the last edit.
4. `requestFlush()` writes at once; with nothing unsaved it writes nothing.
5. A page switch in the whole window (UI test) writes the page changed before it without waiting 1 s.
6. When there are no changes, nothing is written, ever (no periodic rewrite).
7. Several pages changed within the wait are written together in one save.

## Approach
`AutoSaver.run`, `requestFlush`, the wiring in `App.kt` and `AppModel`. Tests: `SaveTriggersTest` (virtual time with a fake writer), `SaveTriggersUiTest` (page switch in the window).

## Dependencies
T057, T058.

## Decisions
(filled in while building)

## Progress
Not started.
