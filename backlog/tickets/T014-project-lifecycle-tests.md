# T014 Project lifecycle tests

**Type:** task · **Status:** done · **Story:** S02 · **Task:** S02.6

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
- **The PDF for each test lives outside the folder the projects go to,** so a snapshot of that folder (every path with its size) shows exactly what the app made, and "exactly what it contained before" can be compared directly.
- **Failure while writing `project.json` is simulated for real:** the copy seam copies the PDF and then puts a folder where the temporary file of `project.json` must go. The test then checks that the project folder is gone, and that creating the same project afterwards works.
- **The flow through the app models is included** (create, a new session opens it, edit, a third session reads it), because the ticket asks for the whole flow and the models are plain Kotlin that needs no window.
- **The sample PDF test uses `assumeTrue`,** like the other sample tests, so it shows as skipped when the file is missing (as in CI, where `samples/` is not present).

## Progress
Done: PR #18 merged, CI green; the sample PDF test showed as skipped on CI, as intended. Code done; `./gradlew build` green (11 new tests in `ProjectLifecycleTest`, including the sample PDF case, which ran locally). Criterion 3 (skipped without the file) is checked on the CI run of the PR.

Which test covers which logic or file criterion of S02 (for the story report):

| S02 criterion | Test |
|---|---|
| No project without a PDF; a bad or missing PDF is refused | `NewProjectTest` (not a PDF, missing, folder, unreadable), `NewProjectModelTest` (`canCreate`), `ProjectLifecycleTest` |
| The project has its own copy of the PDF | `NewProjectTest`, `ProjectLifecycleTest` (incl. the sample PDF, byte-identical) |
| Title, author, year, source; only the title required; title suggested from the file name | `MetadataTest` (blank title), `NewProjectTest` (`suggestTitle`), `NewProjectModelTest` |
| Year and source are free text (`ca. 1790`, `1785?`) | `MetadataTest`, `NewProjectModelTest`, `ProjectLifecycleTest` |
| Metadata can be edited later | `MetadataTest` (`updateMetadata`), `EditMetadataModelTest`, `ProjectLifecycleTest` |
| Stored in the format and documented | `MetadataTest`, `SpecExamplesTest` (spec examples round-trip) |
| An existing project can be opened | `AppModelTest`, `AppUiTest` (the file dialog itself is a manual check) |
| The window title shows the project title | `AppModelTest`, `AppUiTest`, `EditMetadataModelTest`, `ProjectLifecycleTest` |
| Errors are clear and nothing half-created is left | `NewProjectTest`, `NewProjectModelTest`, `ProjectLifecycleTest` (parent folder unchanged after every failure) |
| Projects from before S02 still open | `MetadataTest`, `ProjectLifecycleTest` (S01-style `project.json`) |
