# Decisions

Big decisions with their reasons. Small decisions are recorded in the tickets.

## D01 UI technology: Kotlin with Compose Multiplatform
**Date:** 2026-11-11

**Decision:** The app is written in Kotlin. The interface uses Compose Multiplatform (desktop on the JVM). Build system: Gradle.

**Reasons:**
- One language for everything, readable during review even without prior experience.
- Desktop comes first, and Compose for Desktop is mature there.
- A spike confirmed the core feature: the scan and the OCR text side by side, line sync in both directions, special characters (ſ, uͤ, ꝛ) displayed and edited correctly, and pages loaded in the background.
- A path to tablets stays open: Android natively, iPad through Compose Multiplatform. A separate tablet codebase would also be acceptable.
- Good libraries for what comes later: PDF (PDFBox, PDFium bindings) and OCR (ONNX Runtime Java API, Tesseract through Tess4J).

**Alternatives considered:**
- Tauri 2 (Rust + TypeScript): the best ready-made editor (CodeMirror) and PDF viewer (pdf.js), but two languages, and Rust is hard to review.
- Flutter: the strongest tablet support, but a weaker text editor and more laborious OCR integration.
- Qt/QML (C++): mature, but C++ is error-prone, and tablets are laborious.
- Electron, Python/Qt: ruled out, because they don't support tablets.

**Consequences:**
- The app bundles a Java runtime (installer about 80–150 MB).
- The editor features (highlights, line sync) are built by us. There's no ready-made editor like CodeMirror.

## D02 PDF library: PDFium
**Date:** 2026-11-11

**Decision:** PDFs are rendered and their text layer read with PDFium (prebuilt binaries from `bblanchon/pdfium-binaries`), accessed from Kotlin through a small binding of our own.

**Reasons:**
- A spike with the sample PDF: about 9× faster than PDFBox (median 26 ms vs. 227 ms per page), well within S04's target of 0.3 s per page switch.
- JBIG2 and JPEG2000, which Google Books uses, are built in. PDFBox needs a JPEG2000 plugin whose license is questionable for GPL-3.0.
- All licenses involved are compatible with GPL-3.0.
- Prebuilt binaries exist for every target platform, including Android and iOS.

**Alternatives considered:**
- Apache PDFBox: pure Java and simpler to integrate, but much slower and JPEG2000 only with a questionable license.
- MuPDF: fast, and it delivers lines and blocks itself. But there are no prebuilt Java bindings for desktop, so they would have to be built from source for every platform. Its AGPL license would not be a real obstacle for this project.

**Consequences:**
- PDFium delivers characters with boxes, but no reliable lines. We detect lines ourselves from the geometry (part of S03).
- PDFium isn't thread-safe: all calls go through one lock, in the background.
- A native library per platform has to be bundled. Whether the binding uses JNA or Java's built-in FFM API is decided in S03.

## D03 Project format: a JSON project folder
**Date:** 2026-11-11

**Decision:** A project is a folder named `<title>.korrektur`, with one JSON file per page and layer:

```
<title>.korrektur/
├── project.json          format version, metadata (S02), page list with status (S09)
├── source.pdf            copy of the PDF, never modified (N4, V5)
├── pages/
│   ├── 0001.ocr.json     original OCR of page 1, written once at import, then never again
│   ├── 0001.json         corrected layer of page 1
│   └── …
├── history/              undo history, append-only (S08)
│   └── 0001.jsonl        one change per line (JSON Lines)
└── .lock                 while the project is open (S07), not part of the content
```

Rules for the files:
- JSON, written canonically: fixed key order, fixed number format, UTF-8, LF line endings. Opening and saving without changes leaves every file byte-for-byte identical.
- Formatted for diffs: one text line of the book is one line in the file, so correcting a word changes one line in the diff.
- No Unicode normalization when saving: text is stored exactly as typed (V1).
- Every file is written crash-safely on its own: write to a temporary file, then rename.

Principles for the data model (details in S01.2):
- Every region and line has a stable ID, unique within its page, that survives editing, splitting and joining.
- Corrected lines refer to the original line they came from. A new line has no reference and no box.
- Coordinates are in PDF points, origin top-left, with one decimal place.
- New fields can be added at any time, and readers ignore fields they don't know. Only incompatible changes raise the format version.

**Reasons:**
- One file per page keeps diffs small (V3), means loading a page doesn't read the whole book (N1), and lets each file be written crash-safely on its own.
- A folder of text files works directly with Git (F3).
- JSON is universal and well supported in Kotlin (`kotlinx.serialization`). JSON Lines lets the history grow by appending only.

**Alternatives considered:**
- PAGE XML or ALTO: the OCR standards, close to our data model, but verbose XML with larger diffs, and no concept of an original and a corrected layer side by side.
- SQLite: robust and fast, but not text-based (V3).
- YAML: readable, but ambiguous and slow to parse.
- A single ZIP file: easy to share, but no small diffs and no crash-safe writing per page.

**Consequences:**
- A folder is awkward to share. Sharing as a single file comes with A5.
- On Windows, a project is a folder and doesn't open with a double-click. The app opens it through "Open…" (S02).
