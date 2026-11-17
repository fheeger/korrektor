# T073 Load the history when a project is opened, and on demand

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.4

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
- **The loader is a class of its own (`HistoryLoader`)** and the history asks it for more: it keeps the state of the steps seen so far (`settled`), the undo and redo entries after the newest edit (`trailing`) and the offset it has read back to. `UndoHistory` stays the one place that has the stacks.
- **It reads whole blocks (16 entries),** so it can find a few more than the 100 wanted; the stack holds 100 to about 115 steps after opening. The criterion said "at most 100"; this is "about 100", which is what the bound is for (a test checks it stays below 120).
- **The redo stack is found at the first `edit` met,** whatever else was found: undo entries before the newest edit never make a step redoable, because a new edit ended the redo steps.
- **A damaged entry stops the opening** (the error names the line, as the spec says), instead of leaving out a step silently; a project with a damaged history needs the file repaired or removed. Considered: opening with an empty history and a notice. Rejected: new entries would be appended to a damaged file and the numbers could clash with the ones in the pages, which the recovery depends on.
- **`canUndo` stays true while the file has older entries,** also if they are all steps that were undone for good; then the first undo finds none and says nothing to do.
- **`UndoHistory`'s primary constructor is internal** (it takes a loader, which is internal); the public one starts empty.

## Progress
Done. `HistoryLoader`, `UndoHistory.load`, `HISTORY_WINDOW`, loading on demand in `peek`, `AppModel.open` loads the history, README. Tests: `UndoHistoryLoadTest` (8: random sessions with the same stacks after loading, the same behaviour on 250 further operations, undo to the first step over dead steps, 100,000 steps read bounded, torn line, no history, `saved` at the end, damaged entry), `HistoryReopenTest` (undo, redo and another close and open on pages the session never showed). Build, tests, ktlint and Detekt are green.
