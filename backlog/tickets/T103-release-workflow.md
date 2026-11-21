# T103 Release workflow: manual trigger, tests, zip, draft pre-release

**Type:** task · **Status:** ready for development · **Story:** S12 · **Task:** S12.4

## What needs doing
- `.github/workflows/release.yml` with `workflow_dispatch` and the input `tag` (for example `v0.1.0-beta.1`). It checks out the tag, checks that the tag matches the version in `build.gradle.kts`, runs `./gradlew build` and `releaseZip`, and creates a draft GitHub release (a pre-release if the version has a suffix) with the zip and the section of that version from `CHANGELOG.md` as its text.
- Permission `contents: write` for this workflow only.

## Acceptance criteria
1. The workflow file is valid (checked by careful reading and a YAML parse; no runner is available for a dry run before a tag exists).
2. A tag that does not match the version fails before building.
3. The release is created as a **draft**, never published.
4. The text of the release is the changelog section of the version; a missing section fails the workflow.

## Approach
Shell steps in the workflow. The changelog extraction is a small script in `scripts/` that can be run and tested locally.

## Dependencies
T102, T104

## Decisions
(none yet)

## Progress
Not started.
