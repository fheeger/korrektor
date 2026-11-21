# T103 Release workflow: manual trigger, tests, zip, draft pre-release

**Type:** task · **Status:** done · **Story:** S12 · **Task:** S12.4

## What needs doing
- `.github/workflows/release.yml` with `workflow_dispatch` and the input `ref` (a branch, tag or commit; default `main`). It checks out the given ref (default `main`), reads the version from `build.gradle.kts`, runs `./gradlew build` and `releaseZip`, and creates a draft GitHub release (a pre-release if the version has a suffix) with the zip and the section of that version from `CHANGELOG.md` as its text.
- Permission `contents: write` for this workflow only.

## Acceptance criteria
1. The workflow file is valid (checked by careful reading and a YAML parse; no runner is available for a dry run before a tag exists).
2. The tag name is made from the version (`v<version>`), so it cannot disagree with it. See Decisions.
3. The release is created as a **draft**, never published.
4. The text of the release is the changelog section of the version; a missing section fails the workflow.

## Approach
Shell steps in the workflow. The changelog extraction is a small script in `scripts/` that can be run and tested locally.

## Dependencies
T102, T104

## Decisions
- The workflow takes a `ref` (default `main`), not a tag, and creates the draft with `--target <commit>`. The tag `v<version>` is created by GitHub when the user publishes the draft, so no tag has to be pushed first and nothing is published by the workflow. Because of that "a tag that does not match the version" cannot happen.
- The notes are checked before the build (`scripts/changelog-section.sh`), so a missing changelog section fails in a minute and not after the build. `ChangelogSectionTest` runs the script, and also checks that the changelog has notes for the version of the build, so a version change without a changelog entry fails CI.
- A `workflow_dispatch` workflow can only be started once its file is on `main`; the first real run is after the story is merged. Before that it was only read and its pieces (the script, the version lookup) run locally.

## Progress
Done. `.github/workflows/release.yml`, `scripts/changelog-section.sh`, `ChangelogSectionTest` (5 tests). Build green locally.
