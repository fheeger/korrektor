# T001 Development setup

**Type:** task · **Status:** to be refined · **Story:** none

Set up everything needed before the first story (S01) can start.

- Install a JDK on the user's system.
- Create a Gradle skeleton for the app (Kotlin, Compose Multiplatform for desktop, see D01).
- Add a CI workflow on GitHub Actions (build, tests, formatter and linter; see "Quality and tests" in `concept/process.md`).
- Turn on branch protection for `main`: no direct pushes, no force pushes, also for admins, and the CI check required.
