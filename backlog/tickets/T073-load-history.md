# T073 Load the history when a project is opened, and on demand

**Type:** task · **Status:** ready for development · **Story:** S08 · **Task:** S08.4

## What needs doing
- **`UndoHistory.open(log)`**: reads the history file backwards from its end, in blocks, until it knows the last 100 steps that can be undone (`HISTORY_WINDOW`) and the redo stack; it never reads the whole file. A step's state comes from the latest entry that mentions it (an `edit` can be undone, unless an `undo` entry that came later is not followed by a `redo`); the redo stack is made of the `undo` entries after the last `edit` (an `undo` pushes the step, a `redo` takes it off; the step's changes are the inverted `changes` of the `undo` entry). The next `seq` is the last entry's plus one.
- **More on demand:** when the undo stack is empty and the file has older entries, the next undo reads the next block back from where it stopped (the state of the steps seen so far is kept), so a history of any length can be undone to the start.
- `AppModel.open` makes the editor with its history from the project's file; a project without one starts empty.
- The saver's entries (T072) and these readers use the same codec.

## Acceptance criteria
1. A history with edits, undos, redos and a new edit after an undo gives the same undo stack and redo stack as the session that wrote it (a test makes random sessions with `UndoHistory`, writes them, loads them, and compares both stacks and what 20 undos and redos then do).
2. Opening a history of 100,000 steps reads a bounded number of bytes (counted, a few blocks, not the file) and the stacks hold at most 100 steps.
3. After the 100 loaded steps are undone, more are loaded and undo goes on to the first step ever made, also when undone steps (not redoable) lie between them.
4. After a crash with a torn last line, the history loads from the complete entries and the next entry gets the next number.
5. Undo after reopening restores what the last session changed, also on pages the new session never shown.
6. Opening a long history is fast (measured in T074).

## Approach
`UndoHistory.kt`, `AppModel.open`. Tests: `UndoHistoryLoadTest` (random sessions, a generated long file, torn line).

## Dependencies
T069, T071.

## Decisions
Written down while working.

## Progress
Not started.
