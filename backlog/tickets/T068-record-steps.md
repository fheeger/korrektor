# T068 Record steps: the undo and redo stacks of the editor

**Type:** task · **Status:** ready for development · **Story:** S08 · **Task:** S08.2

Every change of the text becomes a step in the history of the open book.

## What needs doing
- **`UndoHistory`** (`korrektor.editor`): the history of the book in memory. It holds the **undo stack** and the **redo stack** of `Step(seq, changes)` and the **pending entries**: the operations (`EditEntry`, later `UndoEntry` and `RedoEntry`, T069) that the saver has not written yet, each with a number `seq`, counted up from the last number the history file has (1 for a new project, T073). It offers `record(changes, typing)`, which pushes a step, clears the redo stack (a new change after undo removes the redo steps) and puts the entry into the pending list; `seal()` (the open typing step takes no more edits); `drainPending()` for the saver (T072); `canUndo`, `canRedo` as Compose state.
- **`BookEditor.edit`** records the step: `diff(layer before, layer after)` (T067). Typing (`setLineText`) is merged into the open step by `TypingGroups`: the step keeps its `removed` (the line as it was at the start) and takes the newest `inserted`; its pending entry is updated in place. If the merged step changes nothing any more (typed and deleted again), it is dropped. Every other edit seals the open step first.
- **`BookEditor.oneStep { … }`**: all edits made inside the block become one step (diff from the layer before the block to the layer after it). The Enter key with a selection (`setLineText`, then `splitLine`) uses it, so that one key press is one step.
- **`BookEditor.record(changes)`** for changes of several pages at once (a "replace all" of S10): one step, with the pending entry; the layers it produces are put in place by the caller through `BookEditor.commit(page, layer)`.
- Each layer an edit produces carries the number of its entry (`PageLayer.history`, an optional field; T071 writes it into the file): the layers are stamped when they are made, so a layer on disk says which entries it contains.
- Sealing: a page change (`show`), an undo or a redo, and the saver's `drainPending` seal the open typing step.

## Acceptance criteria
1. Each edit of S05 and S06 (typing in a line, Enter, Backspace and Delete at the line's ends, Add line, Delete line) makes one step with the right changes; a no-op edit makes none.
2. A run of typing in one word is one step; the step's `removed` is the line before the first character and `inserted` the line after the last.
3. After a new edit, the redo stack is empty.
4. Enter with a selected text is one step.
5. Typing a word and deleting it again leaves no step.
6. The pending list holds one entry per step, in order; a merged step has one entry, with the final text. `drainPending` returns them and empties the list; typing after it starts a new step.
7. A page's layer carries the `seq` of the last entry that changed it.

## Approach
`UndoHistory.kt`, `BookEditor` (`edit`, `oneStep`, `record`), `TextPanel.lineKey` uses `oneStep`. Tests: `UndoHistoryTest` and additions to `BookEditorTest` (fake clock through the constructor).

## Dependencies
T067.

## Decisions
Written down while working.

## Progress
Not started.
