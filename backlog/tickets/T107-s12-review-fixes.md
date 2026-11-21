# T107 Fixes from the S12 code review

**Type:** task · **Status:** done · **Story:** S12 · **Task:** none (review fixes)

## What needs doing
The Opus review of the whole story found these (details in the story report):
1. The draft release was aimed at `GITHUB_SHA`, which is the head of the branch the workflow was started from, not the `ref` that is built. The tag would then land on the wrong commit.
2. Skiko's native library (Skia) and data file (ICU) were shipped without their notices.
3. T106 claimed more than it checked (see the story report: criterion 3 is only partly checked by Claude).
4. The task boxes of the story were not ticked.
5. The PDFium error said "not found" also when the file was there but could not be loaded.
6. Criterion 2 of the story and the body of T103 still said "from a tag" / "input `tag`".
7. `ChangelogSectionTest` could fail on a machine where Git is installed per user and `bash` on the PATH is the one of WSL.
8. The About dialog showed the repository as plain text, not as a link; some small jars were not named in `third-party.md`.
9. Nothing checked that the zip has the library and the license texts.

## Acceptance criteria
1. The release is targeted at `git rev-parse HEAD` of the checked-out ref.
2. The zip has the Skia and ICU texts in `app/resources/licenses/skiko/`, and `third-party.md` names them and the small jars.
3. The story report says what was and was not checked for criterion 3.
4. The five completed tasks' boxes are ticked, the criterion 2 text and the T103 body are corrected.
5. A library that exists but cannot be loaded gets its own message (`PdfiumLocatorTest`).
6. The test of the changelog script finds a per-user Git, and is skipped (not failed) if the bash found cannot run a command.
7. The repository address in the About dialog opens in the browser on a click.
8. `releaseZip` fails if the zip lacks the exe, `pdfium.dll`, `LICENSE`, `third-party.md` or the license texts.

## Approach
As listed. The link is a clickable text with `LocalUriHandler`; the test only checks that it is shown (opening a browser is not tested).

## Dependencies
T101–T106.

## Decisions
- The notice of Skia is the BSD 3-clause text of the Skia project; the ICU text is the Unicode License v3 text from PDFium's package, which has the same license. The other libraries inside Skia (FreeType, HarfBuzz, libpng, zlib, libjpeg-turbo) are named in `third-party.md` and their texts are the ones already shipped with PDFium. Whether Skiko's own build contains anything else cannot be told from the jars; this is listed as an uncertainty in the report.
- A full check of criterion 3 by Claude is not possible (no Windows Sandbox, no way to click through the window); it is left to the user and listed under "how to try it".

## Progress
Done. Build, tests, Detekt and ktlint green locally.
