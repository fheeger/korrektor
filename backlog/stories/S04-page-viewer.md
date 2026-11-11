# S04 Page viewer

**Features:** C2, N1 · **Status:** ready for development

As a reader of historical sources, I want to view the scanned pages with zoom and pan, loading quickly even in a book with 1000+ pages
so that I can read the original comfortably while correcting.

## Acceptance criteria
- Pages are drawn from the copy of the PDF inside the project.
- The viewer shows one page at a time (no continuous scrolling).
- Zoom: fit width, fit page, fixed zoom steps, and Ctrl+mouse wheel. Zooming in shows the scan's full detail up to its native resolution. The page isn't drawn once at low resolution and then enlarged.
- Pan: when zoomed in, the page can be moved by dragging and with the scrollbars.
- Navigation: previous/next page, go to a page number, and a "page n of N" display.
- Pages load only when needed, and a cache of limited size keeps memory use under control.
- Switching pages takes less than 0.5 s in a 1000-page book (target: 0.3 s).
- The last page viewed and the zoom level are remembered for each project, in the app settings (not in the project format).
- Optional: an overlay showing the boxes around the OCR'd words can be turned on and off.

## Tasks
- [ ] S04.1 Draw a single page from the project's PDF copy
- [ ] S04.2 Zoom: fit width, fit page, zoom steps, Ctrl+wheel, full detail up to native resolution
- [ ] S04.3 Pan when zoomed in
- [ ] S04.4 Navigation: previous/next, go to page, "page n of N"
- [ ] S04.5 Load pages on demand, with a cache of limited size
- [ ] S04.6 Performance test on a 1000-page PDF (<0.5 s, target 0.3 s)
- [ ] S04.7 Remember last page and zoom per project in the app settings
- [ ] S04.8 Optional: overlay showing word boxes
