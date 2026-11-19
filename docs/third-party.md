# Third-party software

korrektor is released under the GPL-3.0. It uses the following components, all with licenses that are compatible with it. This list names the ones that need attention; the full dependency list is in `build.gradle.kts`.

## PDFium (native library, loaded at run time)

- **What:** the PDF engine of Chromium, used to read pages and their text layer and to render pages (D02).
- **Source of the binary:** [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries), release `chromium/8152`, downloaded by the build and checked against a pinned SHA-256 (D04). It is not part of this repository.
- **License:** PDFium is BSD 3-clause (some parts Apache 2.0). The binaries package is MIT. The package also contains third-party libraries (FreeType, HarfBuzz, ICU, lcms, OpenJPEG, libjpeg-turbo, libpng, zlib, abseil and others) under their own licenses: BSD, MIT, zlib, Apache 2.0, the FreeType License. The build puts all license texts next to the library in `build/pdfium/` (`LICENSE` and `licenses/`). A distributed app must ship these files with the library.
- **How it is used:** korrektor loads the library dynamically; it is not linked into or modified by korrektor.

## JNA (Java Native Access)

- **What:** the library that lets Kotlin call PDFium (D04).
- **License:** dual-licensed LGPL 2.1 or later, and Apache 2.0. korrektor uses it under the Apache 2.0 terms.

## Junicode (font, bundled)

- **What:** the font of the text panel (S05), made for historical texts: it has the long s `ſ`, `ꝛ`, the combining small letters such as the `e` over `uͤ`, the ligatures and the other characters of old German print. The default fonts of Windows, macOS and Linux differ in what they cover.
- **Version and source:** Junicode 2.226 (`Junicode-Regular.ttf`, 1.0 MB, SHA-256 `1cf12039709603afe6aa2ae9d0910c11ed9b2b1bd975b38f04290a42d1821552`), from the release `v2.226` of [psb1558/Junicode-font](https://github.com/psb1558/Junicode-font). It is in this repository under `src/main/resources/fonts/`, so the build needs no download.
- **License:** SIL Open Font License 1.1 (copyright 2025 Peter S. Baker), which allows bundling and redistributing the font with software under another license, as long as the font is not sold on its own and keeps its name and license. The license text is next to the font (`Junicode-OFL.txt`). A distributed app must ship it.
- **Coverage:** every Latin letter, digit, punctuation mark and combining mark in the text layers of the two sample books has a glyph (`HistoricFontsTest`). What it lacks is OCR noise from other scripts (Cyrillic, Hebrew, Arabic, Devanagari, …) and a few symbols; the text engine draws these from a system font.

## For development only

- **Apache PDFBox** (Apache 2.0): used by the tests to generate small PDFs and as an independent check of PDF contents. It is not part of the app.
- **commonmark-java** (BSD 2-clause): used by the tests to read the exported Markdown back and check that it comes out as the exported text. It is not part of the app.
