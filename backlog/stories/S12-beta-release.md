# S12 Beta release 0.1.0-beta.1

**Features:** – (distribution) · **Status:** done

As the author (and a few early testers), I want to download a Windows build of korrektor and run it without a development setup, so that I can correct real books and report what breaks.

S12 prepares the first public beta, `0.1.0-beta.1`: one version number, a Windows zip with everything in it, a release workflow, a changelog and the release documents. Not part of it: an installer, code signing, macOS and Linux builds, and migrations of the project format (the format may still change during 0.x, without a promise).

## Acceptance criteria
1. The app has one version, `0.1.0-beta.1`, defined in a single place (`build.gradle.kts`). A small About dialog (Help menu) shows the version, the license and a link to the repository.
2. A manually triggered GitHub Actions workflow builds `korrektor-0.1.0-beta.1-windows.zip` from a branch, tag or commit (the version is the one in `build.gradle.kts`; the tag `v<version>` is made when the draft is published). The zip holds a folder with `korrektor.exe`, the bundled Java runtime and the PDFium DLL. The workflow creates a **draft** GitHub pre-release with the zip attached and the changelog section as its text.
3. The unzipped app runs on a Windows machine without Java or the project's tooling. It can create a project from the sample PDF, edit, save, reopen and export. This is checked on a clean Windows environment (Windows Sandbox is acceptable, and is reported as such).
4. PDFium is found next to the app in the packaged build, not via `build/pdfium`. If it is missing, the app says so with a clear message.
5. `CHANGELOG.md` exists in the style of Keep a Changelog, with an `Unreleased` section and a `0.1.0-beta.1` section. The first entry lists the MVP functions in a few lines for users.
6. `docs/releasing.md` describes the release steps and the version policy.
7. The README has an "Install" section (download, unzip, run, the SmartScreen warning, where projects and settings are stored, how to update) and says this is a beta.
8. `docs/project-format.md` says that the format may still change during 0.x. No promise of compatibility or migration is made.
9. `docs/third-party.md` matches what the zip contains (runtime, PDFium, font, libraries), and the license texts are in the zip.
10. All earlier stories are `done` and CI is green on the story branch.

## Tasks
- [x] S12.1 Mark S11 `done` in the backlog → [T100](../tickets/T100-s11-done.md)
- [x] S12.2 App version in one place, and the About dialog → [T101](../tickets/T101-version-and-about.md)
- [x] S12.3 Packaged build: the app folder with the PDFium DLL, found at run time, and the zip → [T102](../tickets/T102-packaged-build.md)
- [x] S12.4 Release workflow: manual trigger, tests, zip, draft pre-release → [T103](../tickets/T103-release-workflow.md)
- [x] S12.5 `CHANGELOG.md` with its first entry, and the changelog rule in `concept/process.md` → [T104](../tickets/T104-changelog.md)
- [x] S12.6 `docs/releasing.md`, README install section, note in the format specification, licenses check → [T105](../tickets/T105-release-docs.md)
- [x] S12.7 Check the zip in a clean Windows environment with the sample, by hand → [T106](../tickets/T106-clean-machine-check.md)
- [x] S12.8 Fixes from the code review → [T107](../tickets/T107-s12-review-fixes.md)

## Decisions
- Version scheme: SemVer with a `-beta.N` suffix, tags `vX.Y.Z-beta.N`.
- Windows zip only, unsigned (SmartScreen will warn), no installer.
- The release stays a draft until the user publishes it.
- Release steps and the changelog are described in `docs/releasing.md`.

## Report

### What was built
- **Version and About** (T101): `version = "0.1.0-beta.1"` in `build.gradle.kts` only; `processResources` writes it into `app.properties`, `AppInfo.version` reads it. *Help > About korrektor* (works without a project) shows version, license and the repository as a link.
- **Packaged build** (T102): `./gradlew releaseZip` makes `build/release/korrektor-0.1.0-beta.1-windows.zip` (about 69 MB): `korrektor.exe`, the Java runtime, the libraries, `pdfium.dll` and the license texts in `app/resources`, `LICENSE` and `third-party.md`. `PdfiumLocator` looks for the library in the app's resources folder first, then in `jna.library.path`.
- **Release workflow** (T103): `.github/workflows/release.yml`, started by hand with a `ref`; it reads the version, takes the notes from the changelog (`scripts/changelog-section.sh`), runs all tests, builds the zip and creates a **draft** (pre-)release. It creates no tag and publishes nothing.
- **Changelog** (T104): `CHANGELOG.md` with `Unreleased` and the first entry `0.1.0-beta.1`; the changelog rule and table rows in `concept/process.md`.
- **Docs** (T105): `docs/releasing.md`, the README "Install" section and beta note, the 0.x note in `docs/project-format.md`, `docs/third-party.md` for what the zip holds, a copy of the Apache License 2.0.
- **Check of the zip** (T106) and **review fixes** (T107).

