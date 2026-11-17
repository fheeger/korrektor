# T082 Fixes from the S09 code review

**Type:** task · **Status:** done · **Story:** S09 · **Task:** S09.8

The end-of-story review (Opus 5.5, whole story) found no high finding, two medium and several low ones.

## What was done
1. **Two threads wrote `project.json` (medium).** The saver now writes it too (statuses), and the metadata dialog on the UI thread; both used the same temp file and the older list could end up on disk. `Project` has one lock around "assign, encode, write, revert" for `saveProjectFile`, `updateMetadata` and `setPages`; `metadata` is `@Volatile`.
2. **Undoing Ctrl+Enter left no cursor (medium).** A status change has no place, so after Ctrl+Z on the page before, no line had the cursor and keys might have been lost. `ProjectKeys` now asks the editor to put the cursor in the first line of that page (`showPageOf`).
3. **Docs (low):** the format spec says that `"unchecked"` in `project.json` is read and written without the field, that history `status` changes name `"unchecked"`, and that a save writes the statuses before `saved`. `docs/architecture.md` was missing the S09 parts: added.

## Acceptance criteria
1. Metadata saves and status saves running at the same time both reach the file (a test with two threads fails without the lock).
2. Ctrl+Enter then Ctrl+Z through the keys: page 1 shown, unchecked, the cursor in its first line, and the keys still work (UI test).

## Decisions
- **Not changed (low):** the *Page* menu entries "Mark corrected"/"Mark as unchecked" are enabled even when they change nothing (Ctrl+Enter still moves on); a status step for a page above the book's last page is not rejected in the editor (it can only come from a damaged history; recovery and the progress ignore such pages); if the recovery cannot write `project.json`, the editor shows the old statuses and the first undo of that step says "does not fit" (same as for pages); Ctrl+Enter in the page field marks the page, it does not go to the typed page.

## Progress
Done. Build green. Tests: `ProjectStatusTest` (concurrent writes), `StatusUiTest` (undo through the keys).
