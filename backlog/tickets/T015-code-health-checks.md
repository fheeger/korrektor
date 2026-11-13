# T015 Code health checks (Detekt, Kover)

**Type:** task · **Status:** in progress · **Story:** none

Add a static analysis check for complexity and code smells that blocks the PR, and a coverage report that Claude reads during code review.

## What needs doing
- Add Detekt (Gradle plugin) with default rules. It is part of `check`, so `./gradlew build` and CI run it.
- Fix all findings in the existing code. There is no baseline.
- Add the Detekt Compose rules plugin, or minimal rule overrides, so `@Composable` functions don't trigger false positives.
- Add Kover and a `koverXmlReport` task. Report only, no threshold.
- Update `concept/process.md` ("Code review", "CI", "Code style") as agreed in chat.

## Acceptance criteria
1. `./gradlew detekt` runs with default complexity rules and reports no findings on the current code.
2. There is no Detekt baseline file. Any violation fails `./gradlew build`.
3. A deliberately complex or smelly function, added once as a check, makes `./gradlew build` fail. It is removed afterwards.
4. Compose functions follow Compose naming and don't produce false positives.
5. `./gradlew koverXmlReport` writes a coverage report to `build/reports/`.
6. CI stays one job `build` and fails on Detekt findings. The reports are uploaded as a CI artifact.
7. `concept/process.md` describes the new checks and the review step.

## Approach
- Plugins in `build.gradle.kts`: `io.gitlab.arturbosch.detekt` and `org.jetbrains.kotlinx.kover`, latest stable versions, with the Compose rules as a `detektPlugins` dependency.
- `config/detekt/detekt.yml` holds only the overrides (Compose). Everything else uses the defaults. `buildUponDefaultConfig = true`.
- Existing findings are fixed in code. A suppression (`@Suppress`) is allowed only with a comment saying why.
- Detekt writes its reports (HTML, Markdown, XML) to `build/reports/detekt/`.
- CI gets an `actions/upload-artifact` step with `if: always()` for `build/reports`.
- Testing: criteria 1–6 are checked locally. Criterion 3 is checked once with a throwaway file, as in T001.

## Dependencies
None. T009 is in progress, so this ticket is worked on after it is merged.

## Decisions
- **Block on all violations, no baseline.** The code base is small, so it is cleaned up now.
- **Default complexity thresholds**, tuned only after the first real reports.
- **Coverage is report-only.** "No coverage target" in the process stays. Kover is there to show Claude uncovered new code during review.
- **Detekt 1.23.8**, the latest stable release. Detekt 2.0 is still alpha. It analyses Kotlin 2.4 code without problems.
- **Compose rules 0.4.28**, the last release built for Detekt 1.x. Newer ones need Detekt 2.0 and are silently ignored by 1.23.8 (found by a throwaway `@Composable` with a lowercase name that no rule reported).
- **`.editorconfig` with `max_line_length = 120`.** Detekt allows 120, ktlint 140, and `ktlintFormat` joined wrapped lines back to over 120. Both tools now use 120.
- **Findings were fixed in the code, not suppressed**, except two deliberate `catch (Throwable)` blocks (cleanup after any failure, then rethrow), each with a comment. Bigger structural fixes: `PageCodec` split into encoder, decoder and JSON helpers; the `project.json` text moved out of `Project` into `ProjectFile`; precondition checks extracted into helpers. Behaviour is unchanged, all tests pass as before.
- **Detekt overrides for Compose:** `FunctionNaming` ignores `@Composable` and `UnusedPrivateMember` ignores `@Preview`. The Compose rules add checks but don't switch off Detekt's own, so a correct `MainScreen` composable failed without them. Checked with a correct composable and preview (passes) and a lowercase one (still fails).
- **CI uses `--continue`,** so a Detekt finding doesn't hide failing tests, and the reports are written anyway.
- **Known risk:** the Detekt 1.23.8 and Kover plugins use APIs deprecated in Gradle 10 (see the build's deprecation warnings). Nothing breaks now; Detekt 1.x may block a later Gradle 10 upgrade, until Detekt 2.0 is stable.
- **No hosted service and no SARIF upload.** Claude reads the reports locally from plain files, so no dashboard is needed. This stays even though the repository is public.

## Progress
Done: Detekt (default rules, Compose rules, no baseline) and Kover added; all 53 findings fixed; CI runs `build koverXmlReport` and uploads `build/reports`; `process.md`, README and `docs/architecture.md` updated. Checked locally: `./gradlew clean build` green (62 tests, 1 skipped by design); a throwaway file with a complex function and a badly named composable made the build fail (criteria 3 and 4). Line coverage is about 97%. Code review (Opus) found no behaviour change in the refactors; its findings are fixed: Compose overrides, `--continue`, doc wording, and two tests for the refactored paths (empty history file, bad number in a word box). Next: PR.
