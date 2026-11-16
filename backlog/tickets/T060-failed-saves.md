# T060 Failed saves: a warning, and saving is retried

**Type:** task · **Status:** done · **Story:** S07 · **Task:** S07.5 (first part)

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
- **The retry lives in `run()`:** after a failed save the loop waits `RETRY_DELAY_MS` (5 s), or less if the user changes something (then the normal timer of a second applies, then the save), or flushes (at once); then it saves again, and again, as long as it fails. It stops retrying only when a save works (or nothing is unsaved any more).
- **One warning:** `AutoSaver.warning` is the reason while saving fails, however many retries fail (tested over 26 s, six attempts, one banner). It is gone as soon as a save works, by itself. Dismissing (`dismissWarning`) hides it; it stays hidden for the retries that follow and comes back only when a save has worked and then failed again.
- **The banner** is the existing `ErrorBanner` below the other banners, with the text "Your changes could not be saved: <reason>. They are kept, and saving is tried again." (test tag `save warning`). The reason comes from T058 (`disk full`, `<file>: access denied`, ...).
- **A change during a failing save** is kept like everything else and written with the rest once saving works; because it starts the normal timer, the retry for it comes after a second, not five.
- **`AppModel` takes a `pageWriter`** (default `Project::writePage`) so that a UI test can make writing fail, as `projectFactory` does for the import. `AutoSaver` and `run()` need nothing else for tests.
- **The reason on a real folder:** a test moves the `pages` folder away (as if the disk had gone) and back: the first save fails with the changes kept, the next works.

## Progress
Done. `AutoSaver` (retry loop, `warning`, `dismissWarning`, `RETRY_DELAY_MS`), `AppModel.pageWriter`, the banner in `App`. Tests: `SaverFailureTest` (6, virtual time: kept and warned; retry after 5 s and success; one warning for six retries; a change during failure; dismiss and come back; flush after failure), `SaveWarningUiTest` (1, the whole window with a failing writer: banner with the reason, correction kept, dismiss, saved when it works again), and one more in `AutoSaverTest` (the folder gone and back).
