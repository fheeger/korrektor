# Changelog

All notable changes to korrektor are listed here, for users. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release has its own section; changes that are not released yet are under *Unreleased*. How a release is made is described in [docs/releasing.md](docs/releasing.md).

## Unreleased

## 0.1.0-beta.1

The first public beta. It covers the whole path from a PDF to clean text: import a PDF that already has OCR, correct it page by page next to the scan, and export the text. Windows only. The project format may still change before 1.0, and a later version may not be able to open projects of this one.

### Added
- **Projects:** create a project from a PDF with a title, author, year and source, and edit them later. A project is a folder; only one app can have it open at a time.
- **Import:** reads the page images and the OCR text with its positions from the PDF (tested with Google Books PDFs), in the background with progress and a Cancel button. Pages without text are noted.
- **Page viewer:** zoom (fit width, fit page, steps, Ctrl+wheel, the scan's own resolution), pan by dragging, go to a page, and an optional overlay of the word boxes. The last page and zoom of each project are remembered.
- **Editor:** the scan and the text of the page side by side, line by line, with a divider you can drag. The active line is highlighted on the scan, and a click on a line of the scan puts the cursor in it. Split, join, add and delete lines. The text panel uses a font with the characters of old German print (ſ, ꝛ, the small letters over a letter, ligatures).
- **Keyboard:** move between lines and pages, zoom, and all editing keys without the mouse. The menus show the shortcuts; F1 lists them all.
- **Autosave:** corrections are saved in the background while you type, when you change the page, and when you close. If saving fails you are told, and nothing is lost.
- **Undo and redo** across pages (Ctrl+Z, Ctrl+Y). The history is kept after closing the project.
- **Page status:** mark a page as corrected (Ctrl+Enter), see how far the book is, and jump to the next or previous unchecked page.
- **Find and replace** in the corrected text, on the current page or in the whole book, with options for case, whole words and regular expressions (Ctrl+F, Ctrl+H). Replace shows a preview of every change, lets you choose which ones to apply, and is one undo step.
- **Export** the corrected text as plain text or Markdown (Ctrl+E): the whole book or a range of pages, with page markers, a header from the project's data and the printed line breaks as options. Your choices are remembered for each format.
- **Help ▸ About korrektor** with the version.

### File format
- Version 1 of the project format, described in [docs/project-format.md](docs/project-format.md). Not yet promised to stay as it is.
