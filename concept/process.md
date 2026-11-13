# Development process

## Workflow

### Backlog
- The backlog lives in `backlog/`:
  - `overview.md`: all stories and all tickets with their status
  - `stories/`: one file per story (`S01-<slug>.md`, …)
  - `tickets/`: one file per ticket (`T001-<slug>.md`, …)
- Story statuses: `to be refined` → `ready for development` → `in progress` → `in review` → `done`
- Ticket statuses: `to be refined` → `ready for development` → `in progress` → `done`
- The status in each file and in `overview.md` is always kept in sync.

### Roles
- **The user** picks the next story or loose ticket, reviews finished stories, and can create loose tickets at any time (a title and a few lines are enough).
- **Claude** works on its own from that point: refining, building, checking, reporting.

### Tickets
- A ticket is the smallest unit of work. Ticket IDs run across the whole project: `T001`, `T002`, …
- Types: `task` (planned work, usually from a story) and `bug`.
- A ticket contains:
  - Type, status, and the story and task it belongs to (or "none")
  - What needs doing
  - Acceptance criteria
  - Approach: the parts that change and how it's tested (test levels and test data, see "Quality and tests")
  - Dependencies on other tickets
  - Decisions made during the work, with reasons
  - Bugs only: steps to reproduce, expected vs. actual behaviour
- A ticket is ready when its criteria can be checked, its approach and dependencies are clear, and it's small enough to review in one sitting.
- A ticket is done when its criteria are met, CI is green and the documents it affects are updated (see "Documentation").

### Before starting a story
Claude lists the open loose tickets. Together we decide which of them to do first. This step is interactive.

### Life of a story
1. **Start:** the user says "work on Sxx". The story goes to `in progress`.
2. **Refine:** Claude creates and refines all of its tickets in one go and sets them to `ready for development`. Normally there's one ticket per task, and each task links to its ticket(s). Tests belong to the ticket they test. Tests that cover several tickets get their own ticket.
3. **Build:** one ticket at a time. Its approach is checked again first, since earlier tickets may have changed things. A task's checkbox is ticked when all its tickets are done.
4. **Check:** every acceptance criterion of the story is checked, with tests and by hand with the sample PDF.
5. **Report:** Claude adds a report to the story, opens the story PR into `main` and sets the story to `in review`. The report covers:
   - what was built
   - all decisions made
   - how each criterion was checked
   - known gaps and uncertainties
   - how to try it
6. **Review:** the user reviews the story PR. Changes become new tickets in the story, which Claude works through. The story goes to `done` when the user merges the story PR.

### Loose tickets
- Worked on the same way when the user says "work on Txxx": refine, build, check. Before the PR, the ticket's changes get the same code review as a story (see "Code review"). Claude then opens a PR into `main` and reports in chat. The ticket goes to `done` when the user merges it.
- Changes after the user has looked at it become new tickets.

