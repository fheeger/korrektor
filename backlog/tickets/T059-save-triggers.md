# T059 Save triggers: after 1 s without typing, at least every 10 s, on page switch

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.2

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
- **One loop with one wake-up channel:** `run()` waits for a signal (`changed()` or `requestFlush()` into a conflated channel), then waits for the deadline, then saves. While waiting, any new signal wakes it to recompute the deadline, which is `min(last change + 1 s, first unsaved change + 10 s)`; so a pause of a second ends the wait, and continuous typing is cut off at ten seconds. A flush ends the wait at once.
- **The "first unsaved change" is reset just before the save starts,** and a change that arrives during the save starts a new ten seconds. A change at the very moment of a save is either in that save (the snapshot is taken after the reset) or sets the new first-change time, so no change waits more than ten seconds (tested with exact numbers over 30 s of typing every 500 ms: saves at 10.0 s, 20.5 s and 30.5 s).
- **Nothing unsaved, nothing written:** a signal without unsaved pages (a flush on every page switch, a no-op) makes `saveNow` write nothing, and without signals the loop sleeps. Tested over 60 s of looking at pages, and on the real folder (no page file touched).
- **The clock is injected** (`now`, milliseconds) and the delays are coroutine delays, so the timing tests run in virtual time with exact numbers. In the app `now` is the system clock.
- **Wiring:** `AppModel` makes the `AutoSaver` with the project (`opened::writePage`) and connects `editor.onUnsaved`; `App` runs `saver.run()` in an effect keyed on the saver (cancelled when another project is opened) and asks for a flush when the shown page changes. Closing and switching projects flush in T062.
- **A flush on page change also happens on the first composition** (nothing unsaved then, so nothing happens).

## Progress
Done. `AutoSaver` (`changed`, `requestFlush`, `run`, `SAVE_DELAY_MS`, `SAVE_MAX_WAIT_MS`), `AppModel.saver`, the effects in `App`. Tests: `SaveTriggersTest` (8, virtual time: one edit after exactly 1 s, 30 s of typing, a pause, flush, flush with nothing unsaved, no changes, several pages in one save, a change during a save) and `SaveTriggersUiTest` (3, the whole window: a pause saves, a page switch saves at once, looking writes nothing).
