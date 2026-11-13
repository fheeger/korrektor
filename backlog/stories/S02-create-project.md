# S02 Create a project with metadata

**Features:** A1 · **Status:** done

As a reader of historical sources, I want to create a project for a source with its title, author, year and source, and open it again later
so that I can keep my sources apart and come back to my work.

## Acceptance criteria
- "New project" starts by choosing a PDF. There are no projects without a PDF.
- The project gets its own copy of the PDF (see S01). Reading the OCR text is part of S03.
- I can enter title, author, year and source (where the scan came from, e.g. a Google Books link). Only the title is required; it is suggested from the file name.
- Year and source are free text, so values like "ca. 1790" or "1785?" are allowed.
- I can edit the metadata later.
- The metadata is stored in the project format and documented in the format spec.
- I can open an existing project with an "Open…" file dialog.
- The window title shows the project title.
- If the PDF can't be read or the project can't be created at the chosen location, a clear error is shown and nothing half-created is left behind.

Out of scope: a list of recently opened projects (later).

## Tasks
- [x] S02.1 Add the metadata fields to the data model and the format spec → [T009](../tickets/T009-project-metadata.md)
- [x] S02.2 "New project" dialog: choose a PDF, enter metadata, choose where to store the project → [T012](../tickets/T012-new-project-dialog.md)
- [x] S02.3 Create the project and copy the PDF into it, cleaning up if anything fails → [T010](../tickets/T010-create-project-service.md)
- [x] S02.4 Dialog for editing the metadata → [T013](../tickets/T013-edit-metadata.md)
- [x] S02.5 Open an existing project with a file dialog → [T011](../tickets/T011-app-shell-open-project.md)
- [x] S02.6 Tests: create, reopen, metadata round trip, cleanup after failure → [T014](../tickets/T014-project-lifecycle-tests.md)

## Report

