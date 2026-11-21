# T102 Packaged build: the app folder with PDFium, and the zip

**Type:** task · **Status:** ready for development · **Story:** S12 · **Task:** S12.3

## What needs doing
- `compose.desktop` gets the Windows settings (name, package version from T101, the JDK modules the app needs). `./gradlew createDistributable` gives a folder with `korrektor.exe` and the runtime.
- The PDFium DLL and its license files go into the app resources of the distributable and are found at run time next to the app. `PdfiumLib.load()` tries the app resources folder before `jna.library.path` / `build/pdfium`.
- If PDFium cannot be found, the error says where it looked.
- A Gradle task `releaseZip` zips the app folder as `korrektor-<version>-windows.zip`, with `LICENSE` and `docs/third-party.md` in it.

## Acceptance criteria
1. `./gradlew releaseZip` produces the zip with `korrektor.exe`, the runtime, `pdfium.dll`, the PDFium licenses, the font license, `LICENSE` and `third-party.md`.
2. The app started from the unzipped folder opens the sample PDF (no `build/pdfium`, no Java on the path).
3. A test of the library lookup: the app resources folder is preferred, and a missing library gives the message that names the places searched.

## Approach
Look first at what the Compose Gradle plugin offers (`appResourcesRootDir`, `modules`). Unit test for the lookup. The check of the real packaged app is starting the unzipped app with the sample here; T106 repeats it in a clean environment.

## Dependencies
T101

## Decisions
(none yet)

## Progress
Not started.
