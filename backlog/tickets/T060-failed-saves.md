# T060 Failed saves: a warning, and saving is retried

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.5 (first part)

## What needs doing
- **Retry:** after a failed save, `AutoSaver.run()` tries again after `RETRY_DELAY_MS = 5000` (and sooner when the user changes something: the normal timer applies). It keeps trying as long as pages are unsaved. The changes stay in memory the whole time; nothing is dropped.
- **Warning:** while the last save failed, a banner shows at the top of the window: "Your changes could not be saved: <reason>. They are kept, and saving is tried again." It stays until the saving works again (it goes away by itself) or the user dismisses it; a later failure shows it again.
- The reason is the message of the exception, made readable: disk full, no permission, folder not found (an `IOException` from `Files.move`/`FileChannel` carries a file name; use the same `reason()` helper as the format package).
- When saving works again, the banner goes and the indicator (T061) says "Saved".
- **No endless noise:** one banner for any number of failed retries.

## Acceptance criteria
1. A failing writer: the first attempt fails, the banner text names the reason, the pages stay unsaved and in memory (UI test).
2. After 5 s the saver tries again; with the writer working again the pages are written, the banner is gone, nothing was lost (virtual time).
3. Five failed retries in a row show one banner, not five.
4. A change made while saving fails is kept and written together with the rest once saving works.
5. Dismissing the banner hides it until the next failure; saving continues.
6. A real failure: make the project's `pages` folder read-only / remove it (platform permitting), edit, and see the warning; restore it and see the pages saved (integration test, skipped if the file system cannot do it).

## Approach
`AutoSaver` (retry loop, `error`), `App` (banner), `SaverFailureTest` (fake writers, virtual time), one UI test.

## Dependencies
T058, T059.

## Decisions
(filled in while building)

## Progress
Not started.
