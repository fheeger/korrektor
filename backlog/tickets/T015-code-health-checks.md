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
- **No hosted service and no SARIF upload.** Claude reads the reports locally from plain files, so no dashboard is needed. This stays even though the repository is public.

## Progress
