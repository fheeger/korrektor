# T054 Menus with the shortcuts, and the list on F1

**Type:** task · **Status:** done · **Story:** S06 · **Task:** S06.6

## What needs doing
- **A menu bar** in the window (Compose `MenuBar`) with the menus *Go* (previous/next line, first/last line, previous/next page, go to page), *View* (zoom in, zoom out, fit page, fit width) and *Help* (Keyboard shortcuts). Each entry shows its shortcut and calls the same action as the key. Entries that cannot apply are disabled (no project open, first/last page).
- The menu bar must not handle the keys a second time: either the entries only *display* the shortcut text and the root handler (T049) does the work, or the accelerators do the work and the handler skips them. Decide while building which of the two avoids double handling in Compose Desktop, and record it.
- **F1** opens a dialog listing every shortcut (from the `Shortcuts` table of T049) and the text-editor keys (Enter, Backspace, Delete) with what they do. Escape or "Close" closes it. F1 again closes it.
- **README:** the keyboard shortcuts in "Using korrektor" (the list the process asks for) and a sentence about keyboard-only work.
- The menus and the list are generated from the table, so a new shortcut cannot be forgotten: a test fails if an action of the table is missing from the menus or the list.

## Acceptance criteria
1. Every action of the table appears in a menu with its shortcut text, and in the F1 list (test over the table).
2. Choosing a menu entry has the same effect as its key (UI test on two entries, the others through the shared action interface).
3. A shortcut is handled once, not twice, whether pressed in a row or in a field (UI test: one page change per Ctrl+Down).
4. F1 opens the list, Escape closes it, and the focus returns to where it was.
5. Menu entries are disabled when they do not apply. With no project open the Go and View entries are disabled.
6. The README lists the shortcuts.

## Approach
- `Main.kt` (the menu bar needs the `FrameWindowScope`), `ShortcutList.kt` (the dialog). Tests in `MenusTest` and `ShortcutListTest`. Compose UI tests cannot click a native menu bar, so the entries come from a function that returns the entry list, and that function is tested directly.

## Dependencies
T049, T050, T051, T053.

## Decisions
- **The menu entries only show the shortcut; the key handler does the work.** An entry is a plain item whose text is the name and the key ("Next page, Ctrl+Down", separated by a wide space), without Compose's `shortcut` parameter. With that parameter the menu bar registers an accelerator of its own, and whether Swing's accelerator and the Compose key handler both fire for one key press could not be checked in a test (menu bars are native and a headless test cannot press keys on them), so the safe way was taken: one place handles keys. Clicking an entry calls the same `ProjectKeys.perform` as the key. A native right-aligned accelerator column could come later if it can be shown not to double-handle.
- **The menus are made by `menus(MenuState)`,** a plain function from the table of shortcuts and four facts (project open, previous and next page exist, the page has lines). So they are tested without a window: every action of the table is in exactly one entry, each entry shows its label and key, the enabled states. `Main.kt` only turns the specs into `MenuBar` items.
- **Disabled entries:** all but "Keyboard shortcuts" without a project; "Previous page" on page 1 and "Next page" on the last page; the four line moves on a page without lines.
- **No dead keys:** `ProjectKeys.handledActions` must equal all actions (test), so a new action in the table without a handler fails the build.
- **F1 toggles** (`AppModel.shortcutsOpen`, a plain flag): it opens the dialog and, pressed in the dialog, closes it. The dialog content (`ShortcutList`) takes the focus and closes on F1 and Escape; "Close" and the window's own close button too. It works without a project too, from the menu.
- **The list is generated from the table** (keys with their alternatives, description) plus `Shortcuts.editorKeys` for Enter, Backspace and Delete in a line, which are not shortcuts of the handler.
- **README:** a "Keyboard" bullet in "Using korrektor" with the keys.
- **Not testable here (listed for the report):** that the native menu bar shows the entries and that clicking works; that after the F1 dialog closes the focus is back in the line the cursor was in.

## Progress
Done. `Menus.kt`, `ShortcutList.kt`, `Main.kt` (menu bar), `AppModel` (`shortcutsOpen`), `ProjectKeys` (F1), `Shortcuts.editorKeys`, README. Tests: `MenusTest` (9: every action once, label and key, the menus, disabled states, F1 toggles the model, no action without a handler) and `ShortcutListTest` (3: every shortcut and editor key listed, Close/Escape/F1 close it).
