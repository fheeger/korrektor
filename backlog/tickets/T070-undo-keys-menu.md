# T070 Keys, menu and list for undo and redo

**Type:** task · **Status:** ready for development · **Story:** S08 · **Task:** S08.3

## What needs doing
- **`AppAction.UNDO` and `REDO`** in `Shortcuts`: Ctrl+Z; Ctrl+Y and Ctrl+Shift+Z (Ctrl+Y is the one shown). The key handler uses them up like all shortcuts, so the text field's own undo (one field at a time) never runs.
- **`ProjectKeys`** handles them: `editor.undo()` or `redo()`, and if the step is on another page, `viewer.goTo(page)` (the editor follows and puts the cursor there, T069).
- **An "Edit" menu** (before "Go") with *Undo* and *Redo*, each with its key; an entry is enabled only if there is something to undo or redo (`MenuState.canUndo`, `canRedo`). The list on F1 shows both (it is made from the table).
- Hand check in the real window: Ctrl+Z in a line, over a page change, after Enter.

## Acceptance criteria
1. Ctrl+Z undoes, Ctrl+Y and Ctrl+Shift+Z redo, in the whole window with the focus in a line, in the page field and on a button (the same way as the other shortcuts).
2. The Edit menu has both entries with their keys; they are disabled when there is nothing to do, and enabled when there is.
3. The list on F1 (the shortcut list) has Undo and Redo.
4. Typing "abc" in a line and pressing Ctrl+Z removes "abc" in one step, Ctrl+Y brings it back; the cursor stays in the line (UI test).
5. Edit on page 1, go to page 2, Ctrl+Z: page 1 is shown with the cursor at the change (UI test).
6. A test checks that every action of the table has a handler (existing) and that every action is in the menus (existing).
7. README: the shortcut list and "Using korrektor" mention undo and redo.

## Approach
`Shortcuts.kt`, `ProjectKeys.kt`, `Menus.kt` (`MENU_LAYOUT`, `MenuState`), `Main.kt` (menu bar state), README. Tests: `ShortcutsTest`, `MenusTest`, `ShortcutListTest`, new `UndoUiTest`.

## Dependencies
T069.

## Decisions
Written down while working.

## Progress
Not started.
