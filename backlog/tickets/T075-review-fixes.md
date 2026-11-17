# T075 Fixes from the S08 code review

**Type:** task · **Status:** done · **Story:** S08 · **Task:** S08.6

The end-of-story review (Opus 5.5, whole story) found no high finding, two medium, four low ones and two simplifications. Three are fixed here; three are known gaps.

## What was done
1. **Ctrl+Z could throw (medium).** Reading older history when the loaded steps are used up could throw `FormatException` out of a key handler. `move` now catches it and answers `UndoResult.Failed`, and `HistoryLoader.read` moves its offset only after the block was decoded, so a failed read is retried instead of skipping the block.
2. **A project could not be opened after "close without saving" (medium).** If a save had appended entries and then failed to write a page, the recovery at the next opening tried to write that page again and, if it failed, refused the project. Now a failed recovery is a notice ("The last changes could not be restored from the history: …"); the project opens.
3. **A half-made `oneStep` could be saved without its entry (low).** Inside the block the intermediate layer is only shown (`layer`), not put in `changed`; the page and its step are put together when the block ends.

## Acceptance criteria
1. Undo through a history with a damaged older entry gives `Failed`, no exception.
2. A project with an entry that cannot be applied because its page cannot be read opens, with a notice.
3. While `oneStep` runs, `unsaved()` and the pending entries are empty; afterwards both have the page and its one entry.

## Decisions
- **Known gap (review 4):** the redo state can differ between the session and after reopening: typing after an undo and deleting it again within a second drops the step, which a reopened history shows as the redo steps from before. Also a step dropped because it does not fit comes back after reopening and is dropped again. Nothing is damaged.
- **Known gap (review 5):** if only the `saved` entry cannot be written, the failure says "0 pages" in the close dialog; the recovery repairs it at the next opening.
- **Known gap (review 6):** `canUndo` is true while older entries exist, also if all of them were undone for good (the first Ctrl+Z then does nothing); and a history whose newest steps were nearly all undone is read back further when opening.
- **Close without saving does not throw everything away:** the entries of a save that failed on a page are in the history file, so the next opening restores those changes (and they can be undone). Said in the story report.

## Progress
Done. Tests: `HistoryReviewFixesTest` (3). Build green.