### Decisions
- Zip of the app folder (`createDistributable`), not an installer; Windows only; unsigned.
- The workflow takes a `ref` (default `main`) and the draft is created with `--target <built commit>`; GitHub makes the tag `v<version>` when the user publishes. So no tag has to be pushed and a version and its tag cannot disagree.
- The Windows package version is `0.1.0` (numbers only); `-beta.1` is in the zip's name and the About dialog.
- `ChangelogSectionTest` also checks that the changelog has notes for the version of the build, so raising the version without a changelog section fails CI.
- `createDistributable` does not see changes in the resources folder by itself; the folder is now declared as its input.
- `*.sh` files keep LF (`.gitattributes`), since bash on the Windows runner cannot run them with CRLF.
- No backwards-compatibility promise and no migration for the format in 0.x (agreed in chat); stated in `project-format.md`, `releasing.md` and the changelog.

### How each criterion was checked
1. **Version, About:** `AboutTest` (version equals the Gradle version, dialog content, close). The Help entry in the real window is not tested (see gaps).
2. **Workflow:** not run yet (a dispatch workflow can only be started once its file is on `main`). Its pieces were run: the notes script (`ChangelogSectionTest`, 5 tests), the version lookup (checked by the reviewer on a CRLF file), `releaseZip`. The Opus review read the workflow.
3. **Runs without Java:** partly. On this machine with a reduced PATH and no `JAVA_HOME`: the unzipped exe starts and shows its window; with the packaged runtime and the shipped DLL the sample PDF opens, a project is created and imported (165 pages, 5 pages with notes) and reopened. **Not done by Claude:** Windows Sandbox is not installed, and Claude cannot click through the window, so editing, save and reopen, undo, find and replace, export and the About dialog in the packaged app are for you (see below).
4. **PDFium next to the app:** `PdfiumLocatorTest` (order, missing, damaged-but-present messages); the packaged runtime found the DLL without `build/pdfium`.
5. **Changelog:** `ChangelogSectionTest` reads the real `CHANGELOG.md`.
6. **releasing.md:** written and read by the reviewer against the workflow.
7. **README install section:** written; the settings path was checked against `AppSettings.defaultFile`.
8. **Format note:** in `project-format.md`.
9. **Licenses:** the zip listing was compared with `third-party.md`; `releaseZip` now fails if the library or the license texts are missing from the zip.
10. **All stories done, CI green:** S11 is marked `done`; CI is green on every ticket PR.

### Code review (Opus 5.5, whole story)
Nine findings; the real ones are fixed in T107:
- the draft's tag target was the dispatching branch's head, not the built `ref` (fixed: `git rev-parse HEAD`);
- Skia and ICU (inside Skiko) shipped without their notices (fixed: texts added to the zip and to `third-party.md`);
- the PDFium message said "not found" for a library that was present but not loadable (fixed, tested);
- `ChangelogSectionTest` could fail instead of skip with the bash of WSL (fixed);
- the repository was plain text, not a link (fixed); small jars missing in `third-party.md` (fixed);
- no check of the zip's content (fixed in `releaseZip`);
- story task boxes unticked, criterion 2 and the T103 body out of date (fixed);
- T106 and criterion 3: the check is only partial; this is not hidden but listed under the gaps below.

### Known gaps and uncertainties
- **Not run yet:** the release workflow itself. Expect to fix small things on its first run (it only makes a draft, so nothing is published by mistake).
- **Not checked by Claude:** the packaged app's window beyond starting (criterion 3), the Help > About entry in the real window, the SmartScreen warning, and `Main.kt` (no tests, as before). The window wrapper of `AboutDialog` is the only other new code without a test.
- **Licenses:** the notices for Skia and ICU were taken from their projects because the Skiko jars have none; whether Skiko's own build bundles anything else cannot be told from the jars. Worth a look before a wider release.
- The zip is not signed; SmartScreen will warn, and antivirus tools may warn about an unknown exe too.
- Windows Sandbox was not available, so "a clean machine" is this machine with a reduced PATH.
- The exe starts a second process (jpackage behaviour); closing the window ends both.

### How to try it
1. Run `./gradlew releaseZip`, or later download the zip from the draft release. Unzip it on a machine (or at least a folder setup) without Java on the PATH and start `korrektor\korrektor.exe` (SmartScreen: More info, then Run anyway).
2. *Help > About korrektor* shows `0.1.0-beta.1`; the address opens in the browser.
3. Create a project from the sample PDF, correct a line, close the window, start the app again and open the project: the correction is there, and **Ctrl+Z** takes it back. Mark a page corrected (**Ctrl+Enter**), find and replace (**Ctrl+H**), export (**Ctrl+E**) and open the file.
4. To make the release: merge the story, then Actions > Release > Run workflow on `main`, check the draft, download its zip and repeat step 3, then publish the draft yourself (`docs/releasing.md`).
