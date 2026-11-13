# T016 PDFium and its Kotlin binding

**Type:** task · **Status:** ready for development · **Story:** S03 · **Task:** S03.1

Bring PDFium into the build and give the app a small, safe Kotlin interface to it: open a PDF, read page sizes and the characters of a page's text layer with their boxes, and render a page. The rest of S03 builds on this.

## What needs doing
- **Build:** add JNA (`net.java.dev.jna:jna`, 5.19.1). Add a Gradle task `downloadPdfium` that fetches the pinned PDFium release (`bblanchon/pdfium-binaries`, tag `chromium/8152`) for the current platform (`pdfium-win-x64`, `pdfium-linux-x64`, `pdfium-mac-x64`, `pdfium-mac-arm64`), checks the SHA-256 that is pinned in the build file, extracts the shared library and the license files into `build/pdfium/`, and does nothing if they are already there. `test` and `run` depend on it and get `jna.library.path` pointing to that folder. CI needs no change (it has network access).
- **Package `korrektor.pdf`:**
  - `Pdfium`: loads the library, calls `FPDF_InitLibrary` once, and owns one lock. Every call into PDFium goes through it, because PDFium is not thread-safe (D02).
  - `PdfDocument.open(path)`: page count, `page(index)`, `close()`.
  - `PdfPage`: size, bounding box (the visible area: crop box within media box), rotation, `text()`, `render(width)`.
  - `PdfTextPage`: `charCount` and `char(i)`: the code point, the box in PDF coordinates (left, bottom, right, top), whether PDFium generated it (a space or line break it inserted itself), whether it is a hyphen, and the font size.
  - `PdfException` with a clear message for: file not found or unreadable, not a PDF or damaged, password required, unsupported security, page cannot be loaded.
  - `render(width)` returns a `BufferedImage`. It is a first version, which S04 refines for speed.
- **Docs:** `docs/decisions.md` D04 (how PDFium is bound), `docs/third-party.md` (licenses of PDFium and JNA), `docs/architecture.md`, README (Development: the first build downloads PDFium).
- **Test helper `TestPdfs`** (test sources): generates small PDFs with PDFBox: text at known positions, invisible text (render mode 3), several pages, a rotated page, a password-protected file.

## Acceptance criteria
1. A clean `./gradlew build` downloads PDFium once, verifies the checksum, and passes. A second build does not download again. A wrong checksum fails the build with a clear message.
2. A generated PDF opens; `PdfDocument` reports the page count; page size and rotation are right.
3. `PdfTextPage` returns the characters of known text in order, with boxes within 1 point of where the text was drawn. `ſ`, a combining `ͤ` after `u`, and a character outside the BMP (a surrogate pair in UTF-16) come back as the right code points.
4. Opening a missing file, a text file and a password-protected PDF each gives a `PdfException` that says what is wrong.
5. Closing is safe: `use` works, a second `close()` does nothing, and using a closed object fails with a clear exception instead of crashing the JVM.
6. Several threads using the binding at the same time do not crash it.
7. A page renders to an image of the requested width with the right proportions, and the image of a page with black text on white is not blank.
8. With the sample PDF present: 165 pages (skipped without it).
9. D04 is written and the licenses are listed.

## Approach
- JNA **direct mapping** (`Native.register`) for the functions called per character, so a million calls stay cheap; plain interface mapping would be several times slower. Strings from `FPDFText_GetText` are UTF-16LE.
- Native handles are wrapped in classes that implement `AutoCloseable`; a closed flag guards every call, and all calls run under the one lock.
- Tests: unit and integration tests with the generated PDFs; the sample PDF case is skipped when `samples/` is missing (as in CI).
- The pinned checksums come from the release's published digests (GitHub shows one per asset). Only Windows is exercised here; the other three are pinned for when those platforms arrive.

## Dependencies
None.

## Decisions
- **JNA, not the Java FFM API** (D04 will say so). FFM is still a preview feature in JDK 21 (final in 22), so it needs flags and may change. JNA runs on JDK 21 as it is and also exists for Android, which the tablet plan needs.
- **The PDFium binary is downloaded and verified at build time, not committed.** It is about 4 MB per platform. The version and checksum are pinned, so builds are reproducible.
- **Packaging the library into an installer** is not part of this ticket (distribution comes later).

## Progress
Not started.