### Decisions and when to stop
- Unclear points are decided by Claude and recorded in the ticket under "Decisions".
- Claude only stops and asks if:
  - an acceptance criterion is impossible or contradicts another one,
  - the work would clearly go beyond the scope,
  - a decision would be hard to undo later (e.g. changing the file format once it's in use, or adding a large new dependency).
- Only one story and one ticket are in progress at a time.

## How AI is used

### Tool and entry point
- The work is done with Claude Code in this folder. `AGENTS.md` is the entry point: it holds the rules and points to this file and to the backlog. Nothing important lives only in chat.

### Models
- Coding and all other tasks: Sonnet 5.5 (the medium-cost model). It is the default in `.claude/settings.json`.
- Code review (end of story, loose tickets): Opus 5.5, run as a separate agent.

### State lives in the files
- The ticket in progress has a "Progress" section that is kept up to date: what's done, what's next, open problems.
- Statuses, checkboxes and `overview.md` are updated as the work goes on, not at the end.
- Claude's memory only holds a short status pointer and the user's feedback. Project facts belong in the repo.

### Starting and resuming
- "work on Sxx" / "work on Txxx": start as described in the workflow.
- "continue": find what's in progress in `overview.md`, read its ticket including "Progress", and carry on.
- If nothing is in progress: say so and list the open loose tickets.

### Code review
- Before a story goes to `in review`, all of its changes get a code review using the review model. Claude fixes what it finds, and the report lists what was found and fixed.
- Loose tickets go straight into `main`, so they get the same review before their PR. The PR description lists what was found and fixed.
- Story tickets get no review of their own. The end-of-story review covers them.
- Before the review, Claude runs `./gradlew build koverXmlReport` and reads the Detekt and coverage reports. Findings are fixed before the review. Uncovered new code is either tested or listed in the PR description or story report with the reason.
- Anything Claude can't check itself (especially in the UI) is listed in the report under "how to try it" for the user to check.

### What Claude may do on its own
- Yes: create and change files in the project, run builds and tests, start the app, install small dependencies.
- Stop and ask: large new dependencies, anything outside the project folder.
- Also yes: push branches, open PRs, merge ticket PRs into story branches (see "Version control").
- Never: merge into `main`, force-push to `main`, or publish anything else without the user's go-ahead.

### Subagents
- Not used by default. The only exception is the code review.

### The user's own changes
- Changes the user makes to code or files are the current state. Claude builds on them and doesn't undo them.

## Version control

### Repository
- Git, hosted on GitHub as a public repository. The license is GPL-3.0.
- Claude works through the GitHub CLI (`gh`), logged in with the user's account.
- `samples/` is not in the repository (`.gitignore`). The README says where the sample PDF comes from. Tests that need it skip if it's missing.
- `main` is protected: no direct pushes, no force pushes, also for admins (Claude works under the user's account). Only the user merges into `main`.

### Branches
- Story branch: `story/<ID>-<slug>` (e.g. `story/S07-autosave`), branched from `main` when the story starts.
- Ticket branch: `ticket/<ID>-<slug>` (e.g. `ticket/T023-dirty-pages`), branched from the story branch, or from `main` for loose tickets.
- Merged branches are deleted.
- Before the story PR at the latest, and whenever a conflict looms, `main` is merged into the story branch.

### Commits
- Small steps within a ticket branch.
- Message: `<ticket ID>: <what changed>` in English, e.g. `T023: Track pages with unsaved changes`, ending with Claude's attribution line.

### Pull requests
- **Ticket PR → story branch.** Title `T023: <ticket title>`. The description links the ticket and says what changed and how it was tested. Claude merges it with a squash merge once CI is green.
- **Story PR → `main`.** Title `S07: <story title>`. The description links the story and its report, lists the ticket PRs and gives the code review results. Changes after the review come in as ticket PRs into the story branch. The user merges it with a merge commit, and the story goes to `done`.
- **Loose ticket PR → `main`.** The description links the ticket, says what changed and how it was tested, and gives the code review results. The user merges it with a squash merge, and the ticket goes to `done`.

## Quality and tests
The concrete tools (test framework, formatter, linter) are chosen with the technical concept.

### Test levels
- **Unit tests** for the logic (e.g. project format, normalization, find and replace, undo history). They make up the bulk of the tests.
- **Integration tests** for the parts working together with real files (e.g. import a PDF, save, reopen, export).
- **UI tests** only for a few core flows (open a project, correct a page, move on to the next).
- **Crash and performance tests** only where a story asks for them (e.g. S07, N1).

### Test data
- Small test PDFs (1–5 pages) live in the repo: generated or public domain, with and without an OCR layer, with Fraktur. Most tests use them, including in CI.
- A large test book (e.g. 1000 pages) is generated by a script when needed and not stored in the repo.
- The sample PDF is only used for local tests and manual checks. Tests that need it are skipped if it's missing.

### Rules for each ticket
- Every acceptance criterion is covered by an automated test where that's reasonably possible. Whatever can only be checked by hand goes into the story report.
- Bugs: first a test that reproduces the bug and fails, then the fix.
- Tests are written in the ticket they belong to. Only tests that cover several tickets get their own ticket.

### CI
- GitHub Actions runs on every PR: build, all tests except those needing the sample PDF, formatter and linter.
- A ticket PR is only merged when CI is green.
- CI runs on Windows first. macOS and Linux are added with those platforms.
- `./gradlew build` includes Detekt. Any finding fails the build; there is no baseline. Detekt and Kover reports are uploaded as a CI artifact.
- Performance and crash tests run locally or on demand, not on every PR.

### Code style
- One formatter (ktlint) and static analysis (Detekt) with their default settings, plus the Compose rule set. The tools decide, not discussions. A suppression needs a comment saying why.
- No coverage target. Kover reports coverage for review, but nothing fails on it. The rule is that every criterion is tested.

## Documentation

### Where things live
| What | Where | Changes |
|---|---|---|
| Vision, features, process | `concept/` | Only with the user's agreement |
| Stories and tickets | `backlog/` | Kept up to date as the work goes on |
| Rules for Claude | `AGENTS.md` | When a rule changes, with the user's agreement |
| Architecture overview | `docs/architecture.md` | Written with the technical concept, updated by every ticket that changes the structure |
| Project file format | `docs/project-format.md` | Written in S01, updated by every ticket that changes the format |
| Important decisions | `docs/decisions.md` | When a big decision is made |
| For users | `README.md`, section "Using korrektor" | Updated by every ticket that changes behaviour |

### Project file format
Projects are exchanged and must stay stable (V3), so the format has its own specification: files, fields, versioning. Every change to the format updates the specification in the same ticket.

### Decisions
- Small decisions are recorded in the ticket under "Decisions".
- Big decisions go into `docs/decisions.md` as numbered entries (`D01`, `D02`, …), each with the decision, the reason and the alternatives considered. This covers the tech stack, the file format, and anything that falls under the stop case "hard to undo".

### Code
- Comments explain *why*, not *what*.
- Public interfaces (modules, important functions) get a short description. There is no separate API documentation.

### Users
`README.md` has a short section "Using korrektor": how to start, the main steps, and a list of keyboard shortcuts.

### Rules
- A ticket is only done when the documents it affects are updated. The end-of-story code review checks this too.
- Everything is in English: documentation, code, comments, commits.
- No changelog until there are releases. Until then, the story PRs and the backlog serve that purpose.
