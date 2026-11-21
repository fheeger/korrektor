# S12 Beta release 0.1.0-beta.1

**Features:** – (distribution) · **Status:** in progress

As the author (and a few early testers), I want to download a Windows build of korrektor and run it without a development setup, so that I can correct real books and report what breaks.

S12 prepares the first public beta, `0.1.0-beta.1`: one version number, a Windows zip with everything in it, a release workflow, a changelog and the release documents. Not part of it: an installer, code signing, macOS and Linux builds, and migrations of the project format (the format may still change during 0.x, without a promise).

## Acceptance criteria
1. The app has one version, `0.1.0-beta.1`, defined in a single place (`build.gradle.kts`). A small About dialog (Help menu) shows the version, the license and a link to the repository.
2. A manually triggered GitHub Actions workflow builds `korrektor-0.1.0-beta.1-windows.zip` from a tag. The zip holds a folder with `korrektor.exe`, the bundled Java runtime and the PDFium DLL. The workflow creates a **draft** GitHub pre-release with the zip attached and the changelog section as its text.
3. The unzipped app runs on a Windows machine without Java or the project's tooling. It can create a project from the sample PDF, edit, save, reopen and export. This is checked on a clean Windows environment (Windows Sandbox is acceptable, and is reported as such).
4. PDFium is found next to the app in the packaged build, not via `build/pdfium`. If it is missing, the app says so with a clear message.
5. `CHANGELOG.md` exists in the style of Keep a Changelog, with an `Unreleased` section and a `0.1.0-beta.1` section. The first entry lists the MVP functions in a few lines for users.
6. `docs/releasing.md` describes the release steps and the version policy.
7. The README has an "Install" section (download, unzip, run, the SmartScreen warning, where projects and settings are stored, how to update) and says this is a beta.
8. `docs/project-format.md` says that the format may still change during 0.x. No promise of compatibility or migration is made.
9. `docs/third-party.md` matches what the zip contains (runtime, PDFium, font, libraries), and the license texts are in the zip.
10. All earlier stories are `done` and CI is green on the story branch.

## Tasks
- [ ] S12.1 Mark S11 `done` in the backlog → [T100](../tickets/T100-s11-done.md)
- [ ] S12.2 App version in one place, and the About dialog → [T101](../tickets/T101-version-and-about.md)
- [ ] S12.3 Packaged build: the app folder with the PDFium DLL, found at run time, and the zip → [T102](../tickets/T102-packaged-build.md)
- [ ] S12.4 Release workflow: manual trigger, tests, zip, draft pre-release → [T103](../tickets/T103-release-workflow.md)
- [ ] S12.5 `CHANGELOG.md` with its first entry, and the changelog rule in `concept/process.md` → [T104](../tickets/T104-changelog.md)
- [ ] S12.6 `docs/releasing.md`, README install section, note in the format specification, licenses check → [T105](../tickets/T105-release-docs.md)
- [ ] S12.7 Check the zip in a clean Windows environment with the sample, by hand → [T106](../tickets/T106-clean-machine-check.md)

## Decisions
- Version scheme: SemVer with a `-beta.N` suffix, tags `vX.Y.Z-beta.N`.
- Windows zip only, unsigned (SmartScreen will warn), no installer.
- The release stays a draft until the user publishes it.
- Release steps and the changelog are described in `docs/releasing.md`.

## Report
(Written at the end of the story.)
