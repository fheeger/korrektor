# S02 Create a project with metadata

**Features:** A1 · **Status:** in progress

As a reader of historical sources, I want to create a project for a source with its title, author, year and source, and open it again later
so that I can keep my sources apart and come back to my work.

## Acceptance criteria
- "New project" starts by choosing a PDF. There are no projects without a PDF.
- The project gets its own copy of the PDF (see S01). Reading the OCR text is part of S03.
- I can enter title, author, year and source (where the scan came from, e.g. a Google Books link). Only the title is required; it is suggested from the file name.
- Year and source are free text, so values like "ca. 1790" or "1785?" are allowed.
- I can edit the metadata later.
- The metadata is stored in the project format and documented in the format spec.
- I can open an existing project with an "Open…" file dialog.
- The window title shows the project title.
- If the PDF can't be read or the project can't be created at the chosen location, a clear error is shown and nothing half-created is left behind.

Out of scope: a list of recently opened projects (later).

## Tasks
- [x] S02.1 Add the metadata fields to the data model and the format spec → [T009](../tickets/T009-project-metadata.md)
- [x] S02.2 "New project" dialog: choose a PDF, enter metadata, choose where to store the project → [T012](../tickets/T012-new-project-dialog.md)
- [x] S02.3 Create the project and copy the PDF into it, cleaning up if anything fails → [T010](../tickets/T010-create-project-service.md)
- [x] S02.4 Dialog for editing the metadata → [T013](../tickets/T013-edit-metadata.md)
- [x] S02.5 Open an existing project with a file dialog → [T011](../tickets/T011-app-shell-open-project.md)
- [x] S02.6 Tests: create, reopen, metadata round trip, cleanup after failure → [T014](../tickets/T014-project-lifecycle-tests.md)
