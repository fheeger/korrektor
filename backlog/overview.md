# Backlog overview

One file per story in `stories/`, one file per ticket in `tickets/`. Tasks are numbered `<story ID>.1`, `<story ID>.2`, … The workflow is described in [concept/process.md](../concept/process.md).

**Story statuses:** to be refined → ready for development → in progress → in review → done

**Ticket statuses:** to be refined → ready for development → in progress → done

Refining a story means writing its acceptance criteria and tasks. After review it moves to *ready for development*.

## MVP stories
| ID | Story | Features | Status |
|---|---|---|---|
| [S01](stories/S01-project-format.md) | Project format | N5, N4 | in review |
| [S02](stories/S02-create-project.md) | Create a project with metadata | A1 | in progress |
| [S03](stories/S03-import-pdf-with-ocr.md) | Import a PDF with OCR | A2 | ready for development |
| [S04](stories/S04-page-viewer.md) | Page viewer | C2, N1 | ready for development |
| [S05](stories/S05-side-by-side-editor.md) | Scan and text side by side | C1 | ready for development |
| [S06](stories/S06-keyboard-navigation.md) | Keyboard navigation | C2 | ready for development |
| [S07](stories/S07-autosave.md) | Autosave | N4 | ready for development |
| [S08](stories/S08-undo-redo.md) | Undo/redo | F1 | ready for development |
| [S09](stories/S09-page-status.md) | Page status | C5 | ready for development |
| [S10](stories/S10-find-and-replace.md) | Find and replace with a preview | D2 | ready for development |
| [S11](stories/S11-export-text-markdown.md) | Export plain text and Markdown | G1 | ready for development |

## Tickets
| ID | Ticket | Type | Story | Status |
|---|---|---|---|---|
| [T001](tickets/T001-development-setup.md) | Development setup | task | – | done |
| [T002](tickets/T002-data-model.md) | Data model | task | S01 | done |
| [T003](tickets/T003-page-file-codec.md) | Page files: canonical JSON, read and write | task | S01 | done |
| [T004](tickets/T004-crash-safe-write.md) | Crash-safe file writing | task | S01 | done |
| [T005](tickets/T005-project-folder.md) | Project folder: project.json, PDF copy, create and open | task | S01 | done |
| [T006](tickets/T006-history-log.md) | Undo history: append-only log | task | S01 | done |
| [T007](tickets/T007-format-spec.md) | Project format specification | task | S01 | done |
| [T008](tickets/T008-sample-fits-format.md) | The sample book fits the format | task | S01 | done |
| [T009](tickets/T009-project-metadata.md) | Project metadata in the format | task | S02 | done |
| [T010](tickets/T010-create-project-service.md) | Create a project from a PDF (logic) | task | S02 | ready for development |
| [T011](tickets/T011-app-shell-open-project.md) | App shell: start screen, open a project, window title | task | S02 | ready for development |
| [T012](tickets/T012-new-project-dialog.md) | New project dialog | task | S02 | ready for development |
| [T013](tickets/T013-edit-metadata.md) | Edit the metadata of an open project | task | S02 | ready for development |
| [T014](tickets/T014-project-lifecycle-tests.md) | Project lifecycle tests | task | S02 | ready for development |
