# T001 Development setup

**Type:** task · **Status:** done · **Story:** none

Set up everything needed before the first story (S01) can start.

## What needs doing
- Install a JDK on the user's system.
- Create a Gradle skeleton for the app (Kotlin, Compose Multiplatform for desktop, see D01).
- Add a CI workflow on GitHub Actions (build, tests, formatter and linter; see "Quality and tests" in `concept/process.md`).
- Turn on branch protection for `main`: no direct pushes, no force pushes, also for admins, and the CI check required.

## Acceptance criteria
1. `java -version` reports JDK 21 on the user's system.
2. `./gradlew build` succeeds from a clean checkout: it compiles, runs the tests and runs the formatter/linter check.
3. `./gradlew run` opens a desktop window (Compose Multiplatform).
4. The build fails if a Kotlin file violates the formatting or lint rules.
5. The CI workflow runs on every PR and on pushes to `main` (on Windows) and runs `./gradlew build`.
6. `main` is protected: PRs only, no force pushes, rules apply to admins, the CI check `build` is required.

## Approach
- Root Gradle project `korrektor`, a single module for now. Kotlin JVM plugin, Compose Multiplatform plugin and Compose compiler plugin. Gradle wrapper committed, so no Gradle install is needed.
- Source in `src/main/kotlin/korrektor`, tests in `src/test/kotlin/korrektor`.
- A minimal `main` opens a window with the app name. The only test checks a constant; it proves the test setup runs in CI. Real tests come with S01.
- Formatter and linter: ktlint through the `org.jlleitschuh.gradle.ktlint` plugin, standard settings. `build` includes `ktlintCheck`.
- CI: one job `build` on `windows-latest` with Temurin 21 and Gradle caching.
- Testing: acceptance criteria 1–5 checked locally and on the PR's CI run; criterion 6 checked with `gh api` after setting it.
- Branch protection is set with `gh api` after the first CI run, so the check name exists.

## Dependencies
None.

## Decisions
- **JDK 21 (Temurin, LTS)** installed with winget. Compose Desktop needs 17 or newer; 21 is the current LTS line the toolchain supports.
- **ktlint as both formatter and linter.** The process asks for one of each with standard settings; ktlint does both for Kotlin and needs no config. Detekt can be added later if needed.
- **`BasicText` instead of Material 3** in the skeleton. Material3 is not needed yet and the Gradle accessor `compose.material3` is deprecated. The UI toolkit choice is made in S04/S05.
- **Versions:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, Gradle 9.9.1, ktlint plugin 14.2.0 (latest stable on 2026-11-11).
- **No `docs/architecture.md` yet.** There is no structure worth describing; it is written when the first story adds modules.

## Progress
- Done: JDK installed, Gradle skeleton, `./gradlew build` green locally, app starts, CI workflow written. Criterion 4 checked by adding a badly formatted file once: `ktlintCheck` failed. Code review fixes applied: workflow permissions, current action versions, `.gitattributes`, executable `gradlew`, "Using korrektor" README section.
- Finished: PR merged, branch protection on `main` set (PR required, check `build` required, no force pushes, applies to admins).
