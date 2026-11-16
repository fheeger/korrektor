# T054 Menus with the shortcuts, and the list on F1

**Type:** task · **Status:** ready for development · **Story:** S06 · **Task:** S06.6

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
(filled in while building)

## Progress
Not started.
