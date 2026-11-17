# S06 Keyboard navigation

**Features:** C2 · **Status:** done

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
- [x] S06.1 Define the shortcut set and check it for conflicts with standard Windows and text-editing keys → [T049](../tickets/T049-shortcut-set.md)
- [x] S06.2 Move between lines, including into the next and previous page → [T050](../tickets/T050-line-navigation.md)
- [x] S06.3 Page shortcuts: next/previous page (Ctrl+Down/Up) and go to page (Ctrl+G) → [T051](../tickets/T051-page-shortcuts.md)
- [x] S06.4 Split, join, add and delete lines with the usual editor keys → [T052](../tickets/T052-line-edit-keys.md)
- [x] S06.5 Zoom shortcuts → [T053](../tickets/T053-zoom-shortcuts.md)
- [x] S06.6 Show shortcuts in the menus, plus a list opened with F1 → [T054](../tickets/T054-menus-and-shortcut-list.md)
- [x] S06.7 Test: correct several pages of the sample PDF using only the keyboard → [T055](../tickets/T055-keyboard-only-tests.md)
- [x] S06.8 Fixes from the code review → [T056](../tickets/T056-review-fixes.md)

## Report

### What was built
- **The shortcut table and one key handler** (T049): `Shortcuts` lists every action with its keys, label and description; `Modifier.shortcutKeys` is the only place that reads shortcut keys. It sits above the open project, so a shortcut works from a text row, the page field and a button, and the focused element never sees it.
- **Lines** (T050): Up/Down move the cursor by one line and keep the column, Ctrl+Home/Ctrl+End go to the first and last line; on the first or last line Up/Down open the previous or next page with the cursor on its last or first line. The yellow box on the scan, the list and the panning follow as in S05.
- **Pages** (T051): Ctrl+Down/Ctrl+Up open the next and previous page with the cursor on its first line; Ctrl+G moves to the page field with its content selected, Enter opens the page, a wrong number is undone, Escape returns to the text.
- **Editing keys** (T052): Backspace on an empty line deletes it (also the first and only line of a page); Enter, Backspace and Delete as before are now tested together with real key events.
- **Zoom** (T053): Ctrl+Plus (also Ctrl+= and Ctrl+Shift+=, numpad plus), Ctrl+Minus, Ctrl+0 (fit page), Ctrl+1 (fit width).
- **Menus and the list** (T054): menus *Go*, *View* and *Help* with every shortcut shown, and F1 opens a list of all shortcuts and of the keys of a line. README and `docs/architecture.md` are updated.
- **Tests** (T055) and **review fixes** (T056).

### Decisions
All in the tickets under "Decisions". The ones that shape the product:
- **One handler, one table.** The menus, the F1 list and the key handler are made from `Shortcuts`; a test fails if an action has no key, no menu entry or no handler. Alt and Meta are never part of a shortcut: on Windows AltGr is Ctrl+Alt, and `@`, `€` or `{` on a German keyboard must not trigger one.
- **The menu entries show the key but do not register an accelerator** with the menu bar, because I could not check that Swing's accelerator and the Compose handler would not both act on one key press. A native right-aligned accelerator column is possible later.
- **Keys need a focus.** A key event only reaches the handler if some element has the focus. The text panel therefore takes the focus at every page change and when a page has no lines, until a row takes it (the cursor arriving).
- **Where the cursor lands:** after Up/Down across a page boundary, on the last/first line; after Ctrl+Up/Down and "go to page", on the first line. It never stays in the page field or nowhere.
- **A page without lines is a stop** for Up/Down (the sample has such pages, for example page 3). Ctrl+Down/Up leave it.
- **An arrival belongs to its page** and is used once, so a cancelled page read (the next key came first) or a page change with the mouse cannot put the cursor somewhere unexpected.
- **Backspace on an empty line deletes it** instead of joining: an erased line may still carry a box that a join would add to the line above.
- **The shortcuts are fixed** (customizing comes later), and on macOS the page shortcuts need another modifier (decided with N3).

