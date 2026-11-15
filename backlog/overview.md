# Backlog overview

One file per story in `stories/`, one file per ticket in `tickets/`. Tasks are numbered `<story ID>.1`, `<story ID>.2`, … The workflow is described in [concept/process.md](../concept/process.md).

**Story statuses:** to be refined → ready for development → in progress → in review → done

**Ticket statuses:** to be refined → ready for development → in progress → done

Refining a story means writing its acceptance criteria and tasks. After review it moves to *ready for development*.

## MVP stories
| ID | Story | Features | Status |
|---|---|---|---|
| [S01](stories/S01-project-format.md) | Project format | N5, N4 | done |
| [S02](stories/S02-create-project.md) | Create a project with metadata | A1 | done |
| [S03](stories/S03-import-pdf-with-ocr.md) | Import a PDF with OCR | A2 | done |
| [S04](stories/S04-page-viewer.md) | Page viewer | C2, N1 | in review |
| [S05](stories/S05-side-by-side-editor.md) | Scan and text side by side | C1 | in progress |
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
| [T010](tickets/T010-create-project-service.md) | Create a project from a PDF (logic) | task | S02 | done |
| [T011](tickets/T011-app-shell-open-project.md) | App shell: start screen, open a project, window title | task | S02 | done |
| [T012](tickets/T012-new-project-dialog.md) | New project dialog | task | S02 | done |
| [T013](tickets/T013-edit-metadata.md) | Edit the metadata of an open project | task | S02 | done |
| [T014](tickets/T014-project-lifecycle-tests.md) | Project lifecycle tests | task | S02 | done |
| [T015](tickets/T015-code-health-checks.md) | Code health checks (Detekt, Kover) | task | – | done |
| [T016](tickets/T016-pdfium-binding.md) | PDFium and its Kotlin binding | task | S03 | done |
| [T017](tickets/T017-text-layer-analysis.md) | Analysis of the sample PDFs' text layers | task | S03 | done |
| [T018](tickets/T018-extract-words.md) | Extract words with text and boxes from a page | task | S03 | done |
| [T019](tickets/T019-coordinates.md) | Coordinates of the format | task | S03 | done |
| [T020](tickets/T020-lines-and-region.md) | Lines in reading order and one body region | task | S03 | done |
| [T021](tickets/T021-import-into-project.md) | Import a PDF into a project | task | S03 | done |
| [T022](tickets/T022-pages-without-text.md) | Pages without text or with a broken text layer | task | S03 | done |
| [T023](tickets/T023-import-in-background.md) | Import in the background with progress and cancel | task | S03 | done |
| [T024](tickets/T024-import-tests.md) | Import tests: sample PDF and generated PDFs | task | S03 | done |
| [T025](tickets/T025-review-fixes.md) | Fixes from the S03 code review | task | S03 | done |
| [T026](tickets/T026-render-region.md) | Render a page region and show a page in the project screen | task | S04 | done |
| [T027](tickets/T027-zoom.md) | Zoom: fit width, fit page, steps, Ctrl+wheel, native resolution | task | S04 | done |
| [T028](tickets/T028-pan.md) | Pan: dragging and scrollbars | task | S04 | done |
| [T029](tickets/T029-navigation.md) | Navigation: previous/next, go to page, "page n of N" | task | S04 | done |
| [T030](tickets/T030-cache.md) | Load pages on demand, with a cache of limited size | task | S04 | done |
| [T031](tickets/T031-viewer-performance.md) | Performance test on a 1000-page PDF | task | S04 | done |
| [T032](tickets/T032-remember-view.md) | Remember last page and zoom per project in the app settings | task | S04 | done |
| [T033](tickets/T033-word-box-overlay.md) | Optional overlay: word boxes | task | S04 | done |
| [T034](tickets/T034-review-fixes.md) | Fixes from the S04 code review | task | S04 | done |
| [T035](tickets/T035-zoom-limit.md) | Zoom stops at the scan's resolution, and gets stuck on a coarse scan | bug | S04 | done |
| [T036](tickets/T036-smooth-scrolling.md) | Scrolling down at fit width: the lower part appears late | bug | S04 | done |
| [T037](tickets/T037-line-operations.md) | Line operations on a page layer | task | S05 | done |
| [T038](tickets/T038-book-editor.md) | The editor state: corrected pages in memory | task | S05 | done |
| [T039](tickets/T039-font.md) | A font for historical characters | task | S05 | done |
| [T040](tickets/T040-two-panel-layout.md) | Two panels with a divider you can drag | task | S05 | done |
| [T041](tickets/T041-text-panel.md) | The text panel shows the corrected lines | task | S05 | done |
| [T042](tickets/T042-edit-lines.md) | Edit the text of a line | task | S05 | done |
| [T043](tickets/T043-line-operations-ui.md) | Split, join, add and delete lines in the panel | task | S05 | ready for development |
| [T044](tickets/T044-highlight-active-line.md) | Highlight the active line on the scan | task | S05 | ready for development |
| [T045](tickets/T045-click-line-on-scan.md) | Click a line on the scan to put the cursor in it | task | S05 | ready for development |
| [T046](tickets/T046-auto-pan.md) | Keep the active line visible by panning | task | S05 | ready for development |
| [T047](tickets/T047-editor-tests.md) | Tests with the sample, and typing speed | task | S05 | ready for development |
