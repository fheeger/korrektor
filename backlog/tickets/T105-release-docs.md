# T105 Release docs: releasing.md, README install section, format note, licenses check

**Type:** task · **Status:** ready for development · **Story:** S12 · **Task:** S12.6

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
(none yet)

## Progress
Not started.
