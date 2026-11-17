# S09 Page status

**Features:** C5 · **Status:** in review

As a reader of historical sources, I want to mark each page as unchecked or corrected
so that I know how far I've got and which pages still need work.

S09 lets you mark each page as unchecked or corrected and shows your progress. A page overview with thumbnails and further statuses (such as "needs attention") come later.

## Acceptance criteria
- Each page is either *unchecked* or *corrected*. After the import (S03), every page is unchecked.
- The status of the current page is shown next to "page n of N", for example "page 12 of 165 · corrected".
- The overall progress is always visible, for example "57 of 165 corrected (35 %)".
- Ctrl+Enter marks the current page as corrected and opens the next page.
- The status can also be changed in either direction through the menu or a button.
- Ctrl+Shift+Down jumps to the next unchecked page, and Ctrl+Shift+Up to the previous one.
- A corrected page stays corrected when you edit it later.
- Status changes are saved (S07) and can be undone (S08).
- The status is part of the project format (S01). Showing the progress doesn't require loading all the pages, even in a 1000-page book (N1).

## Tasks
- [x] S09.1 Add page status to the format, stored so that all statuses can be read without loading the pages → [T076](../tickets/T076-page-status-format.md)
- [x] S09.2 Show the status of the current page and the overall progress → [T078](../tickets/T078-show-status-progress.md)
- [x] S09.3 Change the status: Ctrl+Enter (mark corrected and go to the next page), menu, button → [T079](../tickets/T079-change-status.md)
- [x] S09.4 Jump to the next or previous unchecked page → [T080](../tickets/T080-jump-unchecked.md)
- [x] S09.5 Hook into autosave (S07) and undo (S08) → [T077](../tickets/T077-status-in-editor.md)
- [x] S09.6 Add the shortcuts to the shortcut list (S06.6) → the table entries are made in [T079](../tickets/T079-change-status.md) and [T080](../tickets/T080-jump-unchecked.md)
- [x] S09.7 Tests: set and undo statuses, jump between unchecked pages, progress in a 1000-page book → [T081](../tickets/T081-status-tests.md)
- [x] S09.8 Fixes from the code review → [T082](../tickets/T082-s09-review-fixes.md)

## Report

### What was built
- **Format** (T076): `PageStatus` (an open set: `unchecked`, `corrected`, others kept) and `pages[].status` in `project.json` (absent = unchecked), so the progress of a book needs no page file; `Project.statuses`, `setStatuses`; `StatusChange` in the undo history. Specified in `docs/project-format.md` with tested examples.
- **In the editor** (T077): `BookEditor.statuses`, `setStatus` is one undo step; undo and redo make status changes (and open that page); a save writes the statuses after the pages and before the `saved` entry; recovery after a crash sets the newest status of each page from the history; save elsewhere keeps an unsaved status.
- **Shown** (T078): the status next to "Page n of N" ("Page 12 of 165 · corrected") and a bar with the progress ("57 of 165 corrected (35 %)").
- **Changed** (T079): Ctrl+Enter marks corrected and opens the next page; Ctrl+Shift+Enter marks unchecked; a button in the bar (toggles, stays on the page); a new *Page* menu; the F1 list has them.
- **Jumps** (T080): Ctrl+Shift+Down / Up open the next / previous unchecked page (menu entries grey when there is none).
- **Tests** (T081) and **review fixes** (T082).

### Decisions
- Statuses live in `project.json`, not in page files (N1). A status change rewrites that small file.
- Replaying a status after a crash sets the newest `to` of each page: a status is a value, so it needs no number and can be done twice.
- Ctrl+Enter = "corrected and next" (as the story says); the button is "mark and stay". Ctrl+Shift+Enter marks unchecked, so that every menu entry shows a key.
- A status this version does not know is kept, counts as not corrected and is not "unchecked" for the jumps.
- Page-status step is the first non-line change in the history (the S08 model took it as planned).

### How each criterion was checked
- **Unchecked after the import, status next to the page number, progress always visible:** `StatusUiTest` (whole window), `ProgressTextTest`.
- **Ctrl+Enter, last page, button both ways, Ctrl+Shift+Enter, key in the page field:** `StatusUiTest` with real key events; `ShortcutsTest`, `MenusTest`, `ShortcutListTest`.
- **Jumps:** `UncheckedPagesTest` (also 1000 pages: 1000 searches in 65 ms), `StatusUiTest`.
- **Editing keeps the status:** `StatusUiTest`.
- **Saved and undone:** `PageStatusTest`, `StatusRecoveryTest` (crash points), `CloseTest` (save elsewhere), `StatusFlowTest` (corrections and statuses undone in order after reopening), `HistoryCrashTest` (real child JVM killed 12 times locally, 3 in CI, now also setting statuses; with the recovery of statuses switched off on purpose it fails), `StatusSampleTest` (the real sample: 20 pages marked, saved, reopened).
- **Format and 1000 pages:** `ProjectStatusTest`, `SpecExamplesTest`; `StatusFlowTest` opens a 1000-page project that has no page files at all and counts its progress (288 ms).

### Code review (Opus 5.5, whole story)
No high finding. Two medium fixed in T082: two threads could write `project.json` at once (the saver for statuses, the metadata dialog), now under one lock; after undoing Ctrl+Enter no line had the cursor. Docs fixed (low). Not changed (low): see T082.

### Known gaps
- The menu entries *Mark corrected* / *Mark as unchecked* are enabled when they change nothing.
- If the recovery cannot write `project.json`, the editor shows the old statuses and the first undo of that step says it does not fit.
- A status step for a page above the last one (damaged history only) is not rejected by the editor.
- Ctrl+Enter in the page field marks the page; it does not go to the typed page.
- Not tried by hand: the real window, menu bar and keyboard (Ctrl+Enter, Ctrl+Shift+Down/Up, the button and how the bar looks, the cursor after undoing Ctrl+Enter).

### How to try it
Open a project: the bar shows "0 of N corrected (0 %)" and the page number says "· unchecked". Press Ctrl+Enter: the page is corrected, the next one opens, the progress grows. Press Ctrl+Z: the first page is back, unchecked, with the cursor in its first line. Ctrl+Shift+Down jumps to the next unchecked page, Ctrl+Shift+Up to the previous one. Try the button and the *Page* menu. Close and reopen the project: the statuses and the progress are the same; Ctrl+Z still takes status changes back. Look at `project.json`: `"status": "corrected"` is on the pages you marked.
