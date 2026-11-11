# S06 Keyboard navigation

**Features:** C2 · **Status:** ready for development

As a reader of historical sources, I want to move between lines and pages with the keyboard
so that I can correct quickly without reaching for the mouse.

Everything in S04 and S05 can be done with the keyboard alone. Customizable shortcuts come later. Jumping to the next unchecked page is part of S09. The scan has no keyboard focus of its own: it follows the cursor (S05).

## Acceptance criteria
- Up/Down moves to the previous or next line, and Ctrl+Home/Ctrl+End to the first or last line of the page. The highlighted box on the scan follows the cursor.
- Down on the last line opens the next page with the cursor on its first line. Up on the first line opens the previous page with the cursor on its last line.
- Ctrl+Down/Ctrl+Up opens the next or previous page. Ctrl+G opens "go to page".
- Lines are edited with the usual text-editor keys:
  - Enter in the middle of a line splits it.
  - Enter at the end of a line adds a new empty line.
  - Backspace at the start of a line joins it with the line above.
  - Delete at the end of a line joins it with the line below.
  - Backspace on an empty line deletes it.
- Ctrl+Plus/Ctrl+Minus zooms in or out, Ctrl+0 fits the page and Ctrl+1 fits the width.
- Every shortcut appears in the menus and in a list you can open with F1.
- You can work through a whole page and move on to the next one without touching the mouse.
- Holding Down to move through many pages keeps up and doesn't freeze the UI (N1).

Note: on macOS (N3) Ctrl+Up/Down belongs to the system, so the page shortcuts will use a different modifier there. That is decided when the macOS version is built.

## Tasks
- [ ] S06.1 Define the shortcut set and check it for conflicts with standard Windows and text-editing keys
- [ ] S06.2 Move between lines, including into the next and previous page
- [ ] S06.3 Page shortcuts: next/previous page (Ctrl+Down/Up) and go to page (Ctrl+G)
- [ ] S06.4 Split, join, add and delete lines with the usual editor keys
- [ ] S06.5 Zoom shortcuts
- [ ] S06.6 Show shortcuts in the menus, plus a list opened with F1
- [ ] S06.7 Test: correct several pages of the sample PDF using only the keyboard
