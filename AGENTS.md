# Agent instructions

## Project
A native desktop app (later also for tablets) that turns historical PDFs into clean, searchable text by correcting their OCR. It is a hobby project; the author is the primary user. See [concept/vision.md](concept/vision.md) and [concept/features.md](concept/features.md).

## Current phase
Development. The concept is done:
- Vision and features: `concept/vision.md`, `concept/features.md` (with the MVP cut)
- Stories: `backlog/stories/`
- Development process, including how AI is used: `concept/process.md`
- Technical concept: `docs/decisions.md` (D01 Kotlin with Compose Multiplatform, D02 PDFium, D03 project format). The OCR engine is decided just before B1.

## Working rules
- Development follows `concept/process.md`. While a story or ticket is in progress, work autonomously as described there.
- Outside a story or ticket (changes to `concept/`, `AGENTS.md`, decisions, new stories): discuss proposals in chat first, and write or change files only after the user has agreed to the content.

## Backlog conventions
The full workflow is in `concept/process.md`.
- `backlog/stories/` has one Markdown file per story, named `<ID>-<slug>.md` (e.g. `S01-project-format.md`).
- Story IDs are `S01`, `S02`, …; tasks are numbered `<ID>.1`, `<ID>.2`, … as checkboxes.
- Each story lists the feature IDs it covers (from `concept/features.md`), a user story, acceptance criteria and tasks.
- Story statuses: `to be refined` → `ready for development` → `in progress` → `in review` → `done`. Refining a story means writing acceptance criteria and tasks; after the user's review the story becomes `ready for development`.
- `backlog/tickets/` has one Markdown file per ticket, named `T001-<slug>.md`. Ticket IDs run across the whole project. Types: `task` and `bug`. A ticket belongs to a story task or is loose.
- Ticket statuses: `to be refined` → `ready for development` → `in progress` → `done`.
- `backlog/overview.md` lists all stories and tickets. Keep its status columns in sync with the files.

## Test material
`samples/Abhandlung_über_den_kleinen_Krieg.pdf`: a Google Books PDF (~165 pages) with an OCR text layer. It is the first test object.
