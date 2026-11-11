# S09 Page status

**Features:** C5 · **Status:** ready for development

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
- [ ] S09.1 Add page status to the format, stored so that all statuses can be read without loading the pages
- [ ] S09.2 Show the status of the current page and the overall progress
- [ ] S09.3 Change the status: Ctrl+Enter (mark corrected and go to the next page), menu, button
- [ ] S09.4 Jump to the next or previous unchecked page
- [ ] S09.5 Hook into autosave (S07) and undo (S08)
- [ ] S09.6 Add the shortcuts to the shortcut list (S06.6)
- [ ] S09.7 Tests: set and undo statuses, jump between unchecked pages, progress in a 1000-page book
