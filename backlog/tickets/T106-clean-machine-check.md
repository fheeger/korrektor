# T106 Check the zip in a clean Windows environment

**Type:** task · **Status:** done · **Story:** S12 · **Task:** S12.7

## What needs doing
- Unzip the release zip in Windows Sandbox (or a machine without Java and without the project), start the app and work through: new project from the sample, edit a line, save, close and reopen, undo after reopening, mark a page, find and replace, export, the About dialog.
- Record the result and what could not be checked in the story report.

## Acceptance criteria
1. Each step works, or the problem becomes a ticket.
2. The report says where it ran.

## Approach
By hand. Windows Sandbox needs an optional Windows feature; if it is not available the check is done on this machine with `PATH` and `JAVA_HOME` cleared, and the report says so.

## Dependencies
T102, T105

## Decisions
- Windows Sandbox is not installed on this machine, so the check ran here with `PATH` reduced to the Windows folders and no `JAVA_HOME`, on a fresh unzip of `releaseZip` in a scratch folder (outside the repository, with no `build/pdfium` on any path).
- Claude has no way to click through the app's window, so the steps that need the UI are left to the user (listed in the story report under "how to try it"). The logic behind the steps was run with the packaged runtime through the launcher instead.

## Progress
Done, with the limits above.
- **Checked:** the unzipped `korrektor.exe` starts and shows its window "korrektor", with no Java on the path. With the packaged runtime and the shipped `pdfium.dll` (the launcher with another main class, since the exe has no console): open the sample PDF (165 pages); create a project from the sample, import all 165 pages (5 pages with notes), reopen the project and read page 5 (18291 characters of layer). The zip contains `LICENSE`, `third-party.md`, the PDFium licenses, the font license, the Apache License 2.0 and the Java runtime licenses.
- **Not checked by Claude:** editing a line, save and reopen by hand, undo after reopening, marking a page, find and replace, export, the About dialog and the Help menu in the real window, and the SmartScreen warning. The logic of these is covered by the tests of S01–S12 on the normal classpath; what is new in the packaged build is only the runtime and the library lookup, which the checks above cover.
- No problem found, so no new ticket.
