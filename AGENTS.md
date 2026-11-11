# Agent instructions

## Project
A native desktop app (later also for tablets) that turns historical PDFs into clean, searchable text by correcting their OCR. It is a hobby project; the author is the primary user. See [concept/vision.md](concept/vision.md) and [concept/features.md](concept/features.md).

## Current phase
Concept phase. There is no code yet, and none should be written until the concept is done.

Process, all as Markdown:
1. Vision (`concept/vision.md`) – done
2. Features (`concept/features.md`) – done
3. MVP cut (priority column in `concept/features.md`) – done
4. Stories and tasks (`backlog/stories/`) – done
5. Development process, including how AI is used (`concept/process.md`) – in progress (only documentation still open)

The tech stack is not a story. It is a separate decision (the "technical concept" in `vision.md`) made before development starts.

## Working rules
- Go one step at a time. Discuss proposals in chat first.
- Write or change files only after the user has agreed to the content.
- Don't jump ahead to later steps or to code.
- Exception: while a story or ticket is in progress, work autonomously according to `concept/process.md`.

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
