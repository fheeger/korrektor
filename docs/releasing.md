# Releasing

How korrektor is released. The first release is `0.1.0-beta.1` (S12). Only Windows is built for now. The build is a zip of the app folder, unsigned, with no installer.

## Version policy
- The version is set in one place: `version = "…"` in `build.gradle.kts`. The app (About dialog) and the name of the zip take it from there.
- The scheme is SemVer: `0.1.0-beta.1`, `0.1.0-beta.2`, …, `0.1.0`, `0.2.0`. A suffix (`-beta.N`) marks a pre-release. The Git tag is `v<version>`.
- During `0.x` anything may change, including the project format. There is no promise that a later version can open projects of an earlier one, and no migration yet. A change that breaks old projects is listed in the changelog under *File format*, so you can tell which release does it. When the format changes, `version` in `project.json` is raised as described in [project-format.md](project-format.md).
- Windows only allows numbers in a program's version, so the packaged app says `0.1.0`; the suffix is only in the name of the zip and in the About dialog.

## What a release contains
`korrektor-<version>-windows.zip` holds one folder, `korrektor`:
- `korrektor.exe`, the Java runtime it needs (`runtime/`) and the libraries (`app/`). Nothing has to be installed.
- `app/resources/pdfium.dll`, the PDF library, and the license texts of what is shipped: `app/resources/licenses/` (PDFium and its parts, the Junicode font, Skia and ICU, the Apache License 2.0), `runtime/legal/` (the Java runtime), `LICENSE` (GPL-3.0) and `third-party.md`.

The build is not signed, so Windows SmartScreen warns when the exe is started for the first time ("More info", then "Run anyway").

## Making a release
1. **Prepare** on a branch (a ticket, or the last ticket of a story): set the new `version` in `build.gradle.kts`, and in `CHANGELOG.md` turn the *Unreleased* section into a section `## <version>` (leave an empty *Unreleased* above it). The build fails if the changelog has no notes for the version (`ChangelogSectionTest`). Merge it into `main`.
2. **Build:** *Actions ▸ Release ▸ Run workflow* on `main`. The workflow reads the version, takes the notes from the changelog, builds, runs all the tests and creates a **draft** release `v<version>` with the zip attached and the notes as its text (a pre-release if the version has a suffix). It creates no tag and publishes nothing.
3. **Check:** download the zip from the draft, unpack it somewhere without Java and try it with the sample PDF: create a project, correct a line, close and reopen, export (the list in [T106](../backlog/tickets/T106-clean-machine-check.md) is a good one).
4. **Publish:** only the maintainer does this. Publishing the draft creates the tag `v<version>` on the commit that was built (the workflow's `ref`, not necessarily the head of `main`).

The zip can also be built on its own with `./gradlew releaseZip` (the result is in `build/release/`).

## The changelog
- `CHANGELOG.md` lists changes for users, in the style of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/): *Added*, *Changed*, *Fixed* (and *File format* for anything that touches the project format).
- Every story PR and every loose ticket PR adds its entries under *Unreleased* when users notice the change. Changes that came out of a story's own review are covered by the story's entry.
- A release turns *Unreleased* into the section of the version, and the text of the GitHub release is that section.