### How each criterion was checked
| Criterion | Tests |
|---|---|
| Up/Down, Ctrl+Home/End move the cursor, the box on the scan follows | `LineNavigationTest` (the model), `LineNavigationUiTest` (the whole window, real keys, the highlight) |
| Down on the last line opens the next page on its first line, Up on the first the previous page on its last | `LineNavigationUiTest`, `LineNavigationTest` (the arrival), `KeyboardOnlyTest` |
| Ctrl+Down/Up open the next/previous page, Ctrl+G opens "go to page" | `PageShortcutsUiTest` (both directions, the ends, Ctrl+G, Enter, wrong numbers, Escape) |
| Enter splits / adds a line, Backspace joins / deletes an empty line, Delete joins | `EditKeysUiTest` (real key events, also the keys that must stay with the field), `LineOperationsUiTest` (S05) |
| Ctrl+Plus/Minus, Ctrl+0, Ctrl+1 zoom | `ZoomShortcutsUiTest` (against the buttons, the alternatives, the limits) |
| Every shortcut in the menus and in the list on F1 | `MenusTest`, `ShortcutListTest` (every action of the table appears once; no action without a handler) |
| A whole page and the next one without the mouse | `KeyboardOnlyTest`: five pages of a generated book (in CI) and of the sample (locally), with a check that the test source has no mouse call |
| Holding Down through many pages keeps up (N1) | `KeyboardPerformanceTest` (on request): 200 Down events at 30 per second over 1000 pages end on the expected page, **13 ms median, 24 ms maximum** per event; a burst of 100 events ends on page 51 in about a second, editor and viewer on the same page |
| The key table is consistent, no conflicts | `ShortcutsTest`, `ShortcutsUiTest` (once from a row, the page field and a button; used up; non-shortcuts left to the field) |

**Code review** (Opus 5.5, whole story): five findings and some cleanups, none of them lost data (nothing is written). Fixed in T056: **AltGr** (`}` and `~` on a German keyboard) could have been taken for Ctrl+0 and Ctrl+Plus, because the JDK may report it as Ctrl plus AltGraph without Alt, so the character could not be typed; a **Down right after a page change** was worked out on the old page and skipped a page (the viewer changes the page at once, the editor a frame later); a **stale arrival** (a wish for the cursor on a page that was never shown) could put the cursor into a page opened later with the mouse; **Escape/Enter in the page field on a page without lines** left the focus in the field; the **column was not sticky** over a short line. Cleanups: an unused enum value, a doubled number parser, an outdated test comment, and the architecture document, which had not been committed yet.

**Build:** `./gradlew build koverXmlReport` green (Detekt and ktlint without findings). The new code is covered except the dialog wrapper of `ShortcutList` (3 lines), a few lines of the page field's layout, and `Main.kt` (the menu bar, which cannot be driven by the test framework).

### Known gaps and uncertainties
- **AltGr is fixed in the code but not tested:** the test framework cannot make a key event with the AltGraph modifier. Please try `@`, `€`, `{`, `}`, `~` in a line on your keyboard (step 5 below).
- **Not checked by a test: the real keyboard and the real menu bar.** The tests send key events; what a German layout or another keyboard does with Ctrl+Plus, and whether a character is typed on top of the shortcut, needs a look by hand (see "How to try it"). The same for the native menu bar and for the focus coming back to the line after the F1 dialog is closed.
- **Blank pages stop Up/Down.** The sample has pages without lines (3, 139, 161, 163, 165). Down from the last line of page 2 opens page 3 and stays there; Ctrl+Down goes on. Skipping blank pages with Up/Down would be a small change if you prefer it.
- **A key pressed while a page is being read, or in the frame after a page change, is lost** (the page is read in a few milliseconds here, so a held key keeps up: 200 events moved 100 pages), but on a slow disk repeated Down events during a read do nothing.
- **Menus show the key as text,** not in a right-aligned column.
- **The text of the corrections is still only in memory** (S07).
- **The number of page reads and renderings while holding a key was not counted** (no seam for it); the latency and the consistent end state were measured.

### How to try it
1. `./gradlew run`, open a project from the sample. Click into a line of the text, then use **Up** and **Down**: the cursor and the yellow box on the scan move, and the column is kept. **Ctrl+End** and **Ctrl+Home** go to the last and first line.
2. On the last line press **Down**: page 2 opens with the cursor in its first line. Press **Up** there: back on the last line of page 1. Try page 3 (no text): the note shows; **Ctrl+Down** goes on.
3. **Ctrl+Down** and **Ctrl+Up** change the page. **Ctrl+G**, type `40`, **Enter**: page 40 with the cursor in its first line. **Ctrl+G**, **Escape**: back to the text.
4. Split a line with **Enter**, join it with **Backspace**; add a line with **Enter** at the end of a line and delete it with **Backspace**; **Delete** at the end of a line joins the next.
5. **Ctrl+Plus** (also try your keyboard's plus key, and **Ctrl+=**), **Ctrl+Minus**, **Ctrl+0**, **Ctrl+1**. Check that no `+`, `-`, `0` or `1` appears in the text. Check **AltGr** characters (`@`, `€`) can still be typed in a line.
6. Open the menus *Go* and *View*: every entry shows its key; clicking one does the same as the key. Press **F1**: the list; **F1** or **Escape** closes it, and you can go on typing in the line you were in.
7. Hold **Down** for a while: the cursor runs through the book without a freeze. `KORREKTOR_PERF_TESTS=1 ./gradlew test --tests '*KeyboardPerformanceTest*' -i` prints the numbers.