### What was built
Creating, opening and editing a project, as logic plus a first real screen:
- **Metadata in the format** (T009): `Metadata` (title, author, year, source, unknown fields), the optional field `metadata` in `project.json`, `Project.updateMetadata`, documented in [docs/project-format.md](../../docs/project-format.md). The format version stays 1.
- **Creating a project, logic** (T010): `NewProject` suggests a title from the PDF's file name, makes a safe folder name (`<title>.korrektur`), checks the PDF and the target, and creates the project with its own copy of the PDF and an empty page list.
- **App shell** (T011): start screen, "Open project…" with a folder dialog, error banner, window title `<title> – korrektor`. UI with Material 3.
- **"New project" dialog** (T012): choose a PDF, title suggested, author, year, source, location, the folder that will be created, progress while the PDF is copied in the background.
- **"Edit metadata" dialog** (T013) for the open project.
- **Lifecycle tests** (T014): the whole flow on real files, including the sample PDF.
- Along the way (outside S02's tickets, see T015): Detekt and Kover were added to the build, and all existing code was cleaned up for them.

### Decisions
Recorded in the tickets T009–T014. The main ones:
- Metadata is a new optional field, so the format version stays 1. Values are stored as typed, without trimming (V1); empty author, year and source are not written; `null` is not accepted (T009).
- A new project has an empty page list until the import (S03). The PDF is only checked for `%PDF-` near its start, because PDFium arrives with S03 (T010).
- The folder name comes from the title and is made safe for file systems; it is not renamed when the title is edited later (T010, T013).
- The location of a new project defaults to the folder of the PDF (T012).
- Material 3 1.9.0, declared directly: the newest stable release, and it works with Compose 1.12. State lives in plain classes (`AppModel`, one model per dialog), no view-model framework (T011).
- `Project.metadata` is plain data that the UI cannot observe, so `AppModel` keeps an observable copy; metadata must be changed through `AppModel.updateMetadata` (T013).
- Dialogs are in-window `Dialog`s, and the pickers (PDF, folder) are interfaces, so UI tests run without a window and with fake pickers (T011, T012).
- Creating runs on a background thread; the window cannot be closed meanwhile (review).
- Error messages give a reason ("access denied"), not only a path (review).

### How each criterion was checked
| Criterion | Check |
|---|---|
| "New project" starts by choosing a PDF; no project without a PDF | `NewProjectModelTest` (`canCreate`), `AppUiTest` (dialog starts with "Choose PDF…", "Create" disabled), `NewProjectTest` and `ProjectLifecycleTest` (not a PDF, missing, folder, unreadable are refused) |
| The project gets its own copy of the PDF | `NewProjectTest`, `ProjectLifecycleTest` (including the sample PDF: byte-identical copy, original untouched, read-only PDFs work) |
| Title, author, year, source; only the title required; title suggested from the file name | `MetadataTest` (blank title), `NewProjectTest` (`suggestTitle`), `NewProjectModelTest`, `AppUiTest` (the full dialog flow) |
| Year and source are free text (`ca. 1790`, `1785?`) | `MetadataTest`, `NewProjectModelTest`, `ProjectLifecycleTest` |
| I can edit the metadata later | `MetadataTest` (`updateMetadata`), `EditMetadataModelTest`, `AppUiTest`, `ProjectLifecycleTest` |
| Stored in the project format and documented | `MetadataTest`, `SpecExamplesTest` (every example in the spec round-trips), `docs/project-format.md` |
| I can open an existing project with an "Open…" file dialog | `AppModelTest`, `AppUiTest` (with a fake folder chooser). The real dialog was not tried (see below) |
| The window title shows the project title | `AppModelTest`, `EditMetadataModelTest`, `ProjectLifecycleTest`. I also started the real app: the window is titled `korrektor` on the start screen. A project title in the real window was not seen |
| A clear error and nothing half-created if the PDF can't be read or the project can't be created | `NewProjectTest`, `NewProjectModelTest`, `ProjectLifecycleTest` (the parent folder is compared before and after: not a PDF, existing project, missing parent, unreadable PDF, failed copy, failed write of `project.json`) |
| Projects from before S02 still open | `MetadataTest`, `ProjectLifecycleTest` (a `project.json` as S01 wrote it opens, saves with identical bytes, and can get metadata) |

The whole build (ktlint, Detekt, 159 tests) is green, and so is CI on every ticket PR. The sample PDF tests run locally and are skipped on CI, where `samples/` does not exist (checked on the CI report).

### Code review (Opus 5.5) and what was done
One real bug and several risks. Found and fixed:
- **A read-only PDF could not become a project on Windows** (archived PDFs, or the `source.pdf` of another project, which korrektor makes read-only): the copy kept the read-only flag and flushing it failed with an unexplained error. Reproduced with a test, then fixed in `AtomicFile.copy`; tests cover a read-only PDF and a project's own `source.pdf` as the source.
- **Closing the window while a project was created** could stop the program in the middle of the copy and leave a half-made folder. The close button now waits until creating is done.
- **Error messages that only showed a path** ("Cannot create the project at X: X"). Now a reason is given ("access denied", "it already exists", …) in all messages that report an I/O error.
- **An unexpected exception** while creating would have crashed the window instead of showing a message. Now it is shown; being cancelled is not an error.
- **`null` in author, year or source** was read as empty and dropped on the next save, changing the file. Now an error naming the field, as the ticket said.
- **Folder names:** leading dots and spaces are removed (a leading dot hides a folder), names are cut to 200 UTF-8 bytes (100 CJK characters would exceed Linux's limit), and `CONIN$`, `CONOUT$` and the superscript device names are reserved.
- Smaller: an unreadable subfolder while cleaning temporary files could crash the open click (now an error message; not covered by a test); the progress indicator was stretched; the windowTitle functions no longer default to the non-observable metadata (which let two tests skip the real code path); one constant for the known metadata keys; `show` renamed `openCreated`; `kotlinx-coroutines` is declared; stale "Next: PR…" lines in four tickets and the T012 criterion about a title "typed before choosing the PDF" (impossible in the dialog, now worded as what the model guarantees) were corrected.

Found and not fixed (listed under gaps): a crash or killed process during creation; Enter and screen-reader announcements in the dialogs; the folder dialog's look.

### Known gaps and uncertainties
- **A crash or a killed process while a project is created** can still leave a half-made folder (`<title>.korrektur` with a `source.pdf.tmp`). The next attempt then says "already exists", and opening it says "has no project.json". Delete the folder by hand. Creating under a temporary name and renaming at the end would remove this; it is a possible later ticket. Closing the window is already handled.
- **The PDF is only checked for the `%PDF-` marker.** A damaged PDF with a valid header is found at the import (S03).
- **Not tried by me, because I can't drive the real window:** the real PDF dialog and folder dialog, how the screens and dialogs look and fit, window title with an open project, the Escape and click-outside guard while creating (the busy state is tested, the guard itself is not), creating a project from the 165-page sample in the app (the logic is tested with it).
- **Keyboard and accessibility:** Enter does not submit the dialogs, and error texts in dialogs are not announced by screen readers. The Swing folder dialog uses the default Java look and starts in the home folder, not at the current location.
- **"Location not writable"** is only tested through the message (a simulated "access denied"); a really unwritable folder cannot be made portably in a test.
- **Two projects with the same title** in one location are refused ("already exists"); there is no automatic numbering. The user picks another title or location.
- **Material 3 1.9.0 with Compose 1.12.2:** works in the tests and when starting the app, but it is not the pairing JetBrains lists (those are alpha and beta). To revisit when Material 3 1.13 is stable.
- **Detekt 1.23.8 and Kover** use APIs that Gradle 10 deprecates (see T015); no problem today.
- **Observable metadata:** `Project.metadata` changes are only seen by the UI when they go through `AppModel.updateMetadata`. Later code that changes metadata elsewhere must do the same.
- `AtomicFile` still throws `IOException` while readers throw `FormatException`. `NewProject` and `EditMetadataModel` handle both; S07 (autosave) must too.

### How to try it
1. `./gradlew build` (about a minute): compiles, runs ktlint, Detekt and all tests. With `samples/Abhandlung_über_den_kleinen_Krieg.pdf` in place, `ProjectLifecycleTest` also creates a project from the real PDF.
2. `./gradlew run`, then:
   1. Click "New project…", then "Choose PDF…" and pick the sample PDF. The title should read `Abhandlung über den kleinen Krieg` and the project folder should be shown next to the PDF. Enter author `Johann von Ewald` and year `1785`, click "Create". The window title should become `Abhandlung über den kleinen Krieg – korrektor`, and the screen shows the metadata.
   2. Look at the folder next to the PDF: `…korrektur` with `project.json`, `source.pdf` (same size as the original, read-only) and `pages`.
   3. Click "Edit metadata…", change the year to `ca. 1785`, "Save". Try clearing the title: "Save" must be disabled.
   4. Click "Open another project…", choose the `.korrektur` folder: the same metadata should come back.
   5. Errors: choose a folder that is not a project (for example `samples`) and read the message, then "Dismiss". Create a second project with the same title and location: refused with a message and nothing new on disk. Pick a text file that is named `.pdf`: refused.
   6. While a large PDF is being copied, click the window's close button: nothing should happen until the copy is done.
3. Judge the look: start screen, the two dialogs, the project screen, and whether the button texts and the order of the fields feel right.
