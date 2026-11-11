# T014 Project lifecycle tests

**Type:** task · **Status:** ready for development · **Story:** S02 · **Task:** S02.6

Tests that cover several tickets of S02 together: create, reopen, edit metadata, and cleanup after failure, with real files.

## What needs doing
`ProjectLifecycleTest` (integration test, no UI):
- create a project from a PDF with all four metadata fields, reopen it, check everything;
- edit the metadata, reopen, check it;
- create with the real sample PDF (`samples/Abhandlung_über_den_kleinen_Krieg.pdf`, skipped if missing): the copy is byte-identical, the title is suggested as `Abhandlung über den kleinen Krieg`, the project opens;
- failures leave nothing behind: not a PDF, existing folder, missing parent, simulated copy failure, simulated failure while writing `project.json`;
- a project folder created by S01 code without metadata still opens and can get metadata later.

## Acceptance criteria
1. Every acceptance criterion of S02 that concerns logic and files (not the UI) is covered by a test in T009, T010 or this ticket; this ticket's tests pass.
2. After every failure case, the parent folder contains exactly what it contained before.
3. The sample PDF case is skipped without the file and runs with it.

## Approach
- Plain Kotlin integration tests in `src/test/kotlin/korrektor/`, using `NewProject` and `Project`. A table in the story report maps each criterion to its test.

## Dependencies
T010, T013 (so the whole flow exists).

## Decisions
None yet.

## Progress
Not started.
