# T102 Packaged build: the app folder with PDFium, and the zip

**Type:** task · **Status:** done · **Story:** S12 · **Task:** S12.3

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
- `createDistributable` is used, not `packageMsi`: a zip of the app folder needs no WiX. The Windows package version is `0.1.0` (numbers only); the `-beta.1` is in the name of the zip.
- The default runtime modules of Compose are enough: the packaged app starts and opens the sample (checked below), so no `modules(...)` list.
- `preparePackagingResources` puts the library and the licenses (PDFium, Junicode) into a `common` resources folder, so the same task works on every platform later; the DLL for the current platform comes from `downloadPdfium`.
- The zip is about 69 MB.

## Progress
Done. `PdfiumLocator`, `PdfiumLocatorTest` (5 tests), Gradle tasks `preparePackagingResources` and `releaseZip`, architecture overview. Build green.
Checked by hand on this machine with PATH reduced to the Windows folders and no `JAVA_HOME`: the unzipped app starts and shows its window; a probe run with the packaged runtime and the packaged `pdfium.dll` (no `build/pdfium`) opens the sample PDF and reads 165 pages. The full flow (project, edit, export) in the packaged app is T106.
