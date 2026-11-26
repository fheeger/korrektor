# T105 Release docs: releasing.md, README install section, format note, licenses check

**Type:** task · **Status:** done · **Story:** S12 · **Task:** S12.6

## What needs doing
- `docs/releasing.md`: the version policy (0.x, SemVer with `-beta.N`), the release steps (update the version, turn `Unreleased` into the version, tag, run the workflow, check the draft, the user publishes) and where the build comes from.
- `README.md`: an "Install" section and the beta note.
- `docs/project-format.md`: a sentence in "Version and compatibility" that the format may change during 0.x without migration.
- `docs/third-party.md`: compare with the content of the zip and fix what differs (runtime license, anything added).

## Acceptance criteria
1. The four documents are changed as described, and the links between them work.
2. Every component in the zip is in `third-party.md` and its license text is in the zip.

## Approach
Documents. The licenses check lists the zip contents from T102.

## Dependencies
T102, T104

## Decisions
- The Apache License 2.0 is not in the PDFium folder under its own name, so a copy is kept in `docs/licenses/Apache-2.0.txt` and shipped in the zip under `app/resources/licenses/apache-2.0/` (Compose, Skiko, Kotlin and kotlinx ask for it).
- Found while checking the zip: the Compose task `createDistributable` did not notice a change in the resources folder, so a local rebuild kept old files. The folder is now an input of the task (T102 code, fixed here because the license copy exposed it).
- `releasing.md` describes the draft-and-publish steps of T103: the maintainer publishes the draft, which creates the tag.

## Progress
Done. `docs/releasing.md`, the README (Install section, beta note, links, start of "Using korrektor"), the note in `docs/project-format.md`, `docs/third-party.md` (what the zip holds), the license copy. The zip was listed: every component named in `third-party.md` is there, with its license text.
