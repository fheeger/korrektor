# T058 The saver: write the unsaved pages in the background

**Type:** task · **Status:** ready for development · **Story:** S07 · **Task:** S07.3

## What needs doing
- **`AutoSaver`** (package `korrektor.editor`; plain Kotlin, no UI) for one open project. Its core is `suspend fun saveNow(): SaveOutcome`:
  1. take the snapshot `editor.unsaved()`;
  2. write each page with `writePage(page, layer)` on the IO dispatcher, in page order, through `Project.writePage` (so with the crash-safe writing of S01.6: temp file, flush, atomic rename);
  3. after each page `editor.markSaved(page, layer)`;
  4. on the first failure stop, keep the rest unsaved and report the failure (T060 adds warning and retry).
- Saves never overlap (one `Mutex`): a second `saveNow` waits for the first, then writes whatever is unsaved by then.
- **Only changed pages are written.** The original OCR layer, `source.pdf`, `project.json` and the other pages are not touched (N4). Pages that were saved and not changed since are not written again.
- **Typing is never blocked:** the snapshot is immutable, the writing is on the IO dispatcher, and the editor is not locked while it runs. An edit during a save makes a new layer, so the page stays unsaved and is written by the next save.
- **State for the indicator and the warning:** `saving: Boolean` and `error: String?` as Compose state (read by the UI, changed by the saver on whichever thread; use `mutableStateOf`).
- The writer is a constructor parameter (`(Int, PageLayer) -> Unit`, default `project::writePage`) so that tests can make writing slow or failing.

## Acceptance criteria
1. After `saveNow` the page files of the changed pages contain the corrected layers (read back with `Project.readPage`) and `editor.changedPages` is empty.
2. Pages without changes, the original layers (`*.ocr.json`), `project.json` and `source.pdf` are byte for byte the same as before (compare the folder before and after).
3. A page edited during a save is written again by the next save and not lost; the earlier layer never overwrites the later one.
4. Two `saveNow` calls at once write in turn, not together (a slow fake writer records no overlap).
5. A failing write leaves that page and all after it unsaved, pages before it saved, `error` set, and the exception does not escape `saveNow`.
6. `saving` is true while the writer runs and false before and after.
7. Typing during a save is not delayed: the editor's `setLineText` does not wait for the writer (test with a writer that blocks until the test releases it).

## Approach
`AutoSaver.kt`; `UnsavedPages` from T057. Tests: `AutoSaverTest` with a real project (generated PDF) and fake writers; byte comparison like `EditorFlowTest`.

## Dependencies
T057.

## Decisions
(filled in while building)

## Progress
Not started.
